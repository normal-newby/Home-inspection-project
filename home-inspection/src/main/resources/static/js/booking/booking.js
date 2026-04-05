import { saveForm, loadForm } from "../formFactory.js";
const params = new URLSearchParams(window.location.search);
const id = params.get("id");

const URI = id ? `http://localhost:8080/api/bookings/${id}` : `http://localhost:8080/api/bookings`
const fields = ["inspectionAddress", "suite", "city", "postalCode", "province", "clientFirstName", "clientLastName", "email", "phone", "month", "day", "year", "referredBy", "bookedBy"];
const saveBtn = document.getElementById("saveBtn");

const method = id ? "PUT" : "POST";

saveBtn.addEventListener("click", () => {saveForm(URI, fields, method)});

if (id) loadForm(URI, fields);