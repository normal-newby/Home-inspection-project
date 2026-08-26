const BASE = "http://localhost:8080/api/google/calendar";

const dot = document.getElementById("calendarDot");
const statusText = document.getElementById("calendarStatusText");
const accountText = document.getElementById("calendarAccount");
const errorBox = document.getElementById("calendarError");
const connectedSection = document.getElementById("calendarConnected");
const setupHint = document.getElementById("calendarSetupHint");
const calendarSelect = document.getElementById("googleCalendarId");
const enabledCheckbox = document.getElementById("googleCalendarEnabled");
const connectBtn = document.getElementById("calendarConnectBtn");
const disconnectBtn = document.getElementById("calendarDisconnectBtn");

function showError(message) {
    errorBox.textContent = message ?? "";
    errorBox.classList.toggle("show", Boolean(message));
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

    if (warning) showError(warning);
}

async function loadStatus() {
    try {
        const res = await fetch(`${BASE}/status`);
        if (!res.ok) throw new Error("status request failed");
        render(await res.json());
    } catch (err) {
        console.error("Could not load Google Calendar status:", err);
        statusText.textContent = "Unavailable";
        showError("Could not read the Google Calendar settings.");
    }
}

async function saveSettings() {
    showError(null);
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
        showError("Could not save the calendar settings.");
    }
}

connectBtn.addEventListener("click", () => {
    window.location.href = `${BASE}/connect`;
});

disconnectBtn.addEventListener("click", async () => {
    if (!confirm("Disconnect Google Calendar? New bookings will stop being added to it.")) return;
    showError(null);
    try {
        const res = await fetch(`${BASE}/disconnect`, { method: "POST" });
        if (!res.ok) throw new Error("disconnect request failed");
    } catch (err) {
        console.error("Could not disconnect Google Calendar:", err);
        showError("Could not disconnect from Google.");
    }
    await loadStatus();
});

calendarSelect.addEventListener("change", saveSettings);
enabledCheckbox.addEventListener("change", saveSettings);

// The OAuth callback bounces back here with the outcome in the query string.
const params = new URLSearchParams(window.location.search);
if (params.get("calendar") === "error") {
    showError(params.get("message") ?? "Could not connect to Google Calendar.");
}
if (params.has("calendar")) {
    window.history.replaceState({}, "", window.location.pathname);
}

loadStatus();
