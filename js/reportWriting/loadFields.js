import { bookingId } from "./getReport.js";
import { initImagesSlider } from "./loadImages.js";

const params = new URLSearchParams(window.location.search);
const place = params.get("place");
const type = params.get("type");

export const buttons = document.querySelectorAll('.component_button');
export const lowerButtons = document.querySelectorAll('.component_button_lower');
export let lastClicked = "roofing";
export let lastClickedSub = "description";

const contentFields = document.querySelector(".fields");
const selectImageDiv = document.querySelector(".select-image-box");
const existingImageDiv = document.querySelector(".existing-image-div");
const existingImageText = document.querySelector(".existing-image-text");
const existingImageImage = document.querySelector(".existing-image-image");

function loadInspectionFieldDefinitions(){
    fetch(`http://localhost:8080/api/fields/definition/${place}/${type}/get`)
    .then(response => response.json())
    .then(fields => {
        contentFields.innerHTML=""; //clear previous
        fields.forEach(field => createField(field))
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

    existingFields.forEach(existingField => {
        createExistingField(valuesDiv, existingField);
    });

    //Create buttons
    field.possibleValues.forEach(value => {
        createButton(valuesDiv, value, field.fieldName);
    });
}

async function getAlreadyExistingFields(name){ //fetches user past stored data
    try {
        const result = await fetch(`http://localhost:8080/api/fields/${bookingId}/${place}/${type}/${name}`)
        const fields = await result.json();
        return fields;
    } catch (error){
        console.log(error);
        return null;
    }
}

function createButton(parent, value, fieldName){
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.textContent = value.value;
    button.addEventListener("click", () => saveNewInspectionField(value.value, fieldName));
    parent.appendChild(button);
}

function createExistingField(parent, field){
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.classList.add("selected-button");
    button.textContent = field.selectedValue.value;
    button.dataset.id = field.id;
    button.addEventListener("contextmenu", (e) => {
        e.preventDefault();
        deleteInspectionField(button.dataset.id)
    });
    button.addEventListener("dblclick", (e) => {
        e.preventDefault();
        console.log(field); 
        showExistingImage(field.inspectionImage);
        selectImageDiv.hidden = false;
        addExistingImages(bookingId, selectImageDiv, button.dataset.id);
    });
    parent.appendChild(button);
}

async function addExistingImages(bookingId, selectImageDiv, fieldId){
    const track = await initImagesSlider(bookingId, selectImageDiv);
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("dblclick", (e) => {
            e.preventDefault();
            selectImageFunction(img.dataset.imageId, fieldId);
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

function showExistingImage(image){
    existingImageText.innerHTML = "";
    existingImageImage.src = "";
    if (!image){
        existingImageText.textContent = "No images selected";
    } else {
        existingImageText.textContent = "This is your selected image";
        existingImageImage.src = `http://localhost:8080/api/images/file/${image.id}`
    }
}

function saveNewInspectionField(value, fieldName){
    fetch(`http://localhost:8080/api/fields/${bookingId}/${place}/${type}/${fieldName}/${value}`,
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

//Buttons

buttons.forEach(button => { //place buttons
    button.addEventListener("click", () => {
        const newPlace = button.getAttribute("data-target")
        window.location.href = `report_writing.html?id=${bookingId}&place=${newPlace}&type=${type}`
    });
});

lowerButtons.forEach(button => { //type buttons
    button.addEventListener("click", () => {
        const newType = button.getAttribute("data-target")
        window.location.href = `report_writing.html?id=${bookingId}&place=${place}&type=${newType}`
    });
});

document.addEventListener("click", (e) => {
    if (!selectImageDiv.contains(e.target)) {
        selectImageDiv.hidden = true;
    }
});

loadInspectionFieldDefinitions();