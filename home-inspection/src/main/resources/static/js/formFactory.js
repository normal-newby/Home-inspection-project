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
        if (el) form[f] = el.type === "checkbox" ? el.checked : el.value;
    });
    return form;
}
 
// Save profile
export async function saveForm(URI, fields, method = "POST") {
    try {
        const res = await fetch(URI, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(collectForm(fields))
        });
        console.log(res);
        if (!res.ok) throw new Error('Failed to save form');
        return await res.json();
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

        if (form.appendixPdf) {
            const label = document.getElementById("appendixPdfLabel");
            if (label) {
                label.textContent = `Current PDF: ${form.appendixPdf}`;
            }
        }

    } catch (err) {
        console.error('Error loading form:', err);
    }
}