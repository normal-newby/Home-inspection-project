import { saveForm, loadForm } from "../formFactory.js";

const URI = `http://localhost:8080/api/profile`;
const fields = ['name', 'company', 'phone', 'email', 'website', 'logoPath', 'inspectionNumber',
     'address', 'city', 'province', 'postalCode', 
     'coverLetterBody', 'summaryLetterBody', 'agreementBody'
];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => saveWithPdf());
resetBtn.addEventListener("click", () => loadForm(URI));

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