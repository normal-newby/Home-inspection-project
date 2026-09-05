import { notify } from "../ui/dialog.js";
const definitionTypes = ["direction", "floorLevel", "room", "task", "time", "cost", "implication"];
let sectionValues; /* = {Structure
    type : list({id, value})
}*/

const recommendationsWrapper = document.querySelector(".recommendations-section-wrapper");
const submitButton = document.getElementById("submit-recommendations-button");
const diagramsButton = document.getElementById("attach-diagrams-button");
const recommendationsPanel = document.querySelector(".recommendations-panel");

// The panel is set up again every time it opens, so its listeners must not be added again
// with it.
let listenersBound = false;

// The field the buttons act on, read at click time so they follow the selected item.
let openFieldId = null;

function createOptionButton(definition, type) {
    const btn = document.createElement("button");
    btn.textContent = definition.value;
    btn.dataset.value = definition.value;
    btn.dataset.id = definition.id;
    btn.addEventListener("click", () => {
        btn.classList.toggle("selected");
    });
    btn.addEventListener("contextmenu", () => {
        deleteDefinition(btn);
    })
    return btn;
}

function createCostInputs() {
    const container = document.createElement("div");
    container.classList.add("recommendations-cost-inputs");

    const lowerLabel = document.createElement("label");
    lowerLabel.textContent = "Lower cost";
    const lowerInput = document.createElement("input");
    lowerInput.type = "number";
    lowerInput.min = "0";
    lowerInput.placeholder = "Min";
    lowerInput.dataset.costKey = "lower_cost";
    lowerInput.classList.add("recommendations-cost-input");

    const upperLabel = document.createElement("label");
    upperLabel.textContent = "Upper cost";
    const upperInput = document.createElement("input");
    upperInput.type = "number";
    upperInput.min = "0";
    upperInput.placeholder = "Max";
    upperInput.dataset.costKey = "upper_cost";
    upperInput.classList.add("recommendations-cost-input");

    container.appendChild(lowerLabel);
    container.appendChild(lowerInput);
    container.appendChild(upperLabel);
    container.appendChild(upperInput);

    return container;
}

function createImplicationInput() {
    const textarea = document.createElement("textarea");
    textarea.placeholder = "Describe the implication...";
    textarea.dataset.implicationKey = "implication";
    textarea.classList.add("recommendations-implication-input");
    return textarea;
}

function renderSections() {
    recommendationsWrapper.innerHTML = "";
    for (let i = 0; i < definitionTypes.length; i++) {

        //Get type of definition
        const type = definitionTypes[i];

        //Create the recommendation section
        const recommendationSection = document.createElement("div");
        recommendationSection.classList.add("recommendations-section");
        recommendationSection.id = "type-" + type;

        //Create label
        const label = document.createElement("div");
        label.classList.add("recommendations-section-label");
        label.textContent = type.charAt(0).toUpperCase() + type.slice(1);
        recommendationSection.appendChild(label);

        //Create container for options
        const optionsContainer = document.createElement("div");
        optionsContainer.classList.add("recommendations-options");

        if (type === "cost") {
            optionsContainer.appendChild(createCostInputs());
        } else if (type === "implication"){
            optionsContainer.appendChild(createImplicationInput());
        } else {
            // Create container for definitions
            const definitionsContainer = document.createElement("div");
            definitionsContainer.classList.add("recommendations-definitions");

            // Create buttons
            if (sectionValues[type] !== null && sectionValues[type] !== undefined) {
                sectionValues[type].forEach(definition => {
                    const btn = createOptionButton(definition, type);
                    definitionsContainer.appendChild(btn);
                });
            }
            optionsContainer.appendChild(definitionsContainer);

            // Create container for adding
            const addContainer = document.createElement("div");
            addContainer.classList.add("recommendations-add");

            // Create "add definition" button
            const definitionInput = document.createElement("input");
            definitionInput.id = "definition-input";
            const btn = document.createElement("button");
            btn.textContent = "Add a value for: " + type;
            btn.addEventListener("click", () => {
                const value = definitionInput.value;
                addDefinition(type, value);
            });

            addContainer.appendChild(definitionInput);
            addContainer.appendChild(btn);
            optionsContainer.appendChild(addContainer);
        }

        recommendationSection.appendChild(optionsContainer);
        recommendationsWrapper.appendChild(recommendationSection);
    };
}

function highLightSelectedButtons(recommendations, definition) {
    const value = recommendations[definition];
    if (value === null || value === undefined) return;

    const section = sectionValues[definition];
    if (!section) return;

    const sectionRoot = document.getElementById("type-" + definition).querySelector(".recommendations-definitions");
    if (!sectionRoot) return;

    const selectedValues = value ? value.split(",").map(v => v.trim()) : [];

    const buttons = sectionRoot.querySelectorAll("button");
    buttons.forEach(btn => {
        btn.classList.toggle("selected", selectedValues.includes(btn.dataset.value));
    });
}

function highlightExistingValues(recommendations) {
    if (!recommendations) return;

    console.log("Highlighting existing values:", recommendations);

    definitionTypes.forEach(definition => {
        if (definition === "cost" || definition === "implication") return; // Skip cost and implication for button highlighting
        highLightSelectedButtons(recommendations, definition);
    });

    // Handle cost inputs separately (lower/upper)
    const costSection = document.getElementById("type-cost");
    if (costSection) {
        const lowerInput = costSection.querySelector('input[data-cost-key="lower_cost"]');
        const upperInput = costSection.querySelector('input[data-cost-key="upper_cost"]');

        if (lowerInput && recommendations.lower_cost !== undefined && recommendations.lower_cost !== null) {
            lowerInput.value = recommendations.lower_cost;
        }
        if (upperInput && recommendations.upper_cost !== undefined && recommendations.upper_cost !== null) {
            upperInput.value = recommendations.upper_cost;
        }
    }

    const implicationSection = document.getElementById("type-implication").querySelector('textarea[data-implication-key="implication"]');
    if (implicationSection && recommendations.implication) {
        implicationSection.value = recommendations.implication;
    }
}

function collectRecommendationsFromUI() {
    const payload = {};

    definitionTypes.forEach(definition => {
        if (definition === "cost" || definition === "implication") return;

        const sectionRoot = document.getElementById("type-" + definition).querySelector(".recommendations-definitions");
        if (!sectionRoot) return;

        const selected = Array.from(sectionRoot.querySelectorAll("button.selected"));
        payload[definition] = selected.length > 0 ?
            selected.map(btn => btn.dataset.value).join(", ") : null;
    });

    // Cost is stored as two separate fields (lower_cost and upper_cost)
    const costSection = document.getElementById("type-cost");
    if (costSection) {
        const lowerInput = costSection.querySelector('input[data-cost-key="lower_cost"]');
        const upperInput = costSection.querySelector('input[data-cost-key="upper_cost"]');

        payload.lower_cost = lowerInput && lowerInput.value.trim() !== "" ? lowerInput.value.trim() : null;
        payload.upper_cost = upperInput && upperInput.value.trim() !== "" ? upperInput.value.trim() : null;
    }

    const implicationSection = document.getElementById("type-implication").querySelector('textarea[data-implication-key="implication"]');
    payload.implication = implicationSection && implicationSection.value.trim() !== "" ? implicationSection.value.trim() : null;

    return payload;
}

function submitRecommendations(fieldId, saveAsDefault = false) {
    const payload = collectRecommendationsFromUI();

    console.log(payload);

    return fetch(`http://localhost:8080/api/fields/${fieldId}/recommendations?saveAsDefaultImplication=${saveAsDefault}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`Failed to save recommendations: ${response.status} ${text}`);
            });
        }
        return response.json();
    })
    .then(saved => {
        const messageSpan = document.querySelector(".recommendations-display-message");
        messageSpan.textContent = "Recommendations saved successfully!";
        setTimeout(() => {
            messageSpan.textContent = "";
        }, 5000);
        highlightExistingValues(saved);
    })
    .catch(error => {
        console.error(error);
        notify(`Could not save recommendations: ${error.message}`, { error: true });
    });
}

export async function setUpRecommendationsPanel(fieldId) {
    openFieldId = fieldId;

    await getSectionsConfig();
    renderSections();

    if (!listenersBound) {
        listenersBound = true;

        submitButton.addEventListener("click", () => {
            const saveAsDefault = document.getElementById("default-implication-checkbox").checked;
            submitRecommendations(openFieldId, saveAsDefault);
        });
    }

    fetch(`http://localhost:8080/api/fields/${fieldId}/recommendations`)
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`Failed to load recommendations: ${response.status} ${text}`);
            });
        }
        return response.json();
    })
    .then(recommendations => {
        highlightExistingValues(recommendations);
    })
    .catch(error => {
        console.error(error);
    });
}

async function getSectionsConfig(){
    await fetch(`http://localhost:8080/api/recommendation-definition`, {
        method: "GET",
        headers: { "Content-Type": "application/json" }
    })
    .then(response => {
        if (!response.ok) {
            console.log("Failed to fetch definitions");
        }
        return response.json();
    })
    .then(sections => {
        sectionValues = sections;
    })
    .catch(error => {
        console.log(error)
    });
}

function appendDefinitionToList(type, definition){
    const definitionsContainer = document.getElementById("type-" + type).querySelector(".recommendations-definitions");
    if (!definitionsContainer) return;

    const btn = createOptionButton(definition, type);
    definitionsContainer.appendChild(btn);
}

async function addDefinition(type, value){
    const body = {
        type: type,
        value, value
    };

    const res = await fetch(`http://localhost:8080/api/recommendation-definition`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    })
    if (!res.ok) {
        console.log("failed to add definition");
        return;
    }
    const definition = await res.json();
    console.log(definition);
    appendDefinitionToList(type, definition);
}

function deleteDefinition(button){
    fetch(`http://localhost:8080/api/recommendation-definition/${button.dataset.id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
    })
    .then(res => {
        if (res.ok) {
            button.remove();
        }
        else console.log("failed to delete")
    })
    .catch(error => console.log(error));
}
// --- Supporting diagrams ---

const DIAGRAMS_URI = "http://localhost:8080/api/recommendation-diagrams";

export async function setUpDiagramsButton(fieldId) {
    if (!diagramsButton) return;

    openFieldId = fieldId;
    diagramsButton.textContent = "Diagrams";

    if (!diagramsButton.dataset.bound) {
        diagramsButton.dataset.bound = "true";
        diagramsButton.addEventListener("click", () => openDiagramPicker());
    }

    try {
        const res = await fetch(`${DIAGRAMS_URI}/field/${fieldId}`);
        if (!res.ok) return;

        const attached = await res.json().catch(() => null);
        const count = attached?.diagramIds?.length ?? 0;
        if (count > 0) diagramsButton.textContent = `Diagrams (${count})`;
    } catch (error) {
        console.error("Could not read attached diagrams:", error);
    }
}

async function openDiagramPicker() {
    if (!openFieldId) return;

    // Picking happens on another page, so anything typed into the panel is saved on the way
    // out rather than lost to the navigation.
    if (!recommendationsPanel.hidden) {
        const saveAsDefault = document.getElementById("default-implication-checkbox").checked;
        await submitRecommendations(openFieldId, saveAsDefault);
    }

    const returnTo = window.location.pathname + window.location.search;
    window.location.href = `recommendation_diagrams.html?fieldId=${encodeURIComponent(openFieldId)}`
        + `&returnTo=${encodeURIComponent(returnTo)}`;
}
