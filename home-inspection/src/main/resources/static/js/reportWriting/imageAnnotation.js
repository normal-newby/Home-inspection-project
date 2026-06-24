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
        const x = ann.x;
        const y = ann.y;
        const endX = ann.width + x;
        const endY = ann.height + y;

        const dx = endX - x;
        const dy = endY - y;
        const angle = Math.atan2(dy, dx);

        const width = Math.abs(ann.width);
        const height = Math.abs(ann.height);

        const length = Math.max(width, height);
        const headLength = length/2;
        const shaftLength = length/2;

        const strokeWidth = length/3;
        const color = ann.color || "#ff0000";

        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(angle);

        // Shaft
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.rect(0, -strokeWidth / 2, shaftLength, strokeWidth);
        ctx.fill();

        // Head (triangle)
        ctx.beginPath();
        ctx.moveTo(shaftLength, -strokeWidth);
        ctx.lineTo(length, 0);
        ctx.lineTo(shaftLength, strokeWidth);
        ctx.closePath();
        ctx.fill();

        ctx.restore();
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
                const preview = {
                    x: startX,
                    y: startY,
                    width: e.offsetX - startX,
                    height: e.offsetY - startY,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
                };
                drawArrow(ctx, preview);
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
                const ann = {
                    type: "arrow",
                    x: startX,
                    y: startY,
                    width: e.offsetX - startX,
                    height: e.offsetY - startY,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
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