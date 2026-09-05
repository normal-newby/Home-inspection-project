import { confirmDialog, notify } from "../ui/dialog.js";

const DIAGRAMS = "http://localhost:8080/api/recommendation-diagrams";

const params = new URLSearchParams(window.location.search);

// A field means the recommendations panel sent us here to attach; no field means the report
// layout page sent us here to manage the library.
const fieldId = params.get("fieldId");
const picking = Boolean(fieldId);

// Only a path within this app is honoured; anything else would be an open redirect.
const returnTo = safeReturn(params.get("returnTo"));

const heading = document.getElementById("galleryHeading");
const subtitle = document.getElementById("gallerySubtitle");
const backLink = document.getElementById("backLink");
const grid = document.getElementById("diagramGrid");
const emptyMessage = document.getElementById("galleryEmpty");
const noMatches = document.getElementById("noMatches");
const errorBox = document.getElementById("diagramError");
const countLabel = document.getElementById("diagramCount");
const search = document.getElementById("diagramSearch");
const pickHint = document.getElementById("pickHint");
const pickActions = document.getElementById("pickActions");
const saveButton = document.getElementById("saveDiagramsBtn");
const cancelButton = document.getElementById("cancelDiagramsBtn");
const resultBanner = document.querySelector(".resultBanner");

const titleInput = document.getElementById("diagramTitle");
const fileInput = document.getElementById("diagramFile");
const uploadButton = document.getElementById("uploadDiagramBtn");
const uploadPanel = document.getElementById("uploadPanel");

// Arrays rather than Sets: the order is what the report prints in.
let library = [];
let savedIds = [];
let selectedIds = [];

function safeReturn(value){
    if (!value) return "report_layout.html";
    try {
        const url = new URL(value, window.location.href);
        if (url.origin !== window.location.origin) return "report_layout.html";
        return url.pathname + url.search;
    } catch (error) {
        return "report_layout.html";
    }
}

function showError(message){
    errorBox.textContent = message ?? "";
    errorBox.classList.toggle("show", Boolean(message));
}

function banner(message){
    if (!resultBanner) return;
    resultBanner.textContent = message;
    setTimeout(() => { resultBanner.textContent = ""; }, 3000);
}

function hasUnsavedChanges(){
    return picking && (selectedIds.length !== savedIds.length
        || selectedIds.some((id, index) => id !== savedIds[index]));
}

// --- Rendering ---

function visibleDiagrams(){
    const term = search.value.trim().toLowerCase();
    if (!term) return library;
    return library.filter(diagram => (diagram.title ?? "").toLowerCase().includes(term));
}

function renderGrid(){
    const shown = visibleDiagrams();

    grid.innerHTML = "";
    shown.forEach(diagram => grid.appendChild(createTile(diagram)));

    emptyMessage.hidden = library.length > 0;
    noMatches.hidden = library.length === 0 || shown.length > 0;

    countLabel.textContent = shown.length === library.length
        ? `${library.length} ${library.length === 1 ? "diagram" : "diagrams"}`
        : `${shown.length} of ${library.length}`;

    paintSelection();
}

function createTile(diagram){
    const tile = document.createElement(picking ? "button" : "div");
    tile.className = "diagram-tile";
    tile.dataset.id = diagram.id;

    if (picking) {
        tile.type = "button";
        tile.setAttribute("aria-pressed", "false");
        tile.addEventListener("click", () => toggle(diagram.id));
    }

    const thumb = document.createElement("img");
    thumb.className = "diagram-tile-image";
    thumb.src = `${DIAGRAMS}/${diagram.id}/thumb`;
    thumb.alt = diagram.title;
    thumb.loading = "lazy";

    const caption = document.createElement("span");
    caption.className = "diagram-tile-title";
    caption.textContent = diagram.title;

    tile.appendChild(thumb);
    tile.appendChild(caption);

    if (picking) {
        // The attach order, which is the order the report prints them in.
        const badge = document.createElement("span");
        badge.className = "diagram-tile-badge";
        badge.setAttribute("aria-hidden", "true");
        tile.appendChild(badge);
    } else {
        // Deleting is kept out of the attach flow; it is destructive and out of place there.
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "diagram-tile-delete";
        remove.textContent = "✕";
        remove.title = "Delete diagram";
        remove.setAttribute("aria-label", `Delete ${diagram.title}`);
        remove.addEventListener("click", () => deleteDiagram(diagram));
        tile.appendChild(remove);
    }

    return tile;
}

function toggle(id){
    const at = selectedIds.indexOf(id);
    if (at === -1) selectedIds.push(id);
    else selectedIds.splice(at, 1);
    paintSelection();
}

function paintSelection(){
    if (!picking) return;

    grid.querySelectorAll(".diagram-tile").forEach(tile => {
        const position = selectedIds.indexOf(tile.dataset.id);
        const selected = position !== -1;

        tile.classList.toggle("selected", selected);
        tile.setAttribute("aria-pressed", String(selected));
        tile.querySelector(".diagram-tile-badge").textContent = selected ? String(position + 1) : "";
    });

    // Counts everything chosen, not just what the search is showing.
    saveButton.textContent = selectedIds.length > 0
        ? `Save ${selectedIds.length} Diagram${selectedIds.length === 1 ? "" : "s"}`
        : "Save Diagrams";
}

// --- Loading ---

async function loadLibrary(){
    try {
        const res = await fetch(DIAGRAMS);
        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
        library = await res.json();
        renderGrid();
    } catch (error) {
        console.error("Error loading diagrams:", error);
        showError("Could not load the diagram library.");
    }
}

async function loadAttachment(){
    try {
        const res = await fetch(`${DIAGRAMS}/field/${fieldId}`);
        const attached = res.ok ? await res.json().catch(() => null) : null;

        if (!attached) {
            showError("That item has no recommendation value to attach diagrams to.");
            saveButton.disabled = true;
            return;
        }

        savedIds = attached.diagramIds ?? [];
        selectedIds = [...savedIds];

        subtitle.textContent =
            `Attaching to ${[attached.fieldName, attached.valueLabel].filter(Boolean).join(" — ")}`;
        paintSelection();

    } catch (error) {
        console.error("Error loading attached diagrams:", error);
        showError("Could not read what is already attached.");
    }
}

async function start(){
    backLink.href = returnTo;
    backLink.textContent = picking ? "← Back to the report" : "← Report Layout";

    heading.textContent = picking ? "Attach Diagrams" : "Recommendation Diagrams";
    pickHint.hidden = !picking;
    pickActions.hidden = !picking;

    await loadLibrary();
    if (picking) await loadAttachment();

    if (library.length === 0) uploadPanel.open = true;
}

// --- Uploading and deleting ---

async function uploadDiagram(){
    const title = titleInput.value.trim();
    const file = fileInput.files[0];

    if (!title || !file) {
        notify("Give the diagram a title and pick a file before uploading.", { error: true });
        return;
    }

    const formData = new FormData();
    formData.append("title", title);
    formData.append("file", file);

    uploadButton.disabled = true;
    try {
        const res = await fetch(DIAGRAMS, { method: "POST", body: formData });
        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);

        titleInput.value = "";
        fileInput.value = "";
        search.value = "";
        await loadLibrary();
        banner("Diagram uploaded.");
    } catch (error) {
        console.error("Error uploading recommendation diagram:", error);
        notify("Could not upload that diagram. Please try again.", { error: true });
    } finally {
        uploadButton.disabled = false;
    }
}

async function deleteDiagram(diagram){
    const confirmed = await confirmDialog(
        "It will be taken off every recommendation it is attached to, and will stop appearing on reports.",
        { title: `Delete "${diagram.title}"?`, confirmLabel: "Delete diagram", danger: true }
    );
    if (!confirmed) return;

    try {
        const res = await fetch(`${DIAGRAMS}/${diagram.id}`, { method: "DELETE" });
        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
        await loadLibrary();
    } catch (error) {
        console.error("Error deleting recommendation diagram:", error);
        notify("Could not delete that diagram.", { error: true });
    }
}

// --- Saving an attachment ---

async function save(){
    showError(null);
    saveButton.disabled = true;

    try {
        const res = await fetch(`${DIAGRAMS}/field/${fieldId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(selectedIds)
        });
        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);

        savedIds = [...selectedIds];
        window.location.href = returnTo;

    } catch (error) {
        console.error("Error saving diagrams:", error);
        showError("Could not save those diagrams.");
        saveButton.disabled = false;
    }
}

search.addEventListener("input", renderGrid);
uploadButton.addEventListener("click", uploadDiagram);
saveButton.addEventListener("click", save);

cancelButton.addEventListener("click", async () => {
    if (hasUnsavedChanges()) {
        const confirmed = await confirmDialog(
            "The diagrams you picked have not been attached yet.",
            { title: "Leave without saving?", confirmLabel: "Leave", danger: true }
        );
        if (!confirmed) return;
    }
    window.location.href = returnTo;
});

start();
