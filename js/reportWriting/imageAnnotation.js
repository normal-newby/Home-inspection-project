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
    const rectTool = toolsDiv.querySelector("#rect-tool");
    const textTool = toolsDiv.querySelector("#text-tool");
    const deleteModeButton = toolsDiv.querySelector("#delete-mode");
    const saveButton = toolsDiv.querySelector("#save-annotations");
    const colourPicker = toolsDiv.querySelector("#color-picker");

    let currentTool = null;
    let deleteMode = false;
    let startX, startY, isDrawing = false;
    let annotations = [];

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
            if (ann.type === "rectangle") {
                ctx.strokeRect(ann.x, ann.y, ann.width, ann.height);
            } else if (ann.type === "text") {
                ctx.fillText(ann.content, ann.x, ann.y);
            }
        });
    }

    rectTool.addEventListener("click", () => {
        currentTool = "rectangle";
        deleteMode = false;
        canvas.style.cursor = "crosshair";
    });

    textTool.addEventListener("click", () => {
        const text = prompt("Enter text:");
        if (text) {
            const ann = { type: "text", x: 50, y: 50, content: text, color: colourPicker.value };
            annotations.push(ann);
            redrawAnnotations();
        }
    });

    deleteModeButton.addEventListener("click", () => {
        deleteMode = !deleteMode;
        currentTool = null;
        canvas.style.cursor = deleteMode ? "pointer" : "default";
        deleteModeButton.textContent = deleteMode ? "Exit Delete Mode" : "Delete Mode";
        deleteModeButton.classList.toggle("active", deleteMode);
    });

    canvas.addEventListener("mousedown", (e) => {
        if (deleteMode) {
            // Check if click is on an annotation
            const clickX = e.offsetX;
            const clickY = e.offsetY;
            for (let i = annotations.length - 1; i >= 0; i--) {
                const ann = annotations[i];
                if (ann.type === "rectangle") {
                    if (clickX >= ann.x && clickX <= ann.x + ann.width && clickY >= ann.y && clickY <= ann.y + ann.height) {
                        // Delete from backend if it has id
                        if (ann.id) {
                            fetch(`http://localhost:8080/api/annotations/${ann.id}`, { method: "DELETE" });
                        }
                        annotations.splice(i, 1);
                        redrawAnnotations();
                        break;
                    }
                } else if (ann.type === "text") {
                    // Approximate text area
                    const textWidth = ctx.measureText(ann.content).width;
                    const textHeight = 20; // approximate
                    if (clickX >= ann.x && clickX <= ann.x + textWidth && clickY >= ann.y - textHeight && clickY <= ann.y) {
                        if (ann.id) {
                            fetch(`http://localhost:8080/api/annotations/${ann.id}`, { method: "DELETE" });
                        }
                        annotations.splice(i, 1);
                        redrawAnnotations();
                        break;
                    }
                }
            }
        } else if (currentTool === "rectangle") {
            isDrawing = true;
            startX = e.offsetX;
            startY = e.offsetY;
        }
    });

    canvas.addEventListener("mousemove", (e) => {
        if (isDrawing && currentTool === "rectangle") {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();
            ctx.strokeStyle = colourPicker.value;
            ctx.strokeRect(startX, startY, e.offsetX - startX, e.offsetY - startY);
        }
    });

    canvas.addEventListener("mouseup", (e) => {
        if (isDrawing && currentTool === "rectangle") {
            isDrawing = false;
            const ann = {
                type: "rectangle",
                x: startX,
                y: startY,
                width: e.offsetX - startX,
                height: e.offsetY - startY,
                color: colourPicker.value
            };
            annotations.push(ann);
            redrawAnnotations();
        }
    });

    saveButton.addEventListener("click", () => {
        // Save only new annotations (without id)
        const newAnnotations = annotations.filter(ann => !ann.id);
        newAnnotations.forEach(ann => {
            fetch(`http://localhost:8080/api/fields/${fieldId}/annotations`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ann)
            });
        });
        alert("New annotations saved!");
    });
}