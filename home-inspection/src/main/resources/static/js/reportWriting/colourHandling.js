import {lastClicked} from "./loadFields.js";

const colours = {
    roofing: '#a68368ff',
    exterior: '#7BB369',
    structure: '#777777ff',
    electrical: '#FFA500',
    heating: '#ff6d4dff',
    cooling: '#399cffff',
    insulation: '#ffb6c1',
    plumbing: '#ADD8E6',
    interior: '#D2D1CD',
}

export function changeButtonColour(button, target, buttons, lowerButtons){
    if (target === "description" || target === "limitations" || target === "recommendations") {
        lowerButtons.forEach(btn => {
            btn.style.backgroundColor = "";
        });
        button.style.backgroundColor = colours[lastClicked];
    } else{
        buttons.forEach(btn => {
            btn.style.backgroundColor = "";
        });
        button.style.backgroundColor = colours[target];
    }
}

export function changeTableColour(element){
    element.querySelectorAll("thead").forEach(thead => {
        thead.style.backgroundColor = colours[lastClicked];
    });
}