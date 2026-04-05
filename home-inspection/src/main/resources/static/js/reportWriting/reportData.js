import { saveForm, loadForm } from "../formFactory.js";
import { bookingId } from "./getReport.js";

const URI = `http://localhost:8080/api/reports/${bookingId}`;
const fields = ['summary'];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => saveForm(URI, fields));
resetBtn.addEventListener("click", () => loadForm(URI));

loadForm(URI, fields);