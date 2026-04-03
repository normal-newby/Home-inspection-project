import { bookingId } from "./getReport.js";
import { initImagesSlider } from "./loadImages.js";
import { addAnnotationCanvas } from "./imageAnnotation.js";
import { setUpRecommendationsPanel } from "./recommendations.js";
import { fetchExisting, saveFunction } from "../fetchExisting.js";

const params = new URLSearchParams(window.location.search);
const place = params.get("place");
const type = params.get("type");

export const buttons = document.querySelectorAll('.component_button');
export const lowerButtons = document.querySelectorAll('.component_button_lower');

const searchBar = document.querySelector(".field-search");
const contentFields = document.querySelector(".fields");
const selectImageDiv = document.querySelector(".select-image-box");
const existingImageDiv = document.querySelector(".existing-image-div");
const existingImageText = document.querySelector(".existing-image-text");
const existingImageImage = document.querySelector(".existing-image-image");

const saveNoteButton = document.getElementById("save-note");
const noteTextArea = document.getElementById("note-text");

const includeInSummaryRow = document.querySelector(".include-summary-row");
const includeInSummaryBox = document.getElementById("include-in-summary");

const recommendationsButton = document.getElementById("show-recommendation-button");
const recommendationsPanel = document.querySelector(".recommendations-panel");
let currentRecommendationFieldId = null;

let curField = null;

function loadInspectionFieldDefinitions(){
    console.log(`Loading fields for place: ${place}, type: ${type}`);
    fetch(`http://localhost:8080/api/fields/definition/${encodeURIComponent(place)}/${encodeURIComponent(type)}/get`)
    .then(response => response.json())
    .then(fields => {
        contentFields.innerHTML=""; //clear previous
        fields.forEach(field => createField(field));
    });
}

function createField(field){
    //Create the div
    const fieldDiv = document.createElement("div");
    fieldDiv.classList.add("inspection-field");

    //Create header
    const fieldHeader = document.createElement("h3");
    fieldHeader.classList.add("field-header");
    fieldHeader.textContent = field.fieldName;

    //Place to store buttons
    const valuesDiv = document.createElement("div");
    valuesDiv.classList.add("values");

    renderFields(valuesDiv, field);

    //Add everything to their parent container
    fieldDiv.appendChild(fieldHeader);
    fieldDiv.appendChild(valuesDiv);

    contentFields.appendChild(fieldDiv);
}

async function renderFields(valuesDiv, field){
    //Fetch user existing inputs
    const existingFields = await getAlreadyExistingFields(field.fieldName);

    if (existingFields) {
        existingFields.forEach(existingField => {
            createExistingField(valuesDiv, existingField); // For already inputted fields
        });
    }

    //Create buttons
    field.possibleValues.forEach(value => {
        createButton(valuesDiv, value, field.fieldName); // For possible values to choose from
    });
}

async function getAlreadyExistingFields(name){ //fetches user past stored data
    try {
        const url = `http://localhost:8080/api/fields/${bookingId}/${encodeURIComponent(place)}/${encodeURIComponent(type)}?name=${encodeURIComponent(name)}`;
        const result = await fetch(url);
        if (!result.ok) {
            const text = await result.text();
            throw new Error(`Fetch failed (${result.status}): ${text}`);
        }
        const fields = await result.json();
        return fields;
    } catch (error){
        console.log(error);
        return null;
    }
}

function createButton(parent, value, fieldName){ //Creates buttons for possible values in each field
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.textContent = value.value;
    button.addEventListener("click", () => saveNewInspectionField(value.value, fieldName));
    parent.appendChild(button);
}

function createExistingField(parent, field){ // Creates buttons for already inputted values, with the possibility to delete or change image
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.classList.add("selected-button");
    button.textContent = field.selectedValue.value;
    button.dataset.id = field.id;

    button.addEventListener("contextmenu", (e) => { // Right click to delete
        e.preventDefault();
        deleteInspectionField(button.dataset.id)
    });

    button.addEventListener("dblclick", (e) => { // Double click to change image
        e.preventDefault();
        button.classList.add("current-button");

        curField = button.dataset.id;

        console.log(field); 

        showExistingImage(field.inspectionImages, button.dataset.id);
        selectImageDiv.hidden = false;

        addExistingImages(bookingId, selectImageDiv, button.dataset.id); // Add images to select from

        fetchExisting(`http://localhost:8080/api/fields/${button.dataset.id}/note`, noteTextArea); // Fetch existing note for the field

        if (type === "recommendations"){ // If field = recommendation, show button
            recommendationsButton.hidden = false;
            currentRecommendationFieldId = button.dataset.id;

            includeInSummaryRow.hidden = false;
            fetchInSummary(button.dataset.id) // Fetch in summary for field
        }
    }); 
    parent.appendChild(button);
}

async function addExistingImages(bookingId, selectImageDiv, fieldId){
    const track = await initImagesSlider(bookingId, selectImageDiv, true); // Initialize slider with only images not used for report
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("dblclick", (e) => {
            e.preventDefault();
            selectImageFunction(img.dataset.imageId, fieldId); // If image is double clicked, link it to the field
        });
    });
}


const sharedState = { currentTool: null, deleteMode: false }; // Shared state for annotation tools

const toolsDiv = existingImageDiv.querySelector(".annotation-tools");
const rectTool = toolsDiv.querySelector("#rect-tool");
const ellipseTool = toolsDiv.querySelector("#ellipse-tool");
const arrowTool = toolsDiv.querySelector("#arrow-tool");
const textTool = toolsDiv.querySelector("#add-text");
const deleteModeButton = toolsDiv.querySelector("#delete-mode");

function removeActiveStates(except) {
    toolsDiv.querySelectorAll("button").forEach(
        btn => { 
            if (btn !== except) btn.classList.remove("active"); 
        }
    );
}

function handleClick(button, tool) {
    const canvas = existingImageDiv.querySelector("canvas");
    removeActiveStates(button);
    if (tool === "delete") {
        sharedState.deleteMode = !sharedState.deleteMode;
        sharedState.currentTool = null;
        canvas.style.cursor = sharedState.deleteMode ? "pointer" : "default";
        deleteModeButton.textContent = sharedState.deleteMode ? "Exit Delete Mode" : "Delete Mode";
        deleteModeButton.classList.toggle("active", sharedState.deleteMode);
        return;
    }
    if (button.classList.contains("active")) {
        sharedState.currentTool = null;
        button.classList.remove("active");
        canvas.style.cursor = "default";
    } else {
        sharedState.currentTool = tool;
        button.classList.add("active");
        canvas.style.cursor = "crosshair";
    }
    sharedState.deleteMode = false;
    deleteModeButton.textContent = "Delete Mode";
}

rectTool.addEventListener("click", () => handleClick(rectTool, "rectangle"));
ellipseTool.addEventListener("click", () => handleClick(ellipseTool, "ellipse"));
arrowTool.addEventListener("click", () => handleClick(arrowTool, "arrow"));
textTool.addEventListener("click", () => handleClick(textTool, "text"));
deleteModeButton.addEventListener("click", () => handleClick(deleteModeButton, "delete"));

function clearCanvas(){
    existingImageText.innerHTML = "";
    existingImageImage.src = "";
    // Clear previous canvas
    const existingCanvas = existingImageDiv.querySelector("canvas");
    if (existingCanvas) existingCanvas.remove();
    // Clear previous gallery
    const existingGallery = selectImageDiv.querySelector(".image-gallery");
    if (existingGallery) existingGallery.remove();
    toolsDiv.hidden = true;
}

function showExistingImage(images, fieldId){
    clearCanvas();
    
     if (!images || images.length === 0) {
        existingImageText.textContent = "No images selected";
        existingImageImage.hidden = true;
        return;
    }

    existingImageDiv.hidden = false;
    existingImageText.textContent = "Select an image to annotate";

    // Build thumbnail grid
    const gallery = document.createElement("div");
    gallery.className = "image-gallery";

    images.forEach(image => {
        const thumb = document.createElement("img");
        thumb.src = `http://localhost:8080/api/images/file/${image.id}`;
        thumb.className = "gallery-thumb";
        thumb.addEventListener("click", () => {
            // Reset states
            sharedState.currentTool = null;
            sharedState.deleteMode = false;
            removeActiveStates(null);

            // Deselect all
            gallery.querySelectorAll(".gallery-thumb").forEach(t => t.classList.remove("selected"));
            thumb.classList.add("selected");

            // Clear previous canvas
            const prevCanvas = existingImageDiv.querySelector("canvas");
            if (prevCanvas) prevCanvas.remove();
            toolsDiv.hidden = true;

            existingImageImage.hidden = false;
            existingImageImage.src = thumb.src;
            existingImageImage.onload = () => {
                addAnnotationCanvas(existingImageDiv, existingImageImage, image.id, sharedState);
                toolsDiv.hidden = false;
            };
        });

        thumb.addEventListener("contextmenu", (e) => {
            e.preventDefault();
            if (confirm("Are you sure you want to remove this image from the field?")) {
                deleteImageFromField(image.id, fieldId);
            }
        });
                    
        gallery.appendChild(thumb);
    });

    selectImageDiv.insertBefore(gallery, existingImageDiv);
}

// Endpoints for fields and images

function saveNewInspectionField(value, fieldName){
    const url = `http://localhost:8080/api/fields/${bookingId}/${encodeURIComponent(place)}/${encodeURIComponent(type)}?name=${encodeURIComponent(fieldName)}&value=${encodeURIComponent(value)}`;
    fetch(url,
        {method : "POST"}
    )
    .then(response => response.json())
    .then(msg => {
        console.log(msg);
    })
    .catch(error => console.log(error));
}

function deleteInspectionField(id){
    fetch(`http://localhost:8080/api/fields/${id}`,
        { method : "DELETE" }
    );
}

function selectImageFunction(imageId, fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/${imageId}`,
        { method: "PUT" }
    )
    .then(console.log("Image linked"))
    .catch(error => console.log(error))
}

function deleteImageFromField(imageId, fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/${imageId}`,
            { method: "DELETE" }
        )
        .then(() => {
            thumb.remove();
            clearCanvas();
        })
        .catch(error => console.log(error)
    );
}

function updateInSummary() {
    const includeBool = includeInSummaryBox.checked;
    fetch(`http://localhost:8080/api/fields/${curField}/summary`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(includeBool),
    });
}

function fetchInSummary(fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/summary`)
    .then(response => response.json())
    .then(inSummary => {
        if (inSummary) {
            includeInSummaryBox.checked = true;
        } else {
            includeInSummaryBox.checked = false;
        }
    })
    .catch(error => console.log(error));
}

//Buttons

searchBar.addEventListener("input", () => { //search function for fields
    const query = searchBar.value.toLowerCase();
    document.querySelectorAll(".inspection-field").forEach(field => {
        const fieldName = field.querySelector(".field-header").textContent.toLowerCase();
        const match = fieldName.includes(query);
        field.style.display = match ? "" : "none";
    });
});

buttons.forEach(button => { //place buttons
    if (button.getAttribute("data-target") === place) button.classList.add("active");
    button.addEventListener("click", () => {
        const newPlace = button.getAttribute("data-target");
        window.location.href = `report_writing.html?id=${bookingId}&place=${newPlace}&type=${type}`;
    });
});

lowerButtons.forEach(button => { //type buttons
    if (button.getAttribute("data-target") === type) button.classList.add("active");
    button.addEventListener("click", () => {
        const newType = button.getAttribute("data-target")
        window.location.href = `report_writing.html?id=${bookingId}&place=${place}&type=${newType}`
    });
});

saveNoteButton.addEventListener("click", (e) => saveFunction(e, noteTextArea));

recommendationsButton.addEventListener("click", () => {
    selectImageDiv.hidden = true;
    recommendationsPanel.hidden = false;
    if (currentRecommendationFieldId) {
        setUpRecommendationsPanel(currentRecommendationFieldId);
    } else {
        console.warn("No recommendation field selected to load");
    }
});

includeInSummaryBox.addEventListener("click", () => updateInSummary());


document.addEventListener("click", (e) => {
    const clickedInsideSelect = selectImageDiv.contains(e.target);
    const clickedInsideRecommendations = recommendationsPanel.contains(e.target) || recommendationsButton.contains(e.target);

    if (!clickedInsideSelect && !clickedInsideRecommendations) {
        document.querySelectorAll(".value-button").forEach(btn => btn.classList.remove("current-button"));
        selectImageDiv.hidden = true;
        recommendationsPanel.hidden = true;
    }
});

loadInspectionFieldDefinitions();