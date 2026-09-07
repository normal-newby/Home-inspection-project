import { confirmDialog, notify } from "../ui/dialog.js";

const bookingsContainer = document.querySelector(".bookings-container");
const bookingsTable = document.querySelector(".bookings-table-wrap");
const emptyMessage = document.querySelector(".bookings-empty");
const filterButtons = [...document.querySelectorAll(".booking-filter")];

const STATUS_LABELS = {
    SCHEDULED: "Scheduled",
    IN_PROGRESS: "In Progress",
    COMPLETED: "Completed"
};

// Rows written before the status column existed come back null, which means Scheduled.
function statusOf(booking){
    return booking.status ?? "SCHEDULED";
}

const FILTER_KEY = "bookings-status-filter";

function readFilter(){
    try {
        const stored = localStorage.getItem(FILTER_KEY);
        return stored in STATUS_LABELS || stored === "ALL" ? stored : "ALL";
    } catch (error) {
        return "ALL";
    }
}

function writeFilter(value){
    try {
        localStorage.setItem(FILTER_KEY, value);
    } catch (error) {
        // Storage off; the filter just resets next visit.
    }
}

let activeFilter = readFilter();
let loadedBookings = [];

function formatWhen(booking) {
    if (!booking.month || !booking.day || !booking.year) return "Date not set";
    const date = `${booking.day} ${booking.month}, ${booking.year}`;
    if (!booking.startTime) return date;

    const [hours, minutes] = booking.startTime.split(":").map(Number);
    const suffix = hours < 12 ? "AM" : "PM";
    const hour12 = hours % 12 === 0 ? 12 : hours % 12;
    return `${date} at ${hour12}:${String(minutes).padStart(2, "0")} ${suffix}`;
}

function createBooking(booking) {
    const row = document.createElement("tr");
    row.className = "booking";
    row.id = booking.id;
    row.dataset.status = statusOf(booking);

    const number = document.createElement("td");
    number.className = "cell-number";
    number.textContent = booking.inspectionNumber != null ? `#${booking.inspectionNumber}` : "—";
    row.appendChild(number);

    const address = document.createElement("td");
    const street = document.createElement("div");
    street.className = "cell-strong";
    street.textContent = booking.inspectionAddress;
    const area = document.createElement("div");
    area.className = "cell-sub";
    area.textContent = [booking.city, booking.postalCode].filter(Boolean).join(", ");
    address.append(street, area);
    row.appendChild(address);

    const client = document.createElement("td");
    client.textContent = `${booking.clientFirstName} ${booking.clientLastName}`;
    row.appendChild(client);

    const when = document.createElement("td");
    when.className = "cell-when";
    when.textContent = formatWhen(booking);
    row.appendChild(when);

    const status = document.createElement("td");
    status.appendChild(createStatusControl(booking, row));
    row.appendChild(status);

    const details = document.createElement("td");
    details.className = "cell-action";
    const viewDetailsLink = document.createElement("a");
    viewDetailsLink.href = `booking.html?id=${booking.id}`;
    viewDetailsLink.className = "booking-link secondary";
    viewDetailsLink.textContent = "View Details";
    details.appendChild(viewDetailsLink);
    row.appendChild(details);

    const write = document.createElement("td");
    write.className = "cell-action";
    const writeReportLink = document.createElement("a");
    writeReportLink.href = `report_writing.html?id=${booking.id}&place=roofing&type=description`;
    writeReportLink.className = "booking-link primary";
    writeReportLink.textContent = "Write Report";
    write.appendChild(writeReportLink);
    row.appendChild(write);

    const remove = document.createElement("td");
    remove.className = "cell-remove";
    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "remove-btn";
    removeButton.title = "Remove booking";
    removeButton.setAttribute("aria-label", `Remove booking for ${booking.clientFirstName} ${booking.clientLastName}`);
    removeButton.innerHTML = '<svg viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1l10 10M11 1L1 11"/></svg>';
    removeButton.addEventListener("click", () => deleteBooking(booking));
    remove.appendChild(removeButton);
    row.appendChild(remove);

    bookingsContainer.appendChild(row);
}

function createStatusControl(booking, bookingElement){
    const select = document.createElement("select");
    select.id = `status-${booking.id}`;
    select.className = "booking-status";

    Object.entries(STATUS_LABELS).forEach(([value, text]) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = text;
        select.appendChild(option);
    });

    let current = statusOf(booking);
    select.value = current;
    applyStatusClass(select, current);

    select.addEventListener("change", async () => {
        const chosen = select.value;
        select.disabled = true;

        if (await saveStatus(booking.id, chosen)){
            current = chosen;
            booking.status = chosen;
            bookingElement.dataset.status = chosen;
            applyStatusClass(select, chosen);
            renderCounts();
            applyFilter(); // the row may no longer belong in the current view
        } else {
            // Put the control back where it was rather than showing a status that was not saved.
            select.value = current;
            notify("Could not update that booking's status.", { error: true });
        }

        select.disabled = false;
    });

    select.setAttribute("aria-label", "Booking status");
    return select;
}

function applyStatusClass(select, status){
    select.classList.remove("status-scheduled", "status-in-progress", "status-completed");
    select.classList.add(`status-${status.toLowerCase().replace("_", "-")}`);
}

async function saveStatus(id, status){
    try {
        const response = await fetch(`http://localhost:8080/api/bookings/${id}/status`, {
            method: "PUT",
            headers: { "Content-Type": "text/plain" },
            body: status
        });
        return response.ok;
    } catch (error) {
        console.error("Error saving booking status:", error);
        return false;
    }
}

// ── Filtering ──

function renderCounts(){
    filterButtons.forEach(button => {
        const wanted = button.dataset.status;
        const count = wanted === "ALL"
            ? loadedBookings.length
            : loadedBookings.filter(booking => statusOf(booking) === wanted).length;
        button.querySelector(".booking-filter-count").textContent = count;
    });
}

function applyFilter(){
    let shown = 0;
    bookingsContainer.querySelectorAll(".booking").forEach(element => {
        const match = activeFilter === "ALL" || element.dataset.status === activeFilter;
        element.hidden = !match;
        if (match) shown++;
    });

    filterButtons.forEach(button => button.classList.toggle("active", button.dataset.status === activeFilter));
    if (bookingsTable) bookingsTable.hidden = shown === 0;
    if (emptyMessage) emptyMessage.hidden = shown > 0 || loadedBookings.length === 0;
}

filterButtons.forEach(button => {
    button.addEventListener("click", () => {
        activeFilter = button.dataset.status;
        writeFilter(activeFilter);
        applyFilter();
    });
});

async function loadBookings(){
    try {
        const response = await fetch("http://localhost:8080/api/get/bookings");
        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        // The server returns them in inspection-date order: upcoming first, then past.
        loadedBookings = await response.json();
        bookingsContainer.innerHTML = "";
        loadedBookings.forEach(createBooking);

        renderCounts();
        applyFilter();
    } catch (error) {
        console.error("Error loading bookings:", error);
        notify("Could not load bookings.", { error: true });
    }
}

async function deleteBooking(booking) {
    const name = `${booking.clientFirstName ?? ""} ${booking.clientLastName ?? ""}`.trim();
    const confirmed = await confirmDialog(
        `${name || "This booking"} and its whole report will be removed. This cannot be undone.`,
        { title: "Delete this booking?", confirmLabel: "Delete booking", danger: true }
    );
    if (!confirmed) return;

    try {
        const response = await fetch(`http://localhost:8080/api/bookings/${booking.id}`, { method: "DELETE" });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        await loadBookings();
    } catch (error) {
        console.error("Error deleting booking:", error);
        notify("Could not delete that booking.", { error: true });
    }
}

window.onload = () => loadBookings();