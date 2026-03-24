const fields = ['name', 'company', 'phone', 'email', 'website', 'logoPath', 'address', 'city', 'province', 'postalCode', 'coverLetterBody', 'summaryLetterBody', 'agreementBody'];
 
function populateForm(profile) {
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) el.value = profile[f] ?? '';
    });
}
 
function collectForm() {
    const profile = {};
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) profile[f] = el.value;
    });
    return profile;
}
 
function showBanner() {
    const banner = document.getElementById('successBanner');
    banner.classList.add('show');
    setTimeout(() => banner.classList.remove('show'), 4000);
}
 
// Load profile on page load
async function loadProfile() {
    try {
        const res = await fetch(`http://localhost:8080/api/profile`);
        if (!res.ok) throw new Error('Failed to load profile');
        const profile = await res.json();
        populateForm(profile);
    } catch (err) {
        console.error('Error loading profile:', err);
    }
}
 
// Save profile
async function saveProfile() {
    try {
        const res = await fetch('http://localhost:8080/api/profile', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(collectForm())
        });
        if (!res.ok) throw new Error('Failed to save profile');
        showBanner();
    } catch (err) {
        console.error('Error saving profile:', err);
    }
}
 
document.getElementById('saveBtn').addEventListener('click', saveProfile);
document.getElementById('resetBtn').addEventListener('click', loadProfile); // reset reloads from server
 
loadProfile();