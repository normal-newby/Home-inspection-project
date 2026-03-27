function populateForm(fields, profile) {
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) el.value = profile[f] ?? '';
    });
}
 
function collectForm(fields) {
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
        console.log(res);
        if (!res.ok) throw new Error('Failed to save form');
    } catch (err) {
        console.error('Error saving form:', err);
    }
}

export async function loadForm(URI, fields){
    try {
        const res = await fetch(URI);
        if (!res.ok) throw new Error('Failed to load form');
        const form = await res.json();
        populateForm(fields, form);
    } catch (err) {
        console.error('Error loading form:', err);
    }
}