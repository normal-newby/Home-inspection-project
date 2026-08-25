// Keeps the date picker and the month / day / year fields showing the same date

const MONTHS = ["January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"];

const datePicker = document.getElementById("inspectionDate");
const monthSelect = document.getElementById("month");
const dayInput = document.getElementById("day");
const yearInput = document.getElementById("year");
const errorLine = document.getElementById("dateError");

let syncing = false;

function daysInMonth(monthIndex, year) {
    if (monthIndex < 0) return 0;
    return new Date(year || 2024, monthIndex + 1, 0).getDate();
}

function showError(message) {
    errorLine.textContent = message ?? "";
    errorLine.classList.toggle("show", Boolean(message));
}

function readParts() {
    return {
        monthIndex: MONTHS.indexOf(monthSelect.value),
        day: dayInput.value === "" ? null : Number(dayInput.value),
        year: yearInput.value === "" ? null : Number(yearInput.value)
    };
}

// Checks the month / day / year fields.
export function validateDate() {
    const { monthIndex, day, year } = readParts();
    const filled = [monthIndex >= 0, day !== null, year !== null].filter(Boolean).length;

    if (filled === 0) return { ok: true, empty: true };
    if (filled < 3) {
        return { ok: false, message: "Enter a complete inspection date — month, day and year are all required." };
    }
    if (year < 1900 || year > 2100) {
        return { ok: false, message: "Enter a year between 1900 and 2100." };
    }
    const max = daysInMonth(monthIndex, year);
    if (day < 1 || day > max) {
        return { ok: false, message: `${MONTHS[monthIndex]} ${year} only has ${max} days.` };
    }
    return { ok: true, date: new Date(year, monthIndex, day) };
}

function pickerFromParts() {
    const result = validateDate();
    if (!result.ok || result.empty) {
        datePicker.value = "";
        return;
    }
    const { monthIndex, day, year } = readParts();
    datePicker.value = `${String(year).padStart(4, "0")}-${String(monthIndex + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function partsFromPicker() {
    if (!datePicker.value) return;
    const [year, month, day] = datePicker.value.split("-").map(Number);
    monthSelect.value = MONTHS[month - 1];
    dayInput.value = day;
    yearInput.value = year;
}

function updateDayCeiling() {
    const { monthIndex, year } = readParts();
    dayInput.max = monthIndex >= 0 ? daysInMonth(monthIndex, year) : 31;
}

function refreshFromParts() {
    if (syncing) return;
    syncing = true;
    updateDayCeiling();
    pickerFromParts();
    const result = validateDate();
    showError(result.ok ? null : result.message);
    syncing = false;
}

datePicker.addEventListener("change", () => {
    if (syncing) return;
    syncing = true;
    partsFromPicker();
    updateDayCeiling();
    showError(null);
    syncing = false;
});

[monthSelect, dayInput, yearInput].forEach(el => {
    el.addEventListener("change", refreshFromParts);
    el.addEventListener("input", refreshFromParts);
});

export function syncPickerFromForm() {
    refreshFromParts();
}

export function assertValidDate() {
    const result = validateDate();
    showError(result.ok ? null : result.message);
    if (!result.ok) {
        datePicker.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    return result.ok;
}

export function showDateError(message) {
    showError(message);
}
