// Arrow stuff
const ARROW_SHAFT_WIDTH_PER_STEP = 5; 
const ARROW_HEAD_LENGTH_RATIO = 2.6; 
const ARROW_HEAD_WIDTH_RATIO = 1.6; 
const ARROW_MAX_HEAD_SHARE = 0.6;
const ARROW_FIXED_HEAD_MULTIPLE = 2;

const CLIP_STEP_RADIANS = Math.PI / 4;

function fixedArrowLength(size){
    return size * ARROW_SHAFT_WIDTH_PER_STEP * ARROW_HEAD_LENGTH_RATIO * ARROW_FIXED_HEAD_MULTIPLE;
}

function arrowGeometry(ann){
    const size = Number(ann.strokeWidth) > 0 ? Number(ann.strokeWidth) : 1;
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

    const successMessage = toolsDiv.querySelector("#success-message");

    let startX, startY, isDrawing = false;
    let strokeSize = parseInt(strokeSlider?.value || "1", 10);
    let annotations = [];

    strokeSlider.addEventListener("input", () => {
        strokeSize = parseInt(strokeSlider.value, 10);
        strokeSizeValue.textContent = strokeSize;
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
                annotations = data;
            }
            redrawAnnotations();
        })
        .catch(error => {
            console.log("No existing annotations found");
            annotations = [];
        });

    function redrawAnnotations() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        annotations.forEach(ann => {
            console.log(ann);
            ctx.strokeStyle = ann.color || "#ff0000";
            ctx.fillStyle = ann.color || "#ff0000";
            ctx.lineWidth = ann.strokeWidth || 1;
            
            if (ann.type === "rectangle") {
                ctx.strokeRect(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "ellipse" || ann.type === "circle") {
                drawEllipse(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "arrow") {
                drawArrow(ctx, ann);
            } else if (ann.type === "text") {
                ctx.font = `${ann.strokeWidth * 10}px Arial`;
                ctx.fillStyle = ann.color || "#000000";
                ctx.fillText(ann.content, ann.x, ann.y);
            }
        });
    }

    function checkRectangleClick(clickX, clickY, ann) {
        return clickX >= ann.x && clickX <= ann.x + ann.width && clickY >= ann.y && clickY <= ann.y + ann.height;
    }

    function checkEllipseClick(clickX, clickY, ann) {
        const dx = clickX - ann.x;
        const dy = clickY - ann.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        return distance <= ann.width; // Assuming width is the radius for hit detection
    }

    function checkTextClick(clickX, clickY, ann) {
        const textWidth = ctx.measureText(ann.content).width;
        const textHeight = parseInt(ctx.font, 10);
        return clickX >= ann.x && clickX <= ann.x + textWidth && clickY >= ann.y - textHeight && clickY <= ann.y;
    }

    function checkArrowClick(clickX, clickY, ann) {
        const x1 = ann.x;
        const y1 = ann.y;
        const x2 = x1 + ann.width;
        const y2 = y1 + ann.height;
        const dx = x2 - x1;
        const dy = y2 - y1;
        const lengthSq = dx * dx + dy * dy;
        if (lengthSq === 0) return false;

        const t = ((clickX - x1) * dx + (clickY - y1) * dy) / lengthSq;
        const tClamped = Math.max(0, Math.min(1, t));
        const closestX = x1 + tClamped * dx;
        const closestY = y1 + tClamped * dy;
        const distance = Math.hypot(clickX - closestX, clickY - closestY);

        const tolerance = Math.max(6, (ann.strokeWidth || 1) * 1.5);
        return distance <= tolerance;
    }

    function drawEllipse(x, y, width, height){
        ctx.beginPath();
        ctx.ellipse(x, y, width, height, 0, 0, 2 * Math.PI);
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

    canvas.addEventListener("mousedown", (e) => {
        e.preventDefault();

        const clickX = e.offsetX;
        const clickY = e.offsetY;

        if (sharedState.deleteMode) {
            // Check if click is on an annotation
            for (let i = annotations.length - 1; i >= 0; i--) {
                const ann = annotations[i];
                if (ann.type === "rectangle") {
                    if (checkRectangleClick(clickX, clickY, ann)) {
                        deleteAnnotation(ann);
                        break;
                    }
                } else if (ann.type === "ellipse" || ann.type === "circle") {
                    if (checkEllipseClick(clickX, clickY, ann)) {
                        deleteAnnotation(ann);
                        break;
                    }
                } else if (ann.type === "arrow") {
                    if (checkArrowClick(clickX, clickY, ann)) {
                        deleteAnnotation(ann);
                        break;
                    }
                } else if (ann.type === "text") {
                    if (checkTextClick(clickX, clickY, ann)) {
                        deleteAnnotation(ann);
                        break;
                    }
                }
            }
            return; // don't start drawing when deleting
        }

        isDrawing = true;
        startX = clickX;
        startY = clickY;
    });

    canvas.addEventListener("mousemove", (e) => {
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

    saveButton.addEventListener("click", () => {
        // Save only new annotations (without id)
        const newAnnotations = annotations.filter(ann => !ann.id);

        newAnnotations.forEach(ann => {
            ann.imageDisplayWidth = existingImageImage.offsetWidth;
            ann.imageDisplayHeight = existingImageImage.offsetHeight;

            console.log('displayWidth:', ann.imageDisplayWidth, 'displayHeight:', ann.imageDisplayHeight);
            
            fetch(`http://localhost:8080/api/fields/images/${imageId}/annotations`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ann)
            }).then(response => {
                if (response.ok) {
                    successMessage.textContent = "Saved successfully!";
                }
            }).catch(error => {
                console.error("Error saving annotation:", error);
            });
        });
    });

}