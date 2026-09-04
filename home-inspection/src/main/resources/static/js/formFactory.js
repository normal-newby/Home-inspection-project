function populateForm(fields, profile) {
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (!el) return;
        if (el.type === "checkbox") {
            el.checked = Boolean(profile[f]);
        } else {
            el.value = profile[f] ?? '';
        }
    });
}
 
export function collectForm(fields) {
    const form = {};
    fields.forEach(f => {
        const el = document.getElementById(f);
        if (el) form[f] = el.type === "checkbox" ? el.checked : el.value;
    });
    return form;
}
 
// Save profile
export async function saveForm(URI, fields, method = "POST") {
    const resultBanner = document.querySelector(".resultBanner");

    try {
        const res = await fetch(URI, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(collectForm(fields))
        });

        if (!res.ok) throw new Error('Failed to save form');

        if (resultBanner) {
            resultBanner.textContent = "Saved successfully!";
            setTimeout(() => {
                resultBanner.textContent = "";
            }, 3000); // Clear the message after 3 seconds
        }

        const body = await res.text();
        return body ? JSON.parse(body) : null;
    } catch (err) {
        console.error('Error saving form:', err);
        if (resultBanner) resultBanner.textContent = "Could not save. Please try again.";
    }
}

export async function loadForm(URI, fields){
    try {
        const res = await fetch(URI);
        if (!res.ok) throw new Error('Failed to load form');
        const form = await res.json();
        populateForm(fields, form);

        console.log(form);

        if (form.appendixPdf) {
            const label = document.querySelector(".appendixPdfLabel");
            if (label) {
                label.textContent = `Current PDF: ${form.appendixPdf}`;
            }
        }

        if (form.coverPageImage) {
            const label = document.querySelector(".coverPageImageLabel");
            if (label) {
                label.textContent = `Current Image: ${form.coverPageImage.imageUrl}`;
            }
        }

        if (form.invoices) return form.invoices; // Return invoices for further processing

    } catch (err) {
        console.error('Error loading form:', err);
    }
}