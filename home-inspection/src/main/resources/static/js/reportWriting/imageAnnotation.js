// Arrow stuff
const ARROW_SHAFT_WIDTH_PER_STEP = 5;
const ARROW_HEAD_LENGTH_RATIO = 2.6;
const ARROW_HEAD_WIDTH_RATIO = 1.6;
const ARROW_MAX_HEAD_SHARE = 0.6;
const ARROW_FIXED_HEAD_MULTIPLE = 2;

const CLIP_STEP_RADIANS = Math.PI / 4;

// Size steps: shapes start thicker than text, which reads better at the same step count.
const DEFAULT_SHAPE_SIZE = 3;
const DEFAULT_TEXT_SIZE = 2;

// Kept in step with CANVAS_TEXT_PX_PER_STEP in InspectionImagesService, so the report matches the canvas.
const TEXT_PX_PER_STEP = 10;

// Edit mode
const HANDLE_SIZE = 8;
const HANDLE_GRAB_RADIUS = 7;
const MIN_SHAPE_SIZE = 6;
const SELECTION_COLOR = "#2c6ca3";

// Remembered across image switches, so a chosen size sticks for the session.
const rememberedSizes = { shape: DEFAULT_SHAPE_SIZE, text: DEFAULT_TEXT_SIZE };

function sizeBucket(tool){
    return tool === "text" ? "text" : "shape";
}

function sizeSteps(ann){
    const size = Number(ann.strokeWidth);
    return size > 0 ? size : 1;
}

function fixedArrowLength(size){
    return size * ARROW_SHAFT_WIDTH_PER_STEP * ARROW_HEAD_LENGTH_RATIO * ARROW_FIXED_HEAD_MULTIPLE;
}

function arrowGeometry(ann){
    const size = sizeSteps(ann);
    const shaftWidth = size * ARROW_SHAFT_WIDTH_PER_STEP;
    const angle = Math.atan2(ann.height, ann.width);
    const headHalfWidth = shaftWidth * ARROW_HEAD_WIDTH_RATIO;

    if (ann.fixedLength){
        if (Math.hypot(ann.width, ann.height) === 0){
            return { length: 0, angle, shaftLength: 0, shaftWidth, headHalfWidth };
        }
        const headLength = shaftWidth * ARROW_HEAD_LENGTH_RATIO;
        return {
            length: headLength * ARROW_FIXED_HEAD_MULTIPLE,
            angle,
            shaftLength: headLength * (ARROW_FIXED_HEAD_MULTIPLE - 1),
            shaftWidth,
            headHalfWidth
        };
    }

    const length = Math.hypot(ann.width, ann.height);
    const headLength = Math.min(shaftWidth * ARROW_HEAD_LENGTH_RATIO, length * ARROW_MAX_HEAD_SHARE);

    return { length, angle, shaftLength: length - headLength, shaftWidth, headHalfWidth };
}

function arrowTip(ann){
    const arrow = arrowGeometry(ann);
    return {
        x: ann.x + Math.cos(arrow.angle) * arrow.length,
        y: ann.y + Math.sin(arrow.angle) * arrow.length,
        arrow
    };
}

function clipToAngleSteps(dx, dy){
    const length = Math.hypot(dx, dy);
    const angle = Math.round(Math.atan2(dy, dx) / CLIP_STEP_RADIANS) * CLIP_STEP_RADIANS;
    return { width: Math.cos(angle) * length, height: Math.sin(angle) * length };
}

export function addAnnotationCanvas(existingImageDiv, existingImageImage, imageId, sharedState){
    // Create canvas
    const wrapper = existingImageDiv.querySelector(".image-canvas-wrapper");
    const canvas = document.createElement("canvas");
    canvas.width = existingImageImage.offsetWidth;
    canvas.height = existingImageImage.offsetHeight;
    wrapper.appendChild(canvas);

    const ctx = canvas.getContext("2d");

    const toolsDiv = existingImageDiv.querySelector(".annotation-tools");

    const saveButton = toolsDiv.querySelector("#save-annotations");
    const colourPicker = toolsDiv.querySelector("#color-picker");
    const strokeSlider = toolsDiv.querySelector("#stroke-size");
    const strokeSizeValue = toolsDiv.querySelector("#stroke-size-value");
    const textInput = toolsDiv.querySelector("#text-tool");
    const clipAngleBox = toolsDiv.querySelector("#clip-arrow-angle");
    const fixLengthBox = toolsDiv.querySelector("#fix-arrow-length");
    const editHint = toolsDiv.querySelector("#edit-hint");

    const successMessage = toolsDiv.querySelector("#success-message");

    let startX, startY, isDrawing = false;
    let strokeSize = DEFAULT_SHAPE_SIZE;
    let annotations = [];

    // Edit mode state
    let selected = null;
    let drag = null; 
    const edited = new Set(); // saved annotations changed since the last save

    function setStrokeSize(value){
        strokeSize = value;
        strokeSlider.value = String(value);
        strokeSizeValue.textContent = value;
    }

    setStrokeSize(rememberedSizes[sizeBucket(sharedState.currentTool)]);

    // loadFields calls this whenever the active tool or mode changes.
    sharedState.onToolChange = () => {
        if (!sharedState.editMode) clearSelection();
        if (sharedState.currentTool) setStrokeSize(rememberedSizes[sizeBucket(sharedState.currentTool)]);
        updateEditHint();
    };

    strokeSlider.addEventListener("input", () => {
        strokeSize = parseInt(strokeSlider.value, 10);
        strokeSizeValue.textContent = strokeSize;
        if (sharedState.currentTool) rememberedSizes[sizeBucket(sharedState.currentTool)] = strokeSize;

        // In edit mode the slider is how a selected annotation is resized.
        if (selected){
            selected.strokeWidth = strokeSize;
            markEdited(selected);
            redrawAnnotations();
        }
    });

    colourPicker.addEventListener("input", () => {
        if (selected){
            selected.color = colourPicker.value;
            markEdited(selected);
            redrawAnnotations();
        }
    });

    textInput.addEventListener("input", () => {
        if (selected && selected.type === "text"){
            selected.content = textInput.value;
            markEdited(selected);
            redrawAnnotations();
        }
    });

    // Load existing annotations
    fetch(`http://localhost:8080/api/fields/images/${imageId}/annotations`)
        .then(response => {
            if (response.status === 404 || response.status === 204) {
                annotations = [];
                return null;
            } else {
                return response.json();
            }
        })
        .then(data => {
            if (data) {
                annotations = data.map(toCanvasSpace);
            }
            redrawAnnotations();
        })
        .catch(error => {
            console.log("No existing annotations found");
            annotations = [];
        });

    // Stored coordinates belong to the display size they were drawn at, so bring them into this canvas.
    function toCanvasSpace(ann){
        const scaleX = ann.imageDisplayWidth > 0 ? canvas.width / ann.imageDisplayWidth : 1;
        const scaleY = ann.imageDisplayHeight > 0 ? canvas.height / ann.imageDisplayHeight : 1;
        if (scaleX === 1 && scaleY === 1) return ann;

        ann.x *= scaleX;
        ann.width *= scaleX;
        ann.y *= scaleY;
        ann.height *= scaleY;
        ann.imageDisplayWidth = canvas.width;
        ann.imageDisplayHeight = canvas.height;
        return ann;
    }

    function markEdited(ann){
        if (ann.id) edited.add(ann);
    }

    function redrawAnnotations() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        annotations.forEach(ann => {
            ctx.strokeStyle = ann.color || "#ff0000";
            ctx.fillStyle = ann.color || "#ff0000";
            ctx.lineWidth = sizeSteps(ann);

            if (ann.type === "rectangle") {
                ctx.strokeRect(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "ellipse" || ann.type === "circle") {
                drawEllipse(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "arrow") {
                drawArrow(ctx, ann);
            } else if (ann.type === "text") {
                ctx.font = textFont(ann);
                ctx.fillStyle = ann.color || "#000000";
                ctx.fillText(ann.content, ann.x, ann.y);
            }
        });
        drawSelection();
    }

    function textFont(ann){
        return `${sizeSteps(ann) * TEXT_PX_PER_STEP}px Arial`;
    }


    // The outer box of an annotation, always with positive width/height.
    function boundsOf(ann){
        if (ann.type === "ellipse" || ann.type === "circle"){
            return {
                x: ann.x - Math.abs(ann.width),
                y: ann.y - Math.abs(ann.height),
                width: Math.abs(ann.width) * 2,
                height: Math.abs(ann.height) * 2
            };
        }

        if (ann.type === "arrow"){
            const tip = arrowTip(ann);
            const pad = Math.max(tip.arrow.headHalfWidth, tip.arrow.shaftWidth / 2);
            return {
                x: Math.min(ann.x, tip.x) - pad,
                y: Math.min(ann.y, tip.y) - pad,
                width: Math.abs(tip.x - ann.x) + pad * 2,
                height: Math.abs(tip.y - ann.y) + pad * 2
            };
        }

        if (ann.type === "text"){
            const size = sizeSteps(ann) * TEXT_PX_PER_STEP;
            ctx.font = textFont(ann);
            const width = ctx.measureText(ann.content || "").width;
            return { x: ann.x, y: ann.y - size * 0.8, width: Math.max(width, MIN_SHAPE_SIZE), height: size };
        }

        // rectangle, including ones stored from a right-to-left drag
        return {
            x: ann.width < 0 ? ann.x + ann.width : ann.x,
            y: ann.height < 0 ? ann.y + ann.height : ann.y,
            width: Math.abs(ann.width),
            height: Math.abs(ann.height)
        };
    }

    // Writes a new outer box back onto the annotation in whatever form that type stores.
    function setBounds(ann, box){
        if (ann.type === "ellipse" || ann.type === "circle"){
            ann.x = box.x + box.width / 2;
            ann.y = box.y + box.height / 2;
            ann.width = box.width / 2;
            ann.height = box.height / 2;
            return;
        }

        ann.x = box.x;
        ann.y = box.y;
        ann.width = box.width;
        ann.height = box.height;
    }

    // Arrows get their two ends; text is resized with the Size slider, everything else gets corners.
    function handlesFor(ann){
        if (ann.type === "arrow"){
            const tip = arrowTip(ann);
            return [
                { name: "tail", x: ann.x, y: ann.y, cursor: "move" },
                { name: "tip", x: tip.x, y: tip.y, cursor: "crosshair" }
            ];
        }

        if (ann.type === "text") return [];

        const box = boundsOf(ann);
        return [
            { name: "nw", x: box.x, y: box.y, cursor: "nwse-resize" },
            { name: "ne", x: box.x + box.width, y: box.y, cursor: "nesw-resize" },
            { name: "sw", x: box.x, y: box.y + box.height, cursor: "nesw-resize" },
            { name: "se", x: box.x + box.width, y: box.y + box.height, cursor: "nwse-resize" }
        ];
    }

    function handleAt(ann, x, y){
        return handlesFor(ann).find(handle =>
            Math.abs(x - handle.x) <= HANDLE_GRAB_RADIUS && Math.abs(y - handle.y) <= HANDLE_GRAB_RADIUS
        ) || null;
    }

    function drawSelection(){
        if (!selected || !sharedState.editMode) return;

        const box = boundsOf(selected);
        ctx.save();
        ctx.setLineDash([4, 3]);
        ctx.lineWidth = 1;
        ctx.strokeStyle = SELECTION_COLOR;
        ctx.strokeRect(box.x - 3, box.y - 3, box.width + 6, box.height + 6);
        ctx.setLineDash([]);

        handlesFor(selected).forEach(handle => {
            ctx.fillStyle = "#ffffff";
            ctx.strokeStyle = SELECTION_COLOR;
            ctx.fillRect(handle.x - HANDLE_SIZE / 2, handle.y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
            ctx.strokeRect(handle.x - HANDLE_SIZE / 2, handle.y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
        });
        ctx.restore();
    }

    // ── Hit testing ──

    function hitsAnnotation(clickX, clickY, ann){
        if (ann.type === "ellipse" || ann.type === "circle") return checkEllipseClick(clickX, clickY, ann);
        if (ann.type === "arrow") return checkArrowClick(clickX, clickY, ann);
        if (ann.type === "text") return checkTextClick(clickX, clickY, ann);
        return checkRectangleClick(clickX, clickY, ann);
    }

    // Topmost annotation first, matching the paint order.
    function annotationAt(clickX, clickY){
        for (let i = annotations.length - 1; i >= 0; i--) {
            if (hitsAnnotation(clickX, clickY, annotations[i])) return annotations[i];
        }
        return null;
    }

    function checkRectangleClick(clickX, clickY, ann) {
        const box = boundsOf(ann);
        return clickX >= box.x && clickX <= box.x + box.width && clickY >= box.y && clickY <= box.y + box.height;
    }

    function checkEllipseClick(clickX, clickY, ann) {
        const radiusX = Math.abs(ann.width) || 1;
        const radiusY = Math.abs(ann.height) || 1;
        const dx = (clickX - ann.x) / radiusX;
        const dy = (clickY - ann.y) / radiusY;
        return dx * dx + dy * dy <= 1;
    }

    function checkTextClick(clickX, clickY, ann) {
        const box = boundsOf(ann);
        return clickX >= box.x && clickX <= box.x + box.width && clickY >= box.y && clickY <= box.y + box.height;
    }

    function checkArrowClick(clickX, clickY, ann) {
        const tip = arrowTip(ann);
        const dx = tip.x - ann.x;
        const dy = tip.y - ann.y;
        const lengthSq = dx * dx + dy * dy;
        if (lengthSq === 0) return false;

        const t = ((clickX - ann.x) * dx + (clickY - ann.y) * dy) / lengthSq;
        const tClamped = Math.max(0, Math.min(1, t));
        const closestX = ann.x + tClamped * dx;
        const closestY = ann.y + tClamped * dy;
        const distance = Math.hypot(clickX - closestX, clickY - closestY);

        return distance <= Math.max(6, tip.arrow.shaftWidth);
    }

    function drawEllipse(x, y, width, height){
        ctx.beginPath();
        ctx.ellipse(x, y, Math.abs(width), Math.abs(height), 0, 0, 2 * Math.PI);
        ctx.stroke();
    }

    function drawArrow(ctx, ann) {
        const arrow = arrowGeometry(ann);
        if (arrow.length < 1) return; // a click that never became a drag

        ctx.save();
        ctx.translate(ann.x, ann.y);
        ctx.rotate(arrow.angle);
        ctx.fillStyle = ann.color || "#ff0000";

        // Shaft stops where the head starts, so the tip lands on the point dragged to.
        if (arrow.shaftLength > 0){
            ctx.beginPath();
            ctx.rect(0, -arrow.shaftWidth / 2, arrow.shaftLength, arrow.shaftWidth);
            ctx.fill();
        }

        // Head (triangle)
        ctx.beginPath();
        ctx.moveTo(arrow.shaftLength, -arrow.headHalfWidth);
        ctx.lineTo(arrow.length, 0);
        ctx.lineTo(arrow.shaftLength, arrow.headHalfWidth);
        ctx.closePath();
        ctx.fill();

        ctx.restore();
    }

    function arrowVector(endX, endY){
        let dx = endX - startX;
        let dy = endY - startY;

        if (clipAngleBox?.checked){
            ({ width: dx, height: dy } = clipToAngleSteps(dx, dy));
        }

        if (fixLengthBox?.checked){
            const dragged = Math.hypot(dx, dy);
            if (dragged === 0) return { width: 0, height: 0 };
            const fixed = fixedArrowLength(strokeSize);
            dx = (dx / dragged) * fixed;
            dy = (dy / dragged) * fixed;
        }

        return { width: dx, height: dy };
    }

    function deleteAnnotation(ann) {
        if (ann === selected) clearSelection();
        edited.delete(ann);

        if (ann.id) {
            fetch(`http://localhost:8080/api/fields/annotations/${ann.id}`, { method: "DELETE" })
            .then(response => response.text())
            .then(data => {
                console.log(data);
                annotations = annotations.filter(a => a !== ann);
                redrawAnnotations();
            })
            .catch(error => console.error("Error deleting annotation:", error));
        } else {
            annotations = annotations.filter(a => a != ann);
            redrawAnnotations();
        }
    }

    // ── Edit mode ──

    function select(ann){
        selected = ann;
        if (ann){
            colourPicker.value = ann.color || "#ff0000";
            setStrokeSize(Math.min(10, Math.max(1, Math.round(sizeSteps(ann)))));
            if (ann.type === "text") textInput.value = ann.content || "";
        }
        updateEditHint();
    }

    function clearSelection(){
        selected = null;
        drag = null;
        updateEditHint();
    }

    function updateEditHint(){
        if (!editHint) return;

        if (!sharedState.editMode){
            editHint.hidden = true;
            editHint.textContent = "";
            return;
        }

        editHint.hidden = false;
        if (!selected){
            editHint.textContent = "Click an annotation to edit it";
        } else if (selected.type === "text"){
            editHint.textContent = "Drag to move · Size slider resizes the text · Delete removes it";
        } else {
            editHint.textContent = "Drag to move · drag a handle to resize · Delete removes it";
        }
    }

    function beginEditDrag(clickX, clickY){
        if (selected){
            const handle = handleAt(selected, clickX, clickY);
            if (handle){
                drag = {
                    mode: "resize",
                    handle: handle.name,
                    pointerX: clickX,
                    pointerY: clickY,
                    bounds: boundsOf(selected)
                };
                return;
            }
        }

        const hit = annotationAt(clickX, clickY);
        select(hit);
        drag = hit ? { mode: "move", handle: null, pointerX: clickX, pointerY: clickY, bounds: boundsOf(hit) } : null;
        redrawAnnotations();
    }

    function applyEditDrag(pointerX, pointerY){
        if (!drag || !selected) return;

        if (drag.mode === "move"){
            selected.x += pointerX - drag.pointerX;
            selected.y += pointerY - drag.pointerY;
            drag.pointerX = pointerX;
            drag.pointerY = pointerY;
        } else if (selected.type === "arrow"){
            resizeArrow(pointerX, pointerY);
        } else {
            resizeBox(pointerX, pointerY);
        }

        markEdited(selected);
        redrawAnnotations();
    }

    function resizeArrow(pointerX, pointerY){
        if (drag.handle === "tail"){
            const tip = arrowTip(selected);
            let dx = tip.x - pointerX;
            let dy = tip.y - pointerY;
            if (clipAngleBox?.checked) ({ width: dx, height: dy } = clipToAngleSteps(dx, dy));
            selected.x = pointerX;
            selected.y = pointerY;
            selected.width = dx;
            selected.height = dy;
            return;
        }

        // A fixed-length arrow only re-aims; arrowGeometry re-derives the length from its own flag.
        let dx = pointerX - selected.x;
        let dy = pointerY - selected.y;
        if (clipAngleBox?.checked) ({ width: dx, height: dy } = clipToAngleSteps(dx, dy));
        selected.width = dx;
        selected.height = dy;
    }

    function resizeBox(pointerX, pointerY){
        const box = drag.bounds;
        let left = box.x;
        let top = box.y;
        let right = box.x + box.width;
        let bottom = box.y + box.height;

        if (drag.handle.includes("w")) left = pointerX; else right = pointerX;
        if (drag.handle.includes("n")) top = pointerY; else bottom = pointerY;

        setBounds(selected, {
            x: Math.min(left, right),
            y: Math.min(top, bottom),
            width: Math.max(MIN_SHAPE_SIZE, Math.abs(right - left)),
            height: Math.max(MIN_SHAPE_SIZE, Math.abs(bottom - top))
        });
    }

    function editCursorFor(clickX, clickY){
        if (selected){
            const handle = handleAt(selected, clickX, clickY);
            if (handle) return handle.cursor;
        }
        return annotationAt(clickX, clickY) ? "move" : "default";
    }

    // Self-removing: the canvas is thrown away whenever another image is picked.
    function handleKeyDown(e){
        if (!canvas.isConnected){
            document.removeEventListener("keydown", handleKeyDown);
            return;
        }
        if (!sharedState.editMode || !selected || e.key !== "Delete") return;

        const tag = e.target?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;

        e.preventDefault();
        deleteAnnotation(selected);
    }
    document.addEventListener("keydown", handleKeyDown);

    canvas.addEventListener("mousedown", (e) => {
        e.preventDefault();

        const clickX = e.offsetX;
        const clickY = e.offsetY;

        if (sharedState.editMode) {
            beginEditDrag(clickX, clickY);
            return; // don't start drawing when editing
        }

        if (sharedState.deleteMode) {
            const hit = annotationAt(clickX, clickY);
            if (hit) deleteAnnotation(hit);
            return; // don't start drawing when deleting
        }

        isDrawing = true;
        startX = clickX;
        startY = clickY;
    });

    canvas.addEventListener("mousemove", (e) => {
        if (sharedState.editMode){
            if (drag) applyEditDrag(e.offsetX, e.offsetY);
            else canvas.style.cursor = editCursorFor(e.offsetX, e.offsetY);
            return;
        }

        if (isDrawing){
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();

            ctx.strokeStyle = colourPicker.value;
            ctx.lineWidth = strokeSize;

            const curTool = sharedState.currentTool;

            if (curTool === "rectangle") {
                ctx.strokeRect(startX, startY, e.offsetX - startX, e.offsetY - startY);
            } else if (curTool === "ellipse" || curTool === "circle") {
                const x = (startX + e.offsetX) / 2;
                const y = (startY + e.offsetY) / 2;
                let width = Math.abs(e.offsetX - startX) / 2;
                let height = Math.abs(e.offsetY - startY) / 2;
                if (curTool === "circle"){
                    width = Math.max(width, height);
                    height = Math.max(width, height);
                }
                drawEllipse(x, y, width, height);
            } else if (isDrawing && curTool === "arrow") {
                const { width, height } = arrowVector(e.offsetX, e.offsetY);
                drawArrow(ctx, {
                    x: startX,
                    y: startY,
                    width,
                    height,
                    color: colourPicker.value,
                    strokeWidth: strokeSize,
                    fixedLength: Boolean(fixLengthBox?.checked)
                });
            }
        }
    });

    canvas.addEventListener("mouseup", (e) => {
        if (sharedState.editMode){
            drag = null;
            return;
        }

        if (isDrawing) {
            isDrawing = false;
            const curTool = sharedState.currentTool;

            if (curTool === "rectangle") {
                const ann = {
                    type: "rectangle",
                    x: startX,
                    y: startY,
                    width: e.offsetX - startX,
                    height: e.offsetY - startY,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
                };
                normalizeRectangle(ann);
                annotations.push(ann);
            } else if (curTool === "ellipse" || curTool === "circle") {
                let width = Math.abs(e.offsetX - startX) / 2;
                let height = Math.abs(e.offsetY - startY) / 2;

                if (curTool === "circle"){
                    width = Math.max(width, height);
                    height = Math.max(width, height);
                }

                const ann = {
                    type: "ellipse",
                    x: (startX + e.offsetX) / 2,
                    y: (startY + e.offsetY) / 2,
                    width: width,
                    height: height,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
                };
                annotations.push(ann);
            } else if (curTool === "arrow") {
                // Saved already clipped, so the report draws the same arrow as the screen.
                const { width, height } = arrowVector(e.offsetX, e.offsetY);
                const ann = {
                    type: "arrow",
                    x: startX,
                    y: startY,
                    width,
                    height,
                    color: colourPicker.value,
                    strokeWidth: strokeSize,
                    fixedLength: Boolean(fixLengthBox?.checked)
                };
                annotations.push(ann);
            } else if (curTool === "text") {
                const text = textInput.value.trim();
                if (text) {
                    const ann = {
                        type: "text",
                        x: e.offsetX,
                        y: e.offsetY,
                        content: text,
                        color: colourPicker.value,
                        strokeWidth: strokeSize,
                        height: 1,
                        width: 1,
                    };
                    annotations.push(ann);
                }
            }

            redrawAnnotations();
        }
    });

    canvas.addEventListener("mouseleave", () => {
        drag = null;
    });

    // normalize width and height for rectangles drawn in reverse direction
    function normalizeRectangle(ann) {
        if (ann.width < 0) {
            ann.x += ann.width;
            ann.width = Math.abs(ann.width);
        }
        if (ann.height < 0) {
            ann.y += ann.height;
            ann.height = Math.abs(ann.height);
        }
        return ann;
    }

    saveButton.addEventListener("click", async () => {
        // New annotations are created; ones edited since the last save are updated in place.
        const newAnnotations = annotations.filter(ann => !ann.id);
        const editedAnnotations = [...edited].filter(ann => annotations.includes(ann));

        if (newAnnotations.length === 0 && editedAnnotations.length === 0){
            successMessage.textContent = "Nothing to save.";
            return;
        }

        [...newAnnotations, ...editedAnnotations].forEach(ann => {
            ann.imageDisplayWidth = canvas.width;
            ann.imageDisplayHeight = canvas.height;
        });

        try {
            await Promise.all([
                ...newAnnotations.map(async ann => {
                    const response = await fetch(`http://localhost:8080/api/fields/images/${imageId}/annotations`, {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(ann)
                    });
                    if (!response.ok) throw new Error("Failed to save annotation");

                    // Keep the new id, so a second save edits this annotation instead of duplicating it.
                    const saved = await response.json().catch(() => null);
                    if (saved?.id) ann.id = saved.id;
                }),
                ...editedAnnotations.map(async ann => {
                    const response = await fetch(`http://localhost:8080/api/fields/annotations/${ann.id}`, {
                        method: "PUT",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(ann)
                    });
                    if (!response.ok) throw new Error("Failed to update annotation");
                })
            ]);

            edited.clear();
            successMessage.textContent = "Saved successfully!";
        } catch (error) {
            console.error("Error saving annotations:", error);
            successMessage.textContent = "Could not save annotations.";
        }
    });

}
