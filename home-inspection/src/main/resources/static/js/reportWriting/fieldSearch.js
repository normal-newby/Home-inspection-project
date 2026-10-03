// Searches every field definition and its values across all places and types.

const MIN_QUERY_LENGTH = 2;
const MAX_FIELDS = 30;
const MAX_VALUES = 5;

let byId = new Map();

async function loadSearchIndex(){
    try {
        const response = await fetch("/api/fields/definition/search-index");
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const entries = await response.json();
        byId = new Map(entries.map(entry => [String(entry.id), prepare(entry)]));
    } catch (error){
        console.error("Could not load the field search index:", error);
    }
}

function prepare(entry){
    return {
        ...entry,
        id: String(entry.id),
        lowerName: (entry.fieldName ?? "").toLowerCase(),
        lowerPlace: entry.place.toLowerCase(),
        lowerType: entry.type.toLowerCase(),
        lowerValues: entry.values.map(v => v.toLowerCase()),
        section: `${capitalize(entry.place)} › ${capitalize(entry.type)}`,
    };
}

/** Keeps a value saved as permanent findable without reloading the page. */
export function addSearchValue(definitionId, value){
    const entry = byId.get(String(definitionId));
    if (!entry || entry.values.includes(value)) return;
    entry.values.push(value);
    entry.lowerValues.push(value.toLowerCase());
}

/** Whether a definition matches the query; null when the index doesn't know it. */
export function fieldMatches(definitionId, query){
    const entry = byId.get(String(definitionId));
    if (!entry) return null;
    const tokens = tokenize(query);
    return tokens.length === 0 || scoreEntry(entry, tokens, null) !== null;
}

function tokenize(query){
    return query.toLowerCase().split(/\s+/).filter(Boolean);
}

function capitalize(text){
    return text.charAt(0).toUpperCase() + text.slice(1);
}

// 2 when the token starts a word in the text, 1 when it only appears mid-word, 0 when absent.
function matchQuality(text, token){
    let i = text.indexOf(token);
    if (i === -1) return 0;
    while (i !== -1){
        if (i === 0 || !/[a-z0-9]/.test(text[i - 1])) return 2;
        i = text.indexOf(token, i + 1);
    }
    return 1;
}

// Every token has to land somewhere. A field name hit outranks a value hit, which outranks
// only naming the section (e.g. "heating"), so "heating rust" narrows to rust under Heating.
function scoreEntry(entry, tokens, current){
    const contextScores = tokens.map(token => {
        const inName = matchQuality(entry.lowerName, token);
        if (inName) return inName === 2 ? 4 : 2;
        return entry.lowerPlace.startsWith(token) || entry.lowerType.startsWith(token) ? 1 : 0;
    });
    const fieldMatched = contextScores.every(s => s > 0);
    const fieldScore = fieldMatched ? contextScores.reduce((a, b) => a + b, 0) : 0;

    const values = [];
    entry.lowerValues.forEach((lowerValue, i) => {
        let total = 0;
        let hitValue = false;
        for (let t = 0; t < tokens.length; t++){
            const inValue = matchQuality(lowerValue, tokens[t]);
            if (inValue) hitValue = true;
            const score = Math.max(contextScores[t], inValue === 2 ? 3 : inValue * 1.5);
            if (score === 0) return;
            total += score;
        }
        if (hitValue) values.push({ value: entry.values[i], score: total });
    });

    if (!fieldMatched && values.length === 0) return null;
    values.sort((a, b) => b.score - a.score);

    let score = Math.max(fieldScore, values[0]?.score ?? 0);
    if (entry.lowerName === tokens.join(" ")) score += 3;
    const isCurrent = current && entry.place === current.place && entry.type === current.type;
    if (isCurrent) score += 1;

    return { entry, score, values, isCurrent };
}

function search(query, current){
    const tokens = tokenize(query);
    const results = [];
    for (const entry of byId.values()){
        const result = scoreEntry(entry, tokens, current);
        if (result) results.push(result);
    }
    results.sort((a, b) =>
        b.score - a.score
        || (b.isCurrent ? 1 : 0) - (a.isCurrent ? 1 : 0)
        || a.entry.fieldName.localeCompare(b.entry.fieldName));
    return { tokens, results };
}

// Builds the text with each token occurrence wrapped in <mark>, without going through innerHTML.
function appendHighlighted(parent, text, tokens){
    const lower = text.toLowerCase();
    const marked = new Array(text.length).fill(false);
    tokens.forEach(token => {
        for (let i = lower.indexOf(token); i !== -1; i = lower.indexOf(token, i + 1)){
            marked.fill(true, i, i + token.length);
        }
    });

    let start = 0;
    for (let i = 1; i <= text.length; i++){
        if (i === text.length || marked[i] !== marked[start]){
            const piece = text.slice(start, i);
            if (marked[start]){
                const mark = document.createElement("mark");
                mark.textContent = piece;
                parent.appendChild(mark);
            } else {
                parent.appendChild(document.createTextNode(piece));
            }
            start = i;
        }
    }
}

/**
 * Wires the search bar to a dropdown of matches from every section.
 * current() gives the open { place, type }; onPick({ definitionId, place, type, value? }) jumps to a result.
 */
export function setUpFieldSearch(input, resultsBox, { current, onPick }){
    loadSearchIndex();

    let options = [];
    let activeIndex = -1;

    function close(){
        resultsBox.hidden = true;
        resultsBox.innerHTML = "";
        options = [];
        activeIndex = -1;
    }

    function setActive(index){
        options[activeIndex]?.button.classList.remove("active");
        activeIndex = index;
        const option = options[activeIndex];
        if (!option) return;
        option.button.classList.add("active");
        option.button.scrollIntoView({ block: "nearest" });
    }

    function addOption(parent, className, pick, fill){
        const button = document.createElement("button");
        button.type = "button";
        button.tabIndex = -1;
        button.classList.add("search-result", className);
        fill(button);
        // Keeps focus in the search bar so the keyboard keeps working.
        button.addEventListener("mousedown", e => e.preventDefault());
        button.addEventListener("click", () => choose(pick));
        options.push({ button, pick });
        parent.appendChild(button);
    }

    function render(){
        const query = input.value.trim();
        if (query.length < MIN_QUERY_LENGTH || byId.size === 0){
            close();
            return;
        }

        const { tokens, results } = search(query, current());
        resultsBox.innerHTML = "";
        options = [];
        activeIndex = -1;

        if (results.length === 0){
            const empty = document.createElement("p");
            empty.classList.add("search-empty");
            empty.textContent = "No matching fields or values";
            resultsBox.appendChild(empty);
        }

        results.slice(0, MAX_FIELDS).forEach(({ entry, values }) => {
            const group = document.createElement("div");
            group.classList.add("search-group");
            const target = { definitionId: entry.id, place: entry.place, type: entry.type };

            addOption(group, "search-field", target, button => {
                const section = document.createElement("span");
                section.classList.add("search-section");
                section.textContent = entry.section;
                const name = document.createElement("span");
                name.classList.add("search-field-name");
                appendHighlighted(name, entry.fieldName, tokens);
                button.append(section, name);
            });

            values.slice(0, MAX_VALUES).forEach(({ value }) => {
                addOption(group, "search-value", { ...target, value },
                    button => appendHighlighted(button, value, tokens));
            });

            if (values.length > MAX_VALUES){
                const more = document.createElement("p");
                more.classList.add("search-more");
                more.textContent = `+${values.length - MAX_VALUES} more`;
                group.appendChild(more);
            }

            resultsBox.appendChild(group);
        });

        if (results.length > MAX_FIELDS){
            const more = document.createElement("p");
            more.classList.add("search-more");
            more.textContent = `${results.length - MAX_FIELDS} more fields — keep typing to narrow down`;
            resultsBox.appendChild(more);
        }

        resultsBox.hidden = false;
        resultsBox.scrollTop = 0;
    }

    function choose(pick){
        close();
        onPick(pick);
    }

    input.addEventListener("input", render);
    input.addEventListener("focus", render);

    input.addEventListener("keydown", e => {
        if (e.key === "Escape"){
            if (!input.value) return;
            input.value = "";
            input.dispatchEvent(new Event("input"));
            return;
        }
        if (resultsBox.hidden || options.length === 0) return;

        if (e.key === "ArrowDown"){
            e.preventDefault();
            setActive((activeIndex + 1) % options.length);
        } else if (e.key === "ArrowUp"){
            e.preventDefault();
            setActive(activeIndex <= 0 ? options.length - 1 : activeIndex - 1);
        } else if (e.key === "Enter"){
            e.preventDefault();
            choose(options[Math.max(activeIndex, 0)].pick);
        }
    });

    document.addEventListener("mousedown", e => {
        if (!resultsBox.hidden && e.target !== input && !resultsBox.contains(e.target)) close();
    });
}
