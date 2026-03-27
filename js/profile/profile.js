import { saveForm, populateForm } from "../formFactory.js";

const fields = ['name', 'company', 'phone', 'email', 'website', 'logoPath', 'address', 'city', 'province', 'postalCode', 'coverLetterBody', 'summaryLetterBody', 'agreementBody'];
const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

// Load profile on page load
async function loadProfile() {
    try {
        const res = await fetch(`http://localhost:8080/api/profile`);
        if (!res.ok) throw new Error('Failed to load profile');
        const profile = await res.json();
        populateForm(fields, profile);
    } catch (err) {
        console.error('Error loading profile:', err);
    }
}

saveBtn.addEventListener("click", () => saveForm(`http://localhost:8080/api/profile`, fields));
resetBtn.addEventListener("click", () => loadProfile);

loadProfile();