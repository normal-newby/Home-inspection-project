import { saveForm, loadForm } from "../formFactory.js";
import { bookingId } from "./getReport.js";

const URI = `http://localhost:8080/api/reports/${bookingId}`;
const fields = ['summary'];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => saveWithPdf());
resetBtn.addEventListener("click", () => loadForm(URI));

async function saveWithPdf() {
     await saveForm(URI, fields);

     const fileEl = document.getElementById("appendixPdf");
     if (fileEl?.files[0]) {
        const formData = new FormData();
        formData.append("file", fileEl.files[0]);
        await fetch(`${URI}/appendix-pdf`, { method: "POST", body: formData });
    }
}

loadForm(URI, fields);