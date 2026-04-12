import { saveForm, loadForm } from "../formFactory.js";
const params = new URLSearchParams(window.location.search);
const id = params.get("id");

const URI = id ? `http://localhost:8080/api/bookings/${id}` : `http://localhost:8080/api/bookings`
const fields = ["inspectionAddress", "suite", "city", "postalCode", "province", // Address
    "clientFirstName", "clientLastName", "email", "phone", // Client
    "month", "day", "year", // Date
    "referredBy", "bookedBy", // Data
    "invoices", "paidInFull", // Invoice
];

// Save
const saveBtn = document.getElementById("saveBtn");
const method = id ? "PUT" : "POST";
saveBtn.addEventListener("click", () => {saveForm(URI, fields, method)});
if (id) loadForm(URI, fields);

// Invoice
const addInvoiceBtn = document.getElementById("add-invoice-btn");
const invoiceList = document.getElementById("invoice-list");
const templateSelector = document.getElementById("select-from-template");

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
    const selectedTemplateId = templateSelector.value;
    fetch(`http://localhost:8080/api/bookings/${id}/invoice/${selectedTemplateId}`, {
        method: "POST",
        headers: { 'Content-Type': 'application/json' },
    })
    .then(res => {
        if (!res) throw new Error("Failed to add invoice");
        return res.json();
    })
    .then(invoice => {
        createInvoice(invoice);
    })
    .catch(err => console.error("Failed to add invoice:", err));
});

function removeInvoice(invoiceId){
    fetch(`http://localhost:8080/api/bookings/${id}/invoice/${invoiceId}`, {
        method: "DELETE",
        headers: { 'Content-Type': 'application/json' },
    })
    .then(res => {
        if (!res) throw new Error("Failed to remove invoice");
        const invoiceItem = invoiceList.querySelector(`[data-invoice-id="${invoiceId}"]`);
        invoiceItem.remove();
    })
    .catch(err => console.error("Failed to remove invoice:", err));
}

function fetchInvoices() {
    fetch(`http://localhost:8080/api/bookings/${id}/invoices`, {
        method: "GET",
        headers: { 'Content-Type': 'application/json' },
    })
    .then(res => {
        if (!res) throw new Error("Failed to fetch invoices");
        return res.json();
    })
    .then(invoices => {
        invoices.forEach(invoice => {
            createInvoice(invoice);
        });
    })
    .catch(err => console.error("Failed to fetch invoices:", err));
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

loadTemplates();
if (id) fetchInvoices();