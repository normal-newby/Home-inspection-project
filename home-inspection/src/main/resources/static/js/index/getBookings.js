import { confirmDialog, notify } from "../ui/dialog.js";

const bookingsContainer = document.querySelector(".bookings-container");
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
    // Main element (contains everything)
    const bookingElement = document.createElement("div");
    bookingElement.classList.add("booking");
    bookingElement.id = booking.id;

    // Card for flex box
    const bookingCard = document.createElement("div");
    bookingCard.className = "booking-card";

    // Left side for meta info
    const bookingLeft = document.createElement("div");
    bookingLeft.className = "booking-left";

    const bookingName = document.createElement("div");
    bookingName.className = "booking-name";
    bookingName.textContent = `${booking.clientFirstName} ${booking.clientLastName}`;
    bookingLeft.appendChild(bookingName);

    const bookingMeta = document.createElement("div");
    bookingMeta.className = "booking-meta";

    const bookingAddress = document.createElement("span");
    bookingAddress.className = "booking-address";
    bookingAddress.textContent = booking.inspectionAddress;
    bookingMeta.appendChild(bookingAddress);

    const bookingDot = document.createElement("span");
    bookingDot.className = "booking-dot";
    bookingDot.textContent = "·";
    bookingMeta.appendChild(bookingDot);

    const bookingPostal = document.createElement("span");
    bookingPostal.className = "booking-postal";
    bookingPostal.textContent = booking.postalCode;
    bookingMeta.appendChild(bookingPostal);

    bookingLeft.appendChild(bookingMeta);

    const bookingDate = document.createElement("div");
    bookingDate.className = "booking-date";
    bookingDate.textContent = formatWhen(booking);
    bookingLeft.appendChild(bookingDate);

    bookingLeft.appendChild(createStatusControl(booking, bookingElement));

    bookingCard.appendChild(bookingLeft);

    // Right side for actions
    const bookingRight = document.createElement("div");
    bookingRight.className = "booking-right";

    const viewDetailsLink = document.createElement("a");
    viewDetailsLink.href = `booking.html?id=${booking.id}`;
    viewDetailsLink.className = "booking-link secondary view-details";
    viewDetailsLink.textContent = "View Details →";
    viewDetailsLink.style.gridArea = "view";
    bookingRight.appendChild(viewDetailsLink);

    const writeReportLink = document.createElement("a");
    writeReportLink.href = `report_writing.html?id=${booking.id}&place=roofing&type=description`;
    writeReportLink.className = "booking-link primary";
    writeReportLink.textContent = "Write Report →";
    writeReportLink.style.gridArea = "write";
    bookingRight.appendChild(writeReportLink);

    const removeButton = document.createElement("button");
    removeButton.className = "booking-link remove-btn";
    removeButton.textContent = "Remove Booking";
    removeButton.addEventListener("click", () => deleteBooking(booking));
    removeButton.style.gridArea = "remove";
    bookingRight.appendChild(removeButton);

    bookingCard.appendChild(bookingRight);

    bookingElement.appendChild(bookingCard);
    bookingElement.dataset.status = statusOf(booking);

    bookingsContainer.appendChild(bookingElement);
}

function createStatusControl(booking, bookingElement){
    const row = document.createElement("div");
    row.className = "booking-status-row";

    const label = document.createElement("label");
    label.className = "booking-status-label";
    label.htmlFor = `status-${booking.id}`;
    label.textContent = "Status";

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
            applyFilter(); // the card may no longer belong in the current view
        } else {
            // Put the control back where it was rather than showing a status that was not saved.
            select.value = current;
            notify("Could not update that booking's status.", { error: true });
        }

        select.disabled = false;
    });

    row.append(label, select);
    return row;
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