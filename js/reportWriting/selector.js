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

function loadInspectionFieldDefinitions(){
    fetch(`http://localhost:8080/api/fields/definition/${place}/${type}/get`)
    .then(response => response.json())
    .then(fields => {
        console.log(fields);
    });
}

loadInspectionFieldDefinitions();