import { saveForm, loadForm } from "../formFactory.js";

const URI = `http://localhost:8080/api/profile`;

const fields = ['coverLetterBody', 'summaryLetterBody', 'agreementBody'];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => saveWithPdf());
resetBtn.addEventListener("click", () => loadForm(URI, fields));

async function saveWithPdf() {
    await saveForm(URI, fields);

    const fileEl = document.getElementById("appendixPdf");
    if (fileEl?.files[0]) {
        const formData = new FormData();
        formData.append("appendixPdf", fileEl.files[0]);
        await fetch(`${URI}/appendix-pdf`, { method: "POST", body: formData });
    }
}

loadForm(URI, fields);

// Shown on the link out to the gallery, so its size is visible without opening it.
async function showDiagramCount() {
    const label = document.getElementById("diagramCount");
    if (!label) return;

    try {
        const res = await fetch("http://localhost:8080/api/recommendation-diagrams");
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
