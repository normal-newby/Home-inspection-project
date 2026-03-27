import { saveForm, populateForm } from "../formFactory.js";

const fields = ["inspectionAddress", "suite", "city", "postalCode", "province", "clientFirstName", "clientLastName", "email", "phone", "month", "day", "year", "referredBy", "bookedBy"];
const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

saveBtn.addEventListener("click", () => {console.log("hi"); saveForm(`http://localhost:8080/api/bookings`, fields)});
//resetBtn.addEventListener("click", () => loadProfile);

console.log("Hi");