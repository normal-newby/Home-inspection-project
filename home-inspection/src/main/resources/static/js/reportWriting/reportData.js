import { saveForm, loadForm } from "../formFactory.js";
import { notify } from "../ui/dialog.js";
import { bookingId } from "./getReport.js";

const URI = `http://localhost:8080/api/reports/${bookingId}`;
const URI_IMAGES = `http://localhost:8080/api/images/${bookingId}`;
const fields = ['summary'];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");
const generateSummaryBtn = document.getElementById("generateSummaryBtn");

saveBtn.addEventListener("click", () => saveWithOthers());
resetBtn.addEventListener("click", () => loadForm(URI));
generateSummaryBtn?.addEventListener("click", () => generateSummary());

async function generateSummary() {
    const label = generateSummaryBtn.textContent;
    generateSummaryBtn.disabled = true;
    generateSummaryBtn.textContent = "Generating…";
    try {
        const res = await fetch(`${URI}/generate-summary`, { method: "POST" });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || `Request failed (${res.status})`);
        }
        const data = await res.json();
        document.getElementById("summary").value = data.summary ?? "";
    } catch (err) {
        console.error("Error generating summary:", err);
        notify("Could not generate summary: " + err.message, { error: true });
    } finally {
        generateSummaryBtn.disabled = false;
        generateSummaryBtn.textContent = label;
    }
}

async function saveWithOthers() {
     await saveForm(URI, fields);

     const fileEl = document.getElementById("appendixPdf");
     if (fileEl?.files[0]) {
        const formData = new FormData();
        formData.append("file", fileEl.files[0]);
        await fetch(`${URI}/appendix-pdf`, { method: "POST", body: formData });
    }

    const coverPageEl = document.getElementById("coverPageImage");
    if (coverPageEl?.files[0]) {
        const formData = new FormData();
        formData.append("file", coverPageEl.files[0]);
        await fetch(`${URI_IMAGES}/cover-page-image`, { method: "POST", body: formData });
    }
}

loadForm(URI, fields);