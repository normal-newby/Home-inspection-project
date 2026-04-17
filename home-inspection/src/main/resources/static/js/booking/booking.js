import { collectForm, saveForm, loadForm } from "../formFactory.js";
const params = new URLSearchParams(window.location.search);
const id = params.get("id");

const URI = id ? `http://localhost:8080/api/bookings/${id}` : `http://localhost:8080/api/bookings`
const fields = ["inspectionAddress", "suite", "city", "postalCode", "province", // Address
    "clientFirstName", "clientLastName", "email", "phone", // Client
    "month", "day", "year", // Date
    "referredBy", "bookedBy", // Data
    "paidInFull", // Invoice
];

// Save & Load
const saveBtn = document.getElementById("saveBtn");
const method = id ? "PUT" : "POST";
saveBtn.addEventListener("click", () => saveWithInvoices());

async function saveWithInvoices() {
    const bookingForm = collectForm(fields);
    const invoices = [];
    invoiceList.querySelectorAll(".invoice-item").forEach(item => {
        const id = item.dataset.invoiceId.startsWith("local-") ? null : item.dataset.invoiceId; // Ignore local IDs
        const type = item.querySelector(".invoice-type").textContent;
        const fee = parseFloat(item.querySelector(".invoice-fee").textContent.replace("$", ""));
        invoices.push({ id, type, fee });
    });
    bookingForm.invoices = invoices;

    try {
        const res = await fetch(URI, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(bookingForm)
        });
        console.log(res);
        if (!res.ok) throw new Error('Failed to save form');
    } catch (err) {
        console.error('Error saving form:', err);
    }
}

async function loadWithInvoices() {
    const invoices = await loadForm(URI, fields);
    invoices.forEach(invoice => createInvoice(invoice));
    calculateTotals();
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
const HST_RATE = 0.13;
const GST_RATE = 0.05;

function calculateTotals() {
    let total = 0;
    invoiceList.querySelectorAll(".invoice-fee").forEach(span => {
        total += parseFloat(span.textContent.replace("$", ""));
    });
    const hst = total * HST_RATE;
    const gst = total * GST_RATE;
    subtotalSpan.textContent = `$${total.toFixed(2)}`;
    hstSpan.textContent = `$${hst.toFixed(2)}`;
    gstSpan.textContent = `$${gst.toFixed(2)}`;
    totalSpan.textContent = `$${(total + hst + gst).toFixed(2)}`;
}

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
}

if (id) loadWithInvoices();
loadTemplates();