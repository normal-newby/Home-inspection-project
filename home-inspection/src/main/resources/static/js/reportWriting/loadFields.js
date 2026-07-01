import { bookingId } from "./getReport.js";
import { initImagesSlider } from "./loadImages.js";
import { addAnnotationCanvas } from "./imageAnnotation.js";
import { setUpRecommendationsPanel } from "./recommendations.js";
import { fetchExisting, saveFunction } from "../fetchExisting.js";

const params = new URLSearchParams(window.location.search);
let place = params.get("place");
let type = params.get("type");

export const buttons = document.querySelectorAll('.component_button:not(.component_button_lower)');
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

async function loadInspectionFieldDefinitions(){
    console.log(`Loading fields for place: ${place}, type: ${type}`);
    const response = await fetch(`http://localhost:8080/api/fields/definition/${encodeURIComponent(place)}/${encodeURIComponent(type)}`);
    const fields = await response.json();

    const fieldsWithExisting = await Promise.all(fields.map(async field => {
        const existingFields = await getAlreadyExistingFields(field.id) || [];
        return {
            field,
            existingFields,
            existingCount: Array.isArray(existingFields) ? existingFields.length : 0,
            pinned: field.expandedByDefault ?? false, 
        }; // return number of existing fields and number of pinned fields for sorting
    }));

    fieldsWithExisting.sort((a, b) => { // propagates existing, pinned to front
        if (b.existingCount !== a.existingCount) return b.existingCount - a.existingCount;
        if (b.pinned !== a.pinned) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0);
        return a.field.fieldName.localeCompare(b.field.fieldName);
    });

    contentFields.innerHTML = "";
    fieldsWithExisting.forEach(({ field, existingFields, existingCount, pinned }) => {
        field.isShowing = existingCount > 0 || pinned; // Show if there are existing fields or if pinned
        createField(field, existingFields);
    });
}

function createField(field, existingFields = []){
    //Create the div
    const fieldDiv = document.createElement("div");
    fieldDiv.classList.add("inspection-field");
    fieldDiv.dataset.id = field.id;

    //Create header
    const fieldHeader = document.createElement("div");
    fieldHeader.classList.add("field-header-row");

    const fieldTitle = document.createElement("h3");
    fieldTitle.classList.add("field-header");
    fieldTitle.textContent = field.fieldName;

    //show and hide
    const controls = document.createElement("div");
    controls.classList.add("field-controls");

    const showButton = document.createElement("button");
    showButton.textContent = field.isShowing ? "Hide" : "Show";
    showButton.classList.add("show-button");

    const keepShowButton = document.createElement("button");
    keepShowButton.textContent = field.expandedByDefault ? "Unpin" : "Pin";
    keepShowButton.classList.add("keep-show-button");

    controls.appendChild(showButton);
    controls.appendChild(keepShowButton);
    fieldHeader.appendChild(fieldTitle);
    fieldHeader.appendChild(controls);

    //Place to store buttons
    const valuesDiv = document.createElement("div");
    valuesDiv.classList.add("values");

    fieldDiv.appendChild(fieldHeader);
    fieldDiv.appendChild(valuesDiv);
    contentFields.appendChild(fieldDiv);
    valuesDiv.hidden = !field.isShowing;
    console.log(valuesDiv.hidden);

    let loaded = false;

    async function expand(){
        if (!loaded) {
            await renderFields(valuesDiv, field, existingFields);
            loaded = true;
        }
        valuesDiv.hidden = false;
        showButton.textContent = "Hide";
    }

    function collapse(){
        valuesDiv.hidden = true;
        showButton.textContent = "Show";
    }

    showButton.addEventListener("click", (e) => {
        e.stopPropagation();
        if (valuesDiv.hidden) {
            expand();
        } else {
            collapse();
        }
    });

    keepShowButton.addEventListener("click", async (e) => {
        e.stopPropagation();
        const newState = !field.expandedByDefault;
        field.expandedByDefault = newState;
        keepShowButton.textContent = newState ? "Unpin" : "Pin";
        keepShowButton.classList.toggle("active", newState);
        
        await fetch(`http://localhost:8080/api/fields/definition/${field.id}/expanded`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(newState),
        });

        if (newState) {
            expand();
        } 
    });

    if (field.isShowing) {
        expand();
    }
}

async function renderFields(valuesDiv, field, existingFields = null){
    const response = await fetch(`http://localhost:8080/api/fields/definition/${field.id}/values`);
    const fullField = await response.json();

    //Fetch user existing inputs if they were not already loaded
    const existing = (existingFields && existingFields.length > 0)
    ? existingFields
    : await getAlreadyExistingFields(field.id) ?? [];

    existing.forEach(existingField => {
        createExistingField(valuesDiv, existingField); // For already inputted fields
    });

    //Create buttons
    fullField.possibleValues.forEach(value => {
        createButton(valuesDiv, value, field.id); // For possible values to choose from
    });
}

async function getAlreadyExistingFields(fieldId){ //fetches user past stored data
    try {
        const url = `http://localhost:8080/api/fields/${bookingId}/${fieldId}`;
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

function createButton(parent, value, fieldId){ //Creates buttons for possible values in each field
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.textContent = value.value;
    button.addEventListener("click", () => saveNewInspectionField(value.value, fieldId));
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
        deleteInspectionField(button.dataset.id);
        loadInspectionFieldDefinitions();
    });

    button.addEventListener("dblclick", (e) => { // Double click to change image
        e.preventDefault();
        button.classList.add("current-button");

        curField = button.dataset.id;

        console.log(field); 

        showExistingImage(field.inspectionImages, button.dataset.id);
        selectImageDiv.hidden = false;

        addExistingImages(bookingId, selectImageDiv, button.dataset.id, field.inspectionImages); // Add images to select from

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

async function addExistingImages(bookingId, selectImageDiv, fieldId, images){
    const track = await initImagesSlider(bookingId, selectImageDiv, true, 4); // Initialize slider with only images not used for report
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("dblclick", (e) => {
            e.preventDefault();
            selectImageFunction(bookingId, selectImageDiv, img.dataset.imageId, fieldId, images); // If image is double clicked, link it to the field
        });
    });
}


const sharedState = { currentTool: null, deleteMode: false }; // Shared state for annotation tools

const toolsDiv = existingImageDiv.querySelector(".annotation-tools");

const rectTool = toolsDiv.querySelector("#rect-tool");
const circleTool = toolsDiv.querySelector("#circle-tool");
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
circleTool.addEventListener("click", () => handleClick(circleTool, "circle"));
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

function saveNewInspectionField(value, fieldDefinitionId){
    const url = `http://localhost:8080/api/fields/${bookingId}/${fieldDefinitionId}/${value}`;
    fetch(url,
        {method : "POST"}
    )
    .then(response => response.json())
    .then(msg => {
        console.log(msg);
        loadInspectionFieldDefinitions(); // Reload fields to show new input
    })
    .catch(error => console.log(error));
}

function deleteInspectionField(id){
    fetch(`http://localhost:8080/api/fields/${id}`,
        { method : "DELETE" }
    );
}

function selectImageFunction(bookingId, selectImageDiv, imageId, fieldId, images){
    fetch(`http://localhost:8080/api/fields/${fieldId}/${imageId}`,
        { method: "PUT" }
    )
    .then(() => {
        console.log("Image linked");
        images.push({ id: imageId });
        addExistingImages(bookingId, selectImageDiv, fieldId, images); // Refresh gallery
        showExistingImage(images, fieldId); // Refresh existing images
    })
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

        buttons.forEach(b => b.classList.remove("active"));
        button.classList.add("active");

        place = newPlace;
        history.pushState(null, "", `report_writing.html?id=${bookingId}&place=${place}&type=${type}`);
        
        loadInspectionFieldDefinitions();
    });
});

lowerButtons.forEach(button => { //type buttons
    if (button.getAttribute("data-target") === type) button.classList.add("active");
    button.addEventListener("click", () => {
        const newType = button.getAttribute("data-target");

        lowerButtons.forEach(b => b.classList.remove("active"));
        button.classList.add("active");

        type = newType;
        history.pushState(null, "", `report_writing.html?id=${bookingId}&place=${place}&type=${type}`);

        loadInspectionFieldDefinitions();
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