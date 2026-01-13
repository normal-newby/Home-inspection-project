const params = new URLSearchParams(window.location.search);
const place = params.get("place");
const type = params.get("type");

import { changeButtonColour, changeTableColour } from './colourHandling.js';
const selector = document.getElementById('selector');
const selectorButton = document.getElementById("selector_button");
export const buttons = document.querySelectorAll('.component_button');
export const lowerButtons = document.querySelectorAll('.component_button_lower');
export let lastClicked = "roofing";
export let lastClickedSub = "description";

const contentFields = document.querySelector(".fields");

function loadInspectionFieldDefinitions(){
    fetch(`http://localhost:8080/api/fields/definition/${place}/${type}/get`)
    .then(response => response.json())
    .then(fields => {
        contentFields.innerHTML=""; //clear previous
        fields.forEach(field => createField(field))
    });
}

function createField(field){
    console.log(field);
    //Create the div
    const fieldDiv = document.createElement("div");
    fieldDiv.classList.add("inspection-field");

    //Create header
    const fieldHeader = document.createElement("h3");
    fieldHeader.classList.add("field-header");
    fieldHeader.textContent = field.fieldName;

    //Place to store buttons
    const values = document.createElement("div");
    values.classList.add("values");

    //Create buttons
    field.possibleValues.forEach(value => {
        createButton(values, value);
    });

    //Add everything to their parent container
    fieldDiv.appendChild(fieldHeader);
    fieldDiv.appendChild(values);

    contentFields.appendChild(fieldDiv);
}

function createButton(parent, value){
    const button = document.createElement("button");
    button.classList.add("value-button");
    button.textContent = value.value;
    parent.appendChild(button);
}

loadInspectionFieldDefinitions();