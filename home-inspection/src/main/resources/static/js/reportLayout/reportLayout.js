import { saveForm, loadForm } from "../formFactory.js";
import { autoSaveOnLeave } from "../ui/autoSave.js";

const URI = `/api/profile`;

const fields = ['coverLetterBody', 'summaryLetterBody', 'agreementBody', 'emailTemplate'];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

// Cover page images upload through their own button.
const autoSave = autoSaveOnLeave(document.getElementById("reportLayoutForm"), saveWithPdf,
    { ignore: '[data-card="cover-images"]' });

saveBtn.addEventListener("click", async () => {
    if (await saveWithPdf()) autoSave.markClean();
});
resetBtn.addEventListener("click", async () => {
    await loadForm(URI, fields);
    autoSave.markClean();
});

async function saveWithPdf() {
    if (!await saveForm(URI, fields)) return false;

    const fileEl = document.getElementById("appendixPdf");
    if (fileEl?.files[0]) {
        const formData = new FormData();
        formData.append("appendixPdf", fileEl.files[0]);
        const res = await fetch(`${URI}/appendix-pdf`, { method: "POST", body: formData }).catch(() => null);
        if (!res?.ok) return false;
    }
    return true;
}

loadForm(URI, fields);

// Shown on the link out to the gallery, so its size is visible without opening it.
async function showDiagramCount() {
    const label = document.getElementById("diagramCount");
    if (!label) return;

    try {
        const res = await fetch("/api/recommendation-diagrams");
        if (!res.ok) return;

        const diagrams = await res.json();
        label.textContent = diagrams.length === 0
            ? "None yet."
            : `${diagrams.length} in the library.`;
    } catch (error) {
        console.error("Could not count recommendation diagrams:", error);
    }
}

showDiagramCount();
