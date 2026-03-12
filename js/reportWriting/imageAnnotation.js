export function addAnnotationCanvas(existingImageDiv, existingImageImage, fieldId) {
    // Create canvas
    const canvas = document.createElement("canvas");
    canvas.width = existingImageImage.width;
    canvas.height = existingImageImage.height;
    canvas.style.position = "absolute";
    canvas.style.top = existingImageImage.offsetTop + "px";
    canvas.style.left = existingImageImage.offsetLeft + "px";
    canvas.style.pointerEvents = "auto";
    existingImageDiv.appendChild(canvas);

    const ctx = canvas.getContext("2d");

    // Get tools from HTML
    const toolsDiv = existingImageDiv.querySelector(".annotation-tools");

    const tools = toolsDiv.querySelectorAll("button");
    const rectTool = toolsDiv.querySelector("#rect-tool");
    const ellipseTool = toolsDiv.querySelector("#ellipse-tool");
    const arrowTool = toolsDiv.querySelector("#arrow-tool");
    const deleteModeButton = toolsDiv.querySelector("#delete-mode");

    const saveButton = toolsDiv.querySelector("#save-annotations");
    const colourPicker = toolsDiv.querySelector("#color-picker");
    const strokeSlider = toolsDiv.querySelector("#stroke-size");
    const strokeSizeValue = toolsDiv.querySelector("#stroke-size-value");

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
    fetch(`http://localhost:8080/api/fields/${fieldId}/annotations`)
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
                }
            }
            return; // don't start drawing when deleting
        }

        if (currentTool === "rectangle" || currentTool === "ellipse" || currentTool === "arrow") {
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
            fetch(`http://localhost:8080/api/fields/${fieldId}/annotations/save`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ann)
            });
        });
        alert("New annotations saved!");
    });
}