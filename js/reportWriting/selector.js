import { sectionData } from './sectionData.js';
import { changeButtonColour, changeTableColour } from './colourHandling.js';
const selector = document.getElementById('selector');
const selectorButton = document.getElementById("selector_button");
const sections = document.querySelectorAll('.component_section');
export const buttons = document.querySelectorAll('.component_button');
export const lowerButtons = document.querySelectorAll('.component_button_lower');
export let lastClicked = "roofing";
export let lastClickedSub = "description";

buttons.forEach(button => {
    button.addEventListener('click', () => respondOnClick(button));
});
function populateSelector(){
    selector.innerHTML = "";
    for (let formSection of sectionData[lastClicked][lastClickedSub]){
        let option = document.createElement('option');
        option.value = formSection["name"];
        option.text = formSection["name"];
        selector.appendChild(option);
    }
}
function generateDiv(event){
    hideAllSections();
    const sectionDiv = document.getElementById(lastClicked).querySelector(`.${lastClickedSub}`);
    updateButtonHiders(sectionDiv);
    changeHidden(event, false, sectionDiv);
}
function updateButtonHiders(sectionDiv){
    //event listener for all "hide" buttons
    sectionDiv.addEventListener("click", (e) => {
        if (e.target.classList.contains("hide_table_button")) {
            const target = e.target.getAttribute("data-target");
            changeHidden(target, true, sectionDiv);
            changeTableColour(sectionDiv);
        }
    });
}
function changeHidden(target, bool, sectionDiv){
    let newHtml = "";
    for (let formSection of sectionData[lastClicked][lastClickedSub]){
        console.log(formSection["hidden"]);
        if (formSection["name"] === target){
            formSection["hidden"] = bool;
        }
         if (formSection["hidden"] === false){
            newHtml += "<table class='information_table'><thead><tr><th><h2>" + formSection["name"] + "</h2>";
            newHtml += `<button class = "hide_table_button" data-target = "${formSection["name"]}">hide</button>`;
            newHtml += "</th></tr></thead><tbody><tr><td>work in progress</td></tr></tbody></table>";
        }
    }
    sectionDiv.innerHTML = newHtml;
}
function hideAllSections(){
    sections.forEach(section => {
        section.hidden = true;
    });
    document.getElementById(lastClicked).hidden = false;
    // If the target is one of the subsections, we want to show only that subsection within the last clicked main section
    document.getElementById(lastClicked).querySelectorAll('.description, .limitations, .recommendations').forEach(subsection => {
        subsection.hidden = true;
    });
    document.getElementById(lastClicked).querySelector(`.${lastClickedSub}`).hidden = false;
}
function respondOnClick(button) { // General function to respond to button clicks
    const target = button.getAttribute('data-target');
    changeButtonColour(button, target, buttons, lowerButtons);
    if (target === "description" || target === "limitations" || target === "recommendations") {
        lastClickedSub = target;
    } else { 
        lastClicked = target;
        lastClickedSub = "description";
        changeButtonColour(document.querySelector(`[data-target="${lastClickedSub}"]`), lastClickedSub, buttons, lowerButtons);
    }
    populateSelector();
    generateDiv(sectionData[lastClicked][lastClickedSub][0]["name"]);
    changeTableColour(document.getElementById(lastClicked).querySelector(`.${lastClickedSub}`));
}

selectorButton.addEventListener("click", () =>{
    generateDiv(selector.value);
    changeTableColour(document.getElementById(lastClicked).querySelector(`.${lastClickedSub}`))
})

window.onload = () => {
    respondOnClick(document.querySelector(`[data-target="${lastClicked}"]`));
}