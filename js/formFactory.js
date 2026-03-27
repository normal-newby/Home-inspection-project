export function populateForm(fields, profile) {
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) el.value = profile[f] ?? '';
    });
}
 
export function collectForm(fields) {
    const form = {};
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) form[f] = el.value;
    });
    return form;
}
 
// Save profile
export async function saveForm(URI, fields) {
    try {
        const res = await fetch(URI, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(collectForm(fields))
        });
        if (!res.ok) throw new Error('Failed to save profile');
    } catch (err) {
        console.error('Error saving profile:', err);
    }
}