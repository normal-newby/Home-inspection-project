import { collectForm, saveForm, loadForm } from "../formFactory.js";
import { assertValidDate, syncPickerFromForm, showDateError } from "./bookingDate.js";
import { notify, confirmDialog } from "../ui/dialog.js";
const params = new URLSearchParams(window.location.search);
const id = params.get("id");

const URI = id ? `http://localhost:8080/api/bookings/${id}` : `http://localhost:8080/api/bookings`
const fields = ["inspectionAddress", "suite", "city", "postalCode", "province", // Address
    "clientFirstName", "clientLastName", "email", "phone", // Client
    "month", "day", "year", "startTime", "durationMinutes", // Date
    "referredBy", "bookedBy", // Data
    "paidInFull", "removeTax", // Invoice
];

// Save & Load
const bookingFormEl = document.getElementById("booking-form");
const method = id ? "PUT" : "POST";

bookingFormEl.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!assertValidDate()) return;

    const result = await saveWithInvoices();
    if (!result.ok) {
        showDateError(result.message);
        return;
    }
    // New inspection lands on its own booking page
    if (!id && result.booking && result.booking.id) {
        window.location.href = `booking.html?id=${result.booking.id}`;
    } else {
        window.location.href = "index.html";
    }
});

async function saveWithInvoices() {
    const bookingForm = collectForm(fields);
    const invoices = [];
    invoiceList.querySelectorAll(".invoice-item").forEach(item => {
        const invoiceId = item.dataset.invoiceId.startsWith("local-") ? null : item.dataset.invoiceId; // Ignore local IDs
        const type = item.querySelector(".invoice-type").textContent;
        const fee = parseFloat(item.querySelector(".invoice-fee").textContent.replace("$", ""));
        invoices.push({ id: invoiceId, type, fee });
    });
    bookingForm.invoices = invoices;

    try {
        const res = await fetch(URI, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(bookingForm)
        });
        if (!res.ok) {
            // The server rejects impossible dates with a readable message
            const body = await res.json().catch(() => null);
            return { ok: false, message: body?.message ?? "Could not save this booking. Please try again." };
        }
        // POST returns the created booking (with id); PUT returns an empty body.
        return { ok: true, booking: method === "POST" ? await res.json() : null };
    } catch (err) {
        console.error('Error saving form:', err);
        return { ok: false, message: "Could not reach the server. Please try again." };
    }
}

async function loadWithInvoices() {
    const invoices = await loadForm(URI, fields);
    invoices.forEach(invoice => createInvoice(invoice));
    calculateTotals();
    // The saved date only reaches the picker once the fields have been filled in.
    syncPickerFromForm();
}

// Invoice
const addInvoiceBtn = document.getElementById("add-invoice-btn");
const invoiceList = document.getElementById("invoice-list");
const templateSelector = document.getElementById("select-from-template");

// Amounts
const subtotalSpan = document.getElementById("subtotalAmount");
const hstSpan = document.getElementById("hstAmount");
const gstSpan = document.getElementById("gstAmount");
const totalSpan = document.getElementById("totalAmount");
const removeTaxBox = document.getElementById("removeTax");
const HST_RATE = 0.08;
const GST_RATE = 0.05;

function calculateTotals() {
    let total = 0;
    invoiceList.querySelectorAll(".invoice-fee").forEach(span => {
        total += parseFloat(span.textContent.replace("$", ""));
    });
    let hst = total * HST_RATE;
    let gst = total * GST_RATE;
    if (removeTaxBox.checked){
        hst = 0;
        gst = 0;
    }
    subtotalSpan.textContent = `$${total.toFixed(2)}`;
    hstSpan.textContent = `$${hst.toFixed(2)}`;
    gstSpan.textContent = `$${gst.toFixed(2)}`;
    totalSpan.textContent = `$${(total + hst + gst).toFixed(2)}`;
}

// Flipping the switch only changes the arithmetic, so just redraw the totals.
removeTaxBox.addEventListener("change", calculateTotals);

function createInvoice(invoice){
    const invoiceItem = document.createElement("div");
    invoiceItem.classList.add("invoice-item");
    invoiceItem.dataset.invoiceId = invoice.id;

    const invoiceType = document.createElement("span");
    invoiceType.textContent = invoice.type;
    invoiceType.classList.add("invoice-type");

    const invoiceFee = document.createElement("span");
    invoiceFee.textContent = `$${invoice.fee.toFixed(2)}`;
    invoiceFee.classList.add("invoice-fee");

    calculateTotals();

    const removeBtn = document.createElement("button");
    removeBtn.textContent = "✕";
    removeBtn.classList.add("remove-btn");
    removeBtn.addEventListener("click", () => removeInvoice(invoice.id));

    invoiceItem.appendChild(invoiceType);
    invoiceItem.appendChild(invoiceFee);
    invoiceItem.appendChild(removeBtn);
    invoiceList.appendChild(invoiceItem);
}

addInvoiceBtn.addEventListener("click", () => {
    const selected = templateSelector.options[templateSelector.selectedIndex];
    if (selected && selected.value) {
        const [type, fee] = selected.textContent.split(" - $");
        const invoice = {
            id: `local-${Date.now()}`, // Temporary ID for client-side management
            type: type,
            fee: parseFloat(fee)
        };
        createInvoice(invoice);
        calculateTotals();
    }
});

function removeInvoice(invoiceId){
    const invoiceItem = invoiceList.querySelector(`[data-invoice-id="${invoiceId}"]`);
    invoiceItem.remove();
}

// Templates
const templateURI = "http://localhost:8080/api/invoice-definition";
const templateFields = ["type", "fee"];

const createTemplateBtn = document.getElementById("add-template-btn");
const saveTemplateBtn = document.getElementById("save-template-btn");
const templateForm = document.getElementById("template-form");
const templateManageList = document.getElementById("template-manage-list");

createTemplateBtn.addEventListener("click", () => {
    templateForm.classList.toggle("open");
});

saveTemplateBtn.addEventListener("click", () => {
    saveForm(templateURI, templateFields).then(() => {
        loadTemplates();
        templateForm.classList.remove("open");
    })
});

async function loadTemplates() {
    const res = await fetch(templateURI);
    const templates = await res.json();

    templateSelector.innerHTML = '<option value="" disabled selected>Add from template</option>';
    templates.forEach(template => {
        const option = document.createElement("option");
        option.value = template.id;
        option.textContent = `${template.type} - $${template.fee}`;
        templateSelector.appendChild(option);
    });

    renderTemplateManageList(templates);
}

function renderTemplateManageList(templates){
    templateManageList.innerHTML = "";
    templates.forEach(template => createTemplateManageRow(template));
}

function createTemplateManageRow(template){
    const row = document.createElement("div");
    row.className = "asset-item";
    row.dataset.id = template.id;

    const typeInput = document.createElement("input");
    typeInput.type = "text";
    typeInput.value = template.type;
    typeInput.className = "template-edit-type";
    typeInput.style.flex = "1";

    const feeInput = document.createElement("input");
    feeInput.type = "number";
    feeInput.min = "0";
    feeInput.step = "0.01";
    feeInput.value = template.fee;
    feeInput.className = "template-edit-fee";
    feeInput.style.width = "6rem";

    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.textContent = "Save";
    saveBtn.className = "btn btn-secondary";
    saveBtn.addEventListener("click", () => saveTemplateEdit(template.id, typeInput, feeInput, saveBtn));

    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.textContent = "✕";
    removeBtn.className = "remove-btn";
    removeBtn.addEventListener("click", () => deleteTemplate(template.id, row));

    row.append(typeInput, feeInput, saveBtn, removeBtn);
    templateManageList.appendChild(row);
}

async function saveTemplateEdit(id, typeInput, feeInput, saveBtn){
    const type = typeInput.value.trim();
    const fee = parseFloat(feeInput.value);

    if (!type || Number.isNaN(fee)){
        notify("Enter a type and falid fee before saving", {error : true});
        return;
    }

    saveBtn.disabled = true;
    try {
        const res = await fetch(`${templateURI}/{${id}`, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({type, fee})
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        await loadTemplates();
        notify("Template updated");
    } catch (error){
        notify("Could not update template", { error: true});
    } finally {
        saveBtn.disabled = false;
    }
}

async function deleteTemplate(id, row){
    const confirmed = await confirmDialog(
        "This won't affect invoices already added to bookings. Only the future use of this invoice will no longer be available.",
        { title: "Delete this template?", confirmLabel: "Delete template", danger: true }
    );
    if (!confirmed) return;

    try {
        const res = await fetch(`${templateURI}/${id}`, { method: "DELETE" });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        row.remove();
        await loadTemplates();
    } catch (error) {
        console.error("Error deleting template:", error);
        notify("Could not delete that template.", { error: true });
    }
}

if (id) loadWithInvoices();
loadTemplates();

// Google Maps Autocomplete
function initAddressAutocomplete() {
    const input = document.getElementById("inspectionAddress");
    const autocomplete = new google.maps.places.Autocomplete(input, {
        types: ["address"],
        componentRestrictions: { country: "ca" }  // restrict to Canada
    });

    autocomplete.addListener("place_changed", () => {
        const place = autocomplete.getPlace();
        if (!place.address_components) return;

        const get = (type) =>
            place.address_components.find(c => c.types.includes(type))?.long_name || "";
        
        const getShort = (type) =>
            place.address_components.find(c => c.types.includes(type))?.short_name || "";

        document.getElementById("inspectionAddress").value =
            `${get("street_number")} ${get("route")}`.trim();
        document.getElementById("city").value =
            get("locality") || get("sublocality");
        document.getElementById("postalCode").value =
            get("postal_code");
        document.getElementById("province").value =
            getShort("administrative_area_level_1"); // "ON", "BC", etc.
    });
}

// Call after DOM loads
initAddressAutocomplete();