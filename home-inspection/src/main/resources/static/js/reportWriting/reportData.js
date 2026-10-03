import { saveForm, loadForm } from "../formFactory.js";
import { notify } from "../ui/dialog.js";
import { autoSaveOnLeave } from "../ui/autoSave.js";
import { bookingId } from "./getReport.js";

const URI = `/api/reports/${bookingId}`;
const URI_IMAGES = `/api/images/${bookingId}`;
const fields = ['summary', 'emailBody', 'showInvoice'];

const backLink = document.getElementById("backLink");
if (backLink) backLink.href = writingScreen();

function writingScreen(){
    if (!bookingId) return "index.html";
    const fallback = `report_writing.html?id=${bookingId}&place=roofing&type=description`;
    const value = new URLSearchParams(window.location.search).get("returnTo");
    if (!value) return fallback;
    try {
        const url = new URL(value, window.location.href);
        if (url.origin !== window.location.origin) return fallback;
        return url.pathname + url.search;
    } catch (error) {
        return fallback;
    }
}

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");
const generateSummaryBtn = document.getElementById("generateSummaryBtn");

const autoSave = autoSaveOnLeave(document.getElementById("reportForm"), saveWithOthers);

saveBtn.addEventListener("click", async () => {
    if (await saveWithOthers()) autoSave.markClean();
});
resetBtn.addEventListener("click", async () => {
    await loadForm(URI, fields);
    autoSave.markClean();
});
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
        autoSave.markDirty();
    } catch (err) {
        console.error("Error generating summary:", err);
        notify("Could not generate summary: " + err.message, { error: true });
    } finally {
        generateSummaryBtn.disabled = false;
        generateSummaryBtn.textContent = label;
    }
}

async function saveWithOthers() {
    if (!await saveForm(URI, fields)) return false;

    const fileEl = document.getElementById("appendixPdf");
    if (fileEl?.files[0] && !await upload(`${URI}/appendix-pdf`, fileEl.files[0])) return false;

    const coverPageEl = document.getElementById("coverPageImage");
    if (coverPageEl?.files[0] && !await upload(`${URI_IMAGES}/cover-page-image`, coverPageEl.files[0])) return false;
    return true;
}

async function upload(url, file) {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(url, { method: "POST", body: formData }).catch(() => null);
    return Boolean(res?.ok);
}

loadForm(URI, fields);