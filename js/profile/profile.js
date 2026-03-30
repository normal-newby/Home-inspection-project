import { saveForm, loadForm } from "../formFactory.js";

const URI = `http://localhost:8080/api/profile`;
const fields = ['name', 'company', 'phone', 'email', 'website', 'logoPath', 'inspectionNumber',
     'address', 'city', 'province', 'postalCode', 
     'coverLetterBody', 'summaryLetterBody', 'agreementBody'
];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => saveForm(URI, fields));
resetBtn.addEventListener("click", () => loadForm(URI));

loadForm(URI, fields);