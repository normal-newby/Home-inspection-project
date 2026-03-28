const sectionsConfig = {
    direction: {
        selector: ".recommendations-section:nth-of-type(1)",
        options: ["North", "South", "East", "West"],
    },
    floorLevel: {
        selector: ".recommendations-section:nth-of-type(2)",
        options: ["Basement", "Ground", "1st Floor", "2nd Floor", "Attic"],
    },
    room: {
        selector: ".recommendations-section:nth-of-type(3)",
        options: ["Kitchen", "Bathroom", "Bedroom", "Living Room", "Garage", "Other"],
    },
    task: {
        selector: ".recommendations-section:nth-of-type(4)",
        options: ["Repair", "Replace", "Inspect", "Maintain", "Monitor"],
    },
    time: {
        selector: ".recommendations-section:nth-of-type(5)",
        options: ["Immediate", "Within 30 days", "Within 90 days", "Within 6 months", "At next inspection"],
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
                    optionsContainer.querySelectorAll("button").forEach(b => b.classList.remove("selected"));
                    button.classList.add("selected");
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
        console.log(value);
        if (value === null || value === undefined) return;

        const section = sectionsConfig[key];
        if (!section) return;

        const sectionRoot = document.querySelector(section.selector);
        if (!sectionRoot) return;

        const buttons = sectionRoot.querySelectorAll(".recommendations-options button");
        buttons.forEach(btn => {
            btn.classList.toggle("selected", btn.dataset.value === value);
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

        const selected = sectionRoot.querySelector(".recommendations-options button.selected");
        if (selected) {
            payload[sectionKey] = selected.dataset.value;
        }
    });

    // Cost is stored as two separate fields (lower_cost and upper_cost)
    const costSection = document.querySelector(sectionsConfig.cost.selector);
    if (costSection) {
        const lowerInput = costSection.querySelector('input[data-cost-key="lower_cost"]');
        const upperInput = costSection.querySelector('input[data-cost-key="upper_cost"]');

        if (lowerInput && lowerInput.value.trim() !== "") {
            payload.lower_cost = lowerInput.value.trim();
        }
        if (upperInput && upperInput.value.trim() !== "") {
            payload.upper_cost = upperInput.value.trim();
        }
    }

    const implicationSection = document.querySelector('[data-implication-key="implication"]');
    if (implicationSection && implicationSection.value.trim() !== "") {
        payload.implication = implicationSection.value.trim();
    }

    return payload;
}

function submitRecommendations(fieldId) {
    const payload = collectRecommendationsFromUI();

    console.log(payload);

    fetch(`http://localhost:8080/api/fields/${fieldId}/recommendations`, {
        method: "POST",
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
        highlightExistingValues(saved);
    })
    .catch(error => {
        console.error(error);
        alert(`Could not save recommendations: ${error.message}`);
    });
}

export function setUpRecommendationsPanel(fieldId) {
    renderSections();

    submitButton.addEventListener("click", () => submitRecommendations(fieldId));

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