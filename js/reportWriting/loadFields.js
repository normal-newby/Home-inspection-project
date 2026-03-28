import { bookingId } from "./getReport.js";
import { initImagesSlider } from "./loadImages.js";
import { addAnnotationCanvas } from "./imageAnnotation.js";
import { setUpRecommendationsPanel } from "./recommendations.js";

const params = new URLSearchParams(window.location.search);
const place = params.get("place");
const type = params.get("type");

export const buttons = document.querySelectorAll('.component_button');
export const lowerButtons = document.querySelectorAll('.component_button_lower');

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

        showExistingImage(field.inspectionImage, button.dataset.id);
        selectImageDiv.hidden = false;

        addExistingImages(bookingId, selectImageDiv, button.dataset.id); // Add images to select from
         
        if (type === "recommendations"){ // If field = recommendation, show button
            recommendationsButton.hidden = false;
            currentRecommendationFieldId = button.dataset.id;

            includeInSummaryRow.hidden = false;
            fetchExistingNote(button.dataset.id); // Fetch existing note for the field
            fetchInSummary(button.dataset.id) // Fetch in summary for field
        }
    }); 
    parent.appendChild(button);
}

function fetchExistingNote(fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/note`)
    .then(response => {
        if (!response.ok) return null; // If no note exists, return null
        return response.text();
    })
    .then(note => {
        if (note) {
            noteTextArea.value = note.content;
        } else {
            noteTextArea.value = "";
        }
    })
    .catch(error => console.log(error));
}

async function addExistingImages(bookingId, selectImageDiv, fieldId){
    const track = await initImagesSlider(bookingId, selectImageDiv);
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("dblclick", (e) => {
            e.preventDefault();
            selectImageFunction(img.dataset.imageId, fieldId); // If image is double clicked, link it to the field
        });
    });
}

function selectImageFunction(imageId, fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/${imageId}`,
        { method: "PUT" }
    )
    .then(console.log("Image linked"))
    .catch(error => console.log(error))
}

function showExistingImage(image, fieldId){
    existingImageText.innerHTML = "";
    existingImageImage.src = "";
    // Clear previous canvas
    const existingCanvas = existingImageDiv.querySelector("canvas");
    if (existingCanvas) existingCanvas.remove();
    const tools = existingImageDiv.querySelector(".annotation-tools");
    tools.hidden = true;

    if (!image){
        existingImageText.textContent = "No images selected";
    } else {
        existingImageDiv.hidden = false;
        existingImageText.textContent = "This is your selected image";
        existingImageImage.src = `http://localhost:8080/api/images/file/${image.id}`;
        existingImageImage.onload = () => {
            addAnnotationCanvas(existingImageDiv, existingImageImage, fieldId);
            tools.hidden = false;
        };
    }
}

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

function saveNote(note, fieldId){
    fetch(`http://localhost:8080/api/fields/${fieldId}/note`,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ content: note })
        }
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
        console.log(inSummary);
        if (inSummary) {
            includeInSummaryBox.checked = true;
        } else {
            includeInSummaryBox.checked = false;
        }
    })
    .catch(error => console.log(error));
}

//Buttons

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

saveNoteButton.addEventListener("click", (e) => {
    e.preventDefault();
    const note = noteTextArea.value;
    console.log("Saving note:", note);
    const activeFieldButton = document.querySelector(".value-button.selected-button.current-button");
    if (activeFieldButton) {
        const fieldId = activeFieldButton.dataset.id;
        saveNote(note, fieldId);
    } else {
        console.log("No active field selected for note.");
    }
});

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
        selectImageDiv.hidden = true;
        recommendationsPanel.hidden = true;
    }
});

loadInspectionFieldDefinitions();