import { bookingId } from "./getReport.js";
import { notify } from "../ui/dialog.js";

const viewReportButton = document.getElementById("view-report-button");

viewReportButton.addEventListener("click", () => {
    window.location.href = `http://localhost:8080/report/${bookingId}`;
});

const editReportDataButton = document.getElementById("edit-report-data");

editReportDataButton.addEventListener("click", () => {
    window.location.href = `report_data.html?id=${bookingId}`;
});

const emailReportButton = document.getElementById("email-report-button");

emailReportButton.addEventListener("click", async () => {
    emailReportButton.disabled = true;
    const originalButtonText = emailReportButton.textContent;
    emailReportButton.textContent = "Sending...";

    try {
        const res = await fetch(`http://localhost:8080/report/${bookingId}/email`,
            {method: "POST"}
        );
        const data = await res.json();

        if (!res.ok){
            notify(data.error || "Could not send the report", {error: true});
        } else {
            notify(`Report sent to ${data.to}`);
        }
    } catch (error){
        notify("Could not reach server.",{ error: true});
    } finally {
        emailReportButton.disabled = false;
        emailReportButton.textContent = originalButtonText;
    }
});