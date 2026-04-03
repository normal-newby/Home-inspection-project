export function addAnnotationCanvas(existingImageDiv, existingImageImage, imageId) {
    // Create canvas
    const canvas = document.createElement("canvas");
    
    canvas.width = existingImageDiv.offsetWidth;
    canvas.height = existingImageDiv.offsetHeight;
    canvas.style.position = "absolute";
    canvas.style.top = "0";
    canvas.style.left = "0";
    canvas.style.zIndex = "10";

    canvas.style.pointerEvents = "auto";
    existingImageDiv.appendChild(canvas);

    const ctx = canvas.getContext("2d");

    // Get tools from HTML
    const toolsDiv = existingImageDiv.querySelector(".annotation-tools");

    const tools = toolsDiv.querySelectorAll("button");
    const rectTool = toolsDiv.querySelector("#rect-tool");
    const ellipseTool = toolsDiv.querySelector("#ellipse-tool");
    const arrowTool = toolsDiv.querySelector("#arrow-tool");
    const textTool = toolsDiv.querySelector("#add-text");
    const deleteModeButton = toolsDiv.querySelector("#delete-mode");

    const saveButton = toolsDiv.querySelector("#save-annotations");
    const colourPicker = toolsDiv.querySelector("#color-picker");
    const strokeSlider = toolsDiv.querySelector("#stroke-size");
    const strokeSizeValue = toolsDiv.querySelector("#stroke-size-value");
    const textInput = toolsDiv.querySelector("#text-tool");

    let currentTool = null;
    let deleteMode = false;
    let startX, startY, isDrawing = false;
    let strokeSize = parseInt(strokeSlider?.value || "1", 10);
    let annotations = [];

    strokeSlider.addEventListener("input", () => {
        strokeSize = parseInt(strokeSlider.value, 10);
        strokeSizeValue.textContent = strokeSize;
    });

    // Load existing annotations
    fetch(`http://localhost:8080/api/fields/${imageId}/annotations`)
        .then(response => response.json())
        .then(data => {
            annotations = data;
            redrawAnnotations();
        }
    );

    function redrawAnnotations() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        annotations.forEach(ann => {
            ctx.strokeStyle = ann.color || "#ff0000";
            ctx.fillStyle = ann.color || "#ff0000";
            ctx.lineWidth = ann.strokeWidth || 1;
            
            if (ann.type === "rectangle") {
                ctx.strokeRect(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "ellipse") {
                ctx.beginPath();
                ctx.ellipse(ann.x, ann.y, ann.width, ann.height, 0, 0, 2 * Math.PI);
                ctx.stroke();
            } else if (ann.type === "arrow") {
                drawArrow(ctx, ann);
            } else if (ann.type === "text") {
                ctx.font = `${ann.strokeWidth * 10}px Arial`;
                ctx.fillStyle = ann.color || "#000000";
                ctx.fillText(ann.content, ann.x, ann.y);
            }
        });
    }

    function removeActiveStates(button) {
        tools.forEach(btn => {
            if (button !== btn) {
                btn.classList.remove("active");
            }
        });
    }

    function handleClick(button, tool){
        // Deactivate all other tools
        removeActiveStates(button);

        // Handle delete mode toggle separately
        if (tool === "delete") {
            deleteMode = !deleteMode;
            currentTool = null;
            canvas.style.cursor = deleteMode ? "pointer" : "default";
            deleteModeButton.textContent = deleteMode ? "Exit Delete Mode" : "Delete Mode";
            deleteModeButton.classList.toggle("active", deleteMode);
            return;
        }

        // Toggle the clicked button
        if (button.classList.contains("active")) {
            currentTool = null;
            button.classList.remove("active");
            canvas.style.cursor = "default";
        } else {
            currentTool = tool;
            button.classList.add("active");
            canvas.style.cursor = "crosshair";
        }
        // Deactivate delete mode when switching tools
        deleteMode = false;
        deleteModeButton.textContent = "Delete Mode";
    }

    rectTool.addEventListener("click", () => {
        handleClick(rectTool, "rectangle");
    });

    ellipseTool.addEventListener("click", () => {
        handleClick(ellipseTool, "ellipse");
    });

    arrowTool.addEventListener("click", () => {
        handleClick(arrowTool, "arrow");
    });

    textTool.addEventListener("click", () => {
        handleClick(textTool, "text");
    });

    deleteModeButton.addEventListener("click", () => {
        handleClick(deleteModeButton, "delete");
    });

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
        const x2 = ann.x2;
        const y2 = ann.y2;
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

    function drawArrow(ctx, ann) {
        const startX = ann.x;
        const startY = ann.y;
        const endX = ann.x2;
        const endY = ann.y2;
        const strokeWidth = ann.strokeWidth*3 || 6;
        const color = ann.color || "#ff0000";

        const dx = endX - startX;
        const dy = endY - startY;
        const length = Math.hypot(dx, dy);
        if (length < 2) return;

        const headLength = Math.max(5, strokeWidth * 2);
        const shaftLength = Math.max(0, length - headLength);
        const angle = Math.atan2(dy, dx);

        ctx.save();
        ctx.translate(startX, startY);
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
            fetch(`http://localhost:8080/api/annotations/${ann.id}/delete`, { method: "DELETE" })
            .then(response => response.text())
            .then(data => console.log(data))
            .catch(error => console.error("Error deleting annotation:", error));
        }
        annotations = annotations.filter(a => a !== ann);
        redrawAnnotations();
    }

    canvas.addEventListener("mousedown", (e) => {
    e.preventDefault();

        const clickX = e.offsetX;
        const clickY = e.offsetY;

        if (deleteMode) {
            // Check if click is on an annotation
            for (let i = annotations.length - 1; i >= 0; i--) {
                const ann = annotations[i];
                if (ann.type === "rectangle") {
                    if (checkRectangleClick(clickX, clickY, ann)) {
                        deleteAnnotation(ann);
                        break;
                    }
                } else if (ann.type === "ellipse") {
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

        if (currentTool === "rectangle" || currentTool === "ellipse" || currentTool === "arrow" || currentTool === "text") {
            e.preventDefault();
            isDrawing = true;
            startX = clickX;
            startY = clickY;
        }
    });

    canvas.addEventListener("mousemove", (e) => {
        if (isDrawing && currentTool === "rectangle") {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();
            ctx.strokeStyle = colourPicker.value;
            ctx.lineWidth = strokeSize;
            ctx.strokeRect(startX, startY, e.offsetX - startX, e.offsetY - startY);
        } else if (isDrawing && currentTool === "ellipse") {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();
            ctx.strokeStyle = colourPicker.value;
            ctx.lineWidth = strokeSize;
            const centerX = (startX + e.offsetX) / 2;
            const centerY = (startY + e.offsetY) / 2;
            const radiusX = Math.abs(e.offsetX - startX) / 2;
            const radiusY = Math.abs(e.offsetY - startY) / 2;
            ctx.beginPath();
            ctx.ellipse(centerX, centerY, radiusX, radiusY, 0, 0, 2 * Math.PI);
            ctx.stroke();
        } else if (isDrawing && currentTool === "arrow") {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();
            const preview = {
                x: startX,
                y: startY,
                x2: e.offsetX,
                y2: e.offsetY,
                color: colourPicker.value,
                strokeWidth: strokeSize
            };
            drawArrow(ctx, preview);
        }
    });

    canvas.addEventListener("mouseup", (e) => {
        if (isDrawing) {
            isDrawing = false;
            if (currentTool === "rectangle") {
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
            } else if (currentTool === "ellipse") {
                const ann = {
                    type: "ellipse",
                    x: (startX + e.offsetX) / 2,
                    y: (startY + e.offsetY) / 2,
                    width: Math.abs(e.offsetX - startX) / 2,
                    height: Math.abs(e.offsetY - startY) / 2,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
                };
                annotations.push(ann);
            } else if (currentTool === "arrow") {
                const ann = {
                    type: "arrow",
                    x: startX,
                    y: startY,
                    x2: e.offsetX,
                    y2: e.offsetY,
                    color: colourPicker.value,
                    strokeWidth: strokeSize
                };
                annotations.push(ann);
            } else if (currentTool === "text") {
                const text = textInput.value.trim();
                if (text) {
                    const ann = {
                        type: "text",
                        x: e.offsetX,
                        y: e.offsetY,
                        content: text,
                        color: colourPicker.value,
                        strokeWidth: strokeSize
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
            
            fetch(`http://localhost:8080/api/fields/${imageId}/annotations/save`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ann)
            });
        });
    });

    removeActiveStates(null); // Ensure no tool is active on load
}