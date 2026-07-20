const sectionsConfig = {
    direction: {
        selector: ".recommendations-section:nth-of-type(1)",
        options: ["Various", "Throughout", "North", "South", "East", "West", "Front", "Rear", "Left", "Right"],
    },
    floorLevel: {
        selector: ".recommendations-section:nth-of-type(2)",
        options: ["Basement", "Ground", "1st Floor", "2nd Floor", "3rd Floor", "Attic"],
    },
    room: {
        selector: ".recommendations-section:nth-of-type(3)",
        options: ["Living room", "Dining room", "Kitchen", "Bathroom", "Master Bathroom",
            "Hallway Bathroom", "Ensuite Bathroom", "Powder Room",
            "Bedroom", "Master Bedroom", "Family Room", "Sunroom",
            "Laundry Area", "Office", "Utility Room", "Furnace Room",
            "Garage", "Carport", "Porch", "Hall", "Foyer", "Staircase", "Panel",
            "Balcony", "Deck", "Cold Room", "Roof", "Addition", "Dinette", "Washroom", "Half Bathroom"
        ],
    },
    task: {
        selector: ".recommendations-section:nth-of-type(4)",
        options: ["Repair", "Replace", "Repair or replace", "Futher evaluation", "Provide",
            "Improve", "Monitor", "Service", "Clean", "Correct", "Request Disclosure",
            "Request Demo", "Service Annually", "Inspect Annually", "Demolish", "Remodel",
            "Upgrade", "Remove", "Protect", "Patch", "Paint", "Seal", "Patch and Paint"
        ],
    },
    time: {
        selector: ".recommendations-section:nth-of-type(5)",
        options: ["Immediate", "Within 1 year", "Within 2 years", "Within 3 years",
            "Within 4 years", "Within 5 years", "Unpredictable", "Unknown", "Discretionary",
        "Ongoing", "Regular Maintenance", "If Necessary", "When Remodelling", "When Necessary",
    "As soon as possible", "As soon as practical", "Before Use"],
    },
    cost: {
        selector: ".recommendations-section:nth-of-type(6)",
        // This section is rendered as inputs instead of option buttons.
    },
    implication: {
        selector: ".recommendations-section:nth-of-type(7)",
    }
};

const recommendationsWrapper = document.querySelector(".recommendations-section-wrapper");
const submitButton = document.getElementById("submit-recommendations-button");

function createOptionButton(value, onSelect) {
    const btn = document.createElement("button");
    btn.textContent = value;
    btn.dataset.value = value;
    btn.addEventListener("click", () => {
        onSelect(value);
    });
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
    for (let i = 0; i < Object.keys(sectionsConfig).length; i++) {

        //Get the information from dictionary
        const sectionKey = Object.keys(sectionsConfig)[i];
        const config = sectionsConfig[sectionKey];

        //Create the recommendation section
        const recommendationSection = document.createElement("div");
        recommendationSection.classList.add("recommendations-section");

        //Create label
        const label = document.createElement("div");
        label.classList.add("recommendations-section-label");
        label.textContent = sectionKey.charAt(0).toUpperCase() + sectionKey.slice(1);

        recommendationSection.appendChild(label);

        //Create container for options
        const optionsContainer = document.createElement("div");
        optionsContainer.classList.add("recommendations-options");

        if (sectionKey === "cost") {
            optionsContainer.appendChild(createCostInputs());
        } else if (sectionKey === "implication"){
            optionsContainer.appendChild(createImplicationInput());
        } else {
            //Create buttons
            config.options.forEach(option => {
                const button = createOptionButton(option, () => {
                    button.classList.toggle("selected");
                });
                optionsContainer.appendChild(button);
            });
        }

        recommendationSection.appendChild(optionsContainer);

        recommendationsWrapper.appendChild(recommendationSection);
    };
}

function highlightExistingValues(recommendations) {
    if (!recommendations) return;

    const keys = ["direction", "floorLevel", "room", "task", "time"];

    keys.forEach(key => {
        const value = recommendations[key];
        if (value === null || value === undefined) return;

        const section = sectionsConfig[key];
        if (!section) return;

        const sectionRoot = document.querySelector(section.selector);
        if (!sectionRoot) return;

        const selectedValues = value ? value.split(",").map(v => v.trim()) : [];

        const buttons = sectionRoot.querySelectorAll(".recommendations-options button");
        buttons.forEach(btn => {
            btn.classList.toggle("selected", selectedValues.includes(btn.dataset.value));
        });
    });

    // Handle cost inputs separately (lower/upper)
    const costSection = document.querySelector(sectionsConfig.cost.selector);
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

    const implicationSection = document.querySelector('[data-implication-key="implication"]');
    if (implicationSection && recommendations.implication) {
        implicationSection.value = recommendations.implication;
    }
}

function collectRecommendationsFromUI() {
    const payload = {};

    Object.keys(sectionsConfig).forEach(sectionKey => {
        if (sectionKey === "cost") return;

        const sectionRoot = document.querySelector(sectionsConfig[sectionKey].selector);
        if (!sectionRoot) return;

        const selected = Array.from(sectionRoot.querySelectorAll(".recommendations-options button.selected"));
        payload[sectionKey] = selected.length > 0 ?
            selected.map(btn => btn.dataset.value).join(", ") : null;
    });

    // Cost is stored as two separate fields (lower_cost and upper_cost)
    const costSection = document.querySelector(sectionsConfig.cost.selector);
    if (costSection) {
        const lowerInput = costSection.querySelector('input[data-cost-key="lower_cost"]');
        const upperInput = costSection.querySelector('input[data-cost-key="upper_cost"]');

        payload.lower_cost = lowerInput && lowerInput.value.trim() !== "" ? lowerInput.value.trim() : null;
        payload.upper_cost = upperInput && upperInput.value.trim() !== "" ? upperInput.value.trim() : null;
    }

    const implicationSection = document.querySelector('[data-implication-key="implication"]');
    payload.implication = implicationSection && implicationSection.value.trim() !== "" ? implicationSection.value.trim() : null;

    return payload;
}

function submitRecommendations(fieldId, saveAsDefault = false) {
    const payload = collectRecommendationsFromUI();

    console.log(payload);

    fetch(`http://localhost:8080/api/fields/${fieldId}/recommendations?saveAsDefaultImplication=${saveAsDefault}`, {
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
        alert(`Could not save recommendations: ${error.message}`);
    });
}

export function setUpRecommendationsPanel(fieldId) {
    renderSections();

    submitButton.addEventListener("click", () => {
        const saveAsDefault = document.getElementById("default-implication-checkbox").checked;
        submitRecommendations(fieldId, saveAsDefault);
    });

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