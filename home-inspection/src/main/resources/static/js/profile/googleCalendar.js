import { confirmDialog, notify } from "../ui/dialog.js";
const BASE = "/api/google/calendar";

const dot = document.getElementById("calendarDot");
const statusText = document.getElementById("calendarStatusText");
const accountText = document.getElementById("calendarAccount");
const connectedSection = document.getElementById("calendarConnected");
const setupHint = document.getElementById("calendarSetupHint");
const calendarSelect = document.getElementById("googleCalendarId");
const enabledCheckbox = document.getElementById("googleCalendarEnabled");
const connectBtn = document.getElementById("calendarConnectBtn");
const disconnectBtn = document.getElementById("calendarDisconnectBtn");

// Every status load re-reports the same warning, so it is only raised when it changes.
let shownWarning = null;

function warn(message) {
    if (message === shownWarning) return;
    shownWarning = message;
    if (message) notify(message, { error: true });
}

function render(status) {
    const { configured, connected, account, calendars, calendarId, enabled, warning } = status;

    dot.classList.toggle("connected", connected);
    connectedSection.style.display = connected ? "block" : "none";
    disconnectBtn.style.display = connected ? "inline-block" : "none";
    setupHint.style.display = configured ? "none" : "block";

    connectBtn.textContent = connected ? "Reconnect" : "Connect Google Calendar";
    connectBtn.disabled = !configured;

    if (!configured) {
        statusText.textContent = "Not set up";
    } else if (connected) {
        statusText.textContent = "Connected";
    } else {
        statusText.textContent = "Not connected — bookings stay in this app only";
    }
    accountText.textContent = connected && account ? account : "";

    calendarSelect.innerHTML = "";
    (calendars ?? []).forEach(calendar => {
        const option = document.createElement("option");
        option.value = calendar.id;
        option.textContent = calendar.name;
        calendarSelect.appendChild(option);
    });

    if (calendarId && !Array.from(calendarSelect.options).some(o => o.value === calendarId)) {
        const option = document.createElement("option");
        option.value = calendarId;
        option.textContent = connected && calendarId !== "primary"
            ? `${calendarId} — not available on this account`
            : calendarId;
        calendarSelect.appendChild(option);
    }
    calendarSelect.value = calendarId ?? "primary";
    enabledCheckbox.checked = Boolean(enabled);

    warn(warning ?? null);
}

async function loadStatus() {
    try {
        const res = await fetch(`${BASE}/status`);
        if (!res.ok) throw new Error("status request failed");
        render(await res.json());
    } catch (err) {
        console.error("Could not load Google Calendar status:", err);
        statusText.textContent = "Unavailable";
        notify("Could not read the Google Calendar settings.", { error: true });
    }
}

async function saveSettings() {
    try {
        const res = await fetch(`${BASE}/settings`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                calendarId: calendarSelect.value,
                enabled: enabledCheckbox.checked
            })
        });
        if (!res.ok) throw new Error("settings request failed");
        render(await res.json());
    } catch (err) {
        console.error("Could not save Google Calendar settings:", err);
        notify("Could not save the calendar settings.", { error: true });
    }
}

connectBtn.addEventListener("click", () => {
    window.location.href = `${BASE}/connect`;
});

disconnectBtn.addEventListener("click", async () => {
    const confirmed = await confirmDialog(
        "New bookings will stop being added to it. Events already there stay put.",
        { title: "Disconnect Google Calendar?", confirmLabel: "Disconnect", danger: true }
    );
    if (!confirmed) return;
    try {
        const res = await fetch(`${BASE}/disconnect`, { method: "POST" });
        if (!res.ok) throw new Error("disconnect request failed");
    } catch (err) {
        console.error("Could not disconnect Google Calendar:", err);
        notify("Could not disconnect from Google.", { error: true });
    }
    await loadStatus();
});

calendarSelect.addEventListener("change", saveSettings);
enabledCheckbox.addEventListener("change", saveSettings);

// The OAuth callback bounces back here with the outcome in the query string.
const params = new URLSearchParams(window.location.search);
if (params.get("calendar") === "error") {
    notify(params.get("message") ?? "Could not connect to Google Calendar.", { error: true });
}
if (params.has("calendar")) {
    window.history.replaceState({}, "", window.location.pathname);
}

loadStatus();
