import { bookingId } from "./getReport.js";

const viewReportButton = document.getElementById("view-report-button");

viewReportButton.addEventListener("click", () => {
    window.location.href = `http://localhost:8080/report/${bookingId}`;
});