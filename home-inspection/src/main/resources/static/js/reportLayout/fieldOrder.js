import { confirmDialog } from "../ui/dialog.js";

const URI = "http://localhost:8080/api/fields/definition/layout";

const placeTabs = document.getElementById("placeTabs");
const orderSections = document.getElementById("orderSections");
const emptyMessage = document.getElementById("orderEmpty");
const errorBox = document.getElementById("orderError");
const saveButton = document.getElementById("saveOrderBtn");
const resetButton = document.getElementById("resetOrderBtn");
const resultBanner = document.querySelector(".resultBanner");

let loadedLayout = [];
let openPlace = null;

let draggedRow = null;

function showError(message){
    errorBox.textContent = message ?? "";
    errorBox.classList.toggle("show", Boolean(message));
}

function banner(message){
    if (!resultBanner) return;
    resultBanner.textContent = message;
    setTimeout(() => { resultBanner.textContent = ""; }, 3000);
}

function capitalise(word){
    return word ? word.charAt(0).toUpperCase() + word.slice(1) : word;
}

// --- Rendering ---

function renderPlaceTabs(){
    placeTabs.innerHTML = "";

    loadedLayout.forEach(({ place }) => {
        const tab = document.createElement("button");
        tab.type = "button";
        tab.className = "place-tab";
        tab.textContent = capitalise(place);
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-selected", String(place === openPlace));
        tab.classList.toggle("active", place === openPlace);
        tab.addEventListener("click", () => selectPlace(place));
        placeTabs.appendChild(tab);
    });
}

// Switching away throws away moves that were never saved, so the tab asks first.
async function selectPlace(place){
    if (place === openPlace) return;

    if (changedSections().length > 0){
        const confirmed = await confirmDialog(
            "The moves you have made in this section have not been saved yet.",
            { title: "Leave without saving?", confirmLabel: "Leave", danger: true }
        );
        if (!confirmed) return;
    }

    openPlace = place;
    renderPlaceTabs();
    renderSections();
}

function renderSections(){
    orderSections.innerHTML = "";

    const place = loadedLayout.find(p => p.place === openPlace);
    if (!place) return;

    place.types.forEach(({ type, definitions }) => {
        const section = document.createElement("section");
        section.className = "order-section";
        section.dataset.place = place.place;
        section.dataset.type = type;

        const heading = document.createElement("h2");
        heading.className = "order-section-title";
        heading.textContent = capitalise(type);

        const count = document.createElement("span");
        count.className = "order-section-count";
        count.textContent = `${definitions.length} ${definitions.length === 1 ? "field" : "fields"}`;
        heading.appendChild(count);

        const list = document.createElement("ol");
        list.className = "order-list";
        definitions.forEach(definition => list.appendChild(createRow(definition)));

        // Dragging is confined to one list: a definition belongs to its place and type, and
        // the save endpoint rejects an id that came from another section.
        list.addEventListener("dragover", event => {
            if (!draggedRow || draggedRow.parentElement !== list) return;
            event.preventDefault();

            const after = rowAfter(list, event.clientY);
            if (after === null) list.appendChild(draggedRow);
            else list.insertBefore(draggedRow, after);
        });
        list.addEventListener("drop", event => event.preventDefault());

        section.appendChild(heading);
        section.appendChild(list);
        orderSections.appendChild(section);
    });
}

function createRow(definition){
    const row = document.createElement("li");
    row.className = "order-row";
    row.draggable = true;
    row.dataset.id = definition.id;

    const handle = document.createElement("span");
    handle.className = "order-handle";
    handle.setAttribute("aria-hidden", "true");
    handle.textContent = "⠿";

    const name = document.createElement("span");
    name.className = "order-name";
    name.textContent = definition.fieldName;

    const controls = document.createElement("span");
    controls.className = "order-controls";
    controls.appendChild(moveButton(row, "up"));
    controls.appendChild(moveButton(row, "down"));

    row.addEventListener("dragstart", event => {
        draggedRow = row;
        event.dataTransfer.effectAllowed = "move";
        // Firefox does not start a drag unless something is set on the transfer.
        event.dataTransfer.setData("text/plain", definition.id);

        // Applied on the next frame: styling the row inside dragstart would be baked into
        // the drag image the browser has just grabbed.
        requestAnimationFrame(() => row.classList.add("dragging"));
    });

    row.addEventListener("dragend", () => {
        row.classList.remove("dragging");
        draggedRow = null;
    });

    row.appendChild(handle);
    row.appendChild(name);
    row.appendChild(controls);
    return row;
}

function moveButton(row, direction){
    const button = document.createElement("button");
    button.type = "button";
    button.className = "order-move";
    button.textContent = direction === "up" ? "↑" : "↓";
    button.title = direction === "up" ? "Move up" : "Move down";
    button.setAttribute("aria-label", `Move ${direction}`);

    button.addEventListener("click", () => {
        const sibling = direction === "up" ? row.previousElementSibling : row.nextElementSibling;
        if (!sibling) return;

        if (direction === "up") row.parentElement.insertBefore(row, sibling);
        else row.parentElement.insertBefore(sibling, row);

        // A long list scrolls the moved row out of view otherwise, and the click moved the
        // button along with it, so focus has to be put back for a second press.
        row.scrollIntoView({ block: "nearest" });
        button.focus();
    });

    return button;
}

// The first row whose midpoint is below the pointer; null means "past the last row".
function rowAfter(list, pointerY){
    const rows = [...list.querySelectorAll(".order-row:not(.dragging)")];

    for (const row of rows){
        const box = row.getBoundingClientRect();
        if (pointerY < box.top + box.height / 2) return row;
    }
    return null;
}

// --- Loading and saving ---

async function loadLayout(){
    showError(null);
    try {
        const res = await fetch(URI);
        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);

        loadedLayout = await res.json();
        emptyMessage.hidden = loadedLayout.length > 0;

        if (!loadedLayout.some(p => p.place === openPlace)){
            openPlace = loadedLayout[0]?.place ?? null;
        }

        renderPlaceTabs();
        renderSections();
    } catch (error) {
        console.error("Error loading field order:", error);
        showError("Could not load the field order.");
    }
}

// Only the place on screen can have been reordered, so the other places are left alone.
function changedSections(){
    const changed = [];

    orderSections.querySelectorAll(".order-section").forEach(section => {
        const { place, type } = section.dataset;
        const ids = [...section.querySelectorAll(".order-row")].map(row => row.dataset.id);

        const loaded = loadedLayout
            .find(p => p.place === place)?.types
            .find(t => t.type === type)?.definitions
            .map(definition => definition.id) ?? [];

        const same = ids.length === loaded.length && ids.every((id, index) => id === loaded[index]);
        if (!same) changed.push({ place, type, ids });
    });

    return changed;
}

async function saveOrder(){
    showError(null);

    const sections = changedSections();
    if (sections.length === 0){
        banner("Nothing to save - the order has not changed.");
        return;
    }

    saveButton.disabled = true;
    try {
        const failed = [];

        for (const { place, type, ids } of sections){
            const res = await fetch(`${URI}/${encodeURIComponent(place)}/${encodeURIComponent(type)}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(ids)
            });
            if (!res.ok) failed.push(`${place} / ${type}`);
        }

        // Reloaded either way: a save that only half landed must not leave the page showing
        // an order the report will not print.
        await loadLayout();

        if (failed.length > 0) showError(`Could not save ${failed.join(", ")}. What is on screen is what is saved.`);
        else banner("Field order saved.");
    } catch (error) {
        console.error("Error saving field order:", error);
        showError("Could not save the field order.");
        await loadLayout();
    } finally {
        saveButton.disabled = false;
    }
}

async function resetOrder(){
    if (changedSections().length > 0){
        const confirmed = await confirmDialog(
            "The moves you have made since the last save will be dropped.",
            { title: "Discard these changes?", confirmLabel: "Discard", danger: true }
        );
        if (!confirmed) return;
    }
    await loadLayout();
}

saveButton.addEventListener("click", saveOrder);
resetButton.addEventListener("click", resetOrder);

// Walking away with rows moved but not saved is the easy mistake to make on this page.
window.addEventListener("beforeunload", event => {
    if (changedSections().length === 0) return;
    event.preventDefault();
    event.returnValue = "";
});

loadLayout();
