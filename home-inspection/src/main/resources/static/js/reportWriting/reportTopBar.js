import { bookingId } from "./getReport.js";
import { notify, confirmDialog } from "../ui/dialog.js";

const viewReportButton = document.getElementById("view-report-button");

viewReportButton.addEventListener("click", () => {
    window.open(`/report/${bookingId}`, "_blank");
});

const editReportDataButton = document.getElementById("edit-report-data");

editReportDataButton.addEventListener("click", () => {
    const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.href = `report_data.html?id=${bookingId}&returnTo=${returnTo}`;
});

const emailReportButton = document.getElementById("email-report-button");

async function fetchRecipients(){
    try {
        const res = await fetch(`/report/${bookingId}/email`);
        return res.ok ? await res.json() : null;
    } catch (error){
        return null;
    }
}

emailReportButton.addEventListener("click", async () => {
    const recipients = await fetchRecipients();
    if (!recipients){
        notify("Could not reach server.", { error: true });
        return;
    }
    if (!recipients.linked){
        notify("Google is not linked, so the report cannot be emailed. Link it on the Profile page.", { error: true });
        return;
    }
    if (!recipients.to.length){
        notify("No client on this booking has an email address.", { error: true });
        return;
    }

    const cc = recipients.cc ? ` (cc ${recipients.cc})` : "";
    const confirmed = await confirmDialog(
        `You are sending the report to ${recipients.to.join(", ")}${cc}, with the PDF attached.`,
        {title : "Send email", confirmLabel : "Send", danger: true }
    );

    if (!confirmed) return;

    emailReportButton.disabled = true;
    const originalButtonText = emailReportButton.textContent;
    emailReportButton.textContent = "Sending...";

    try {
        const res = await fetch(`/report/${bookingId}/email`,
            {method: "POST"}
        );
        const data = await res.json();

        if (!res.ok){
            notify(data.error || "Could not send the report", {error: true});
        } else {
            notify(`Report sent to ${data.to}${data.cc ? ` (cc ${data.cc})` : ""}`);
        }
    } catch (error){
        notify("Could not reach server.",{ error: true});
    } finally {
        emailReportButton.disabled = false;
        emailReportButton.textContent = originalButtonText;
    }
});