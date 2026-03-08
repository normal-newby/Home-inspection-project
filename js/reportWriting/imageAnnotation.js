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

    let currentTool = null;
    let startX, startY, isDrawing = false;
    let annotations = [];

    // Load existing annotations
    fetch(`http://localhost:8080/api/fields/${fieldId}/annotations`)
        .then(response => response.json())
        .then(data => {
            annotations = data;
            redrawAnnotations();
        });

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

    toolsDiv.querySelector("#rect-tool").addEventListener("click", () => currentTool = "rectangle");
    toolsDiv.querySelector("#text-tool").addEventListener("click", () => {
        const text = prompt("Enter text:");
        if (text) {
            const ann = { type: "text", x: 50, y: 50, content: text, color: toolsDiv.querySelector("#color-picker").value };
            annotations.push(ann);
            redrawAnnotations();
        }
    });

    canvas.addEventListener("mousedown", (e) => {
        if (currentTool === "rectangle") {
            isDrawing = true;
            startX = e.offsetX;
            startY = e.offsetY;
        }
    });

    canvas.addEventListener("mousemove", (e) => {
        if (isDrawing && currentTool === "rectangle") {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            redrawAnnotations();
            ctx.strokeStyle = toolsDiv.querySelector("#color-picker").value;
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
                color: toolsDiv.querySelector("#color-picker").value
            };
            annotations.push(ann);
            redrawAnnotations();
        }
    });

    toolsDiv.querySelector("#save-annotations").addEventListener("click", () => {
        // Save all annotations
        annotations.forEach(ann => {
            fetch(`http://localhost:8080/api/fields/${fieldId}/annotations`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ann)
            });
        });
        alert("Annotations saved!");
    });
}