package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;

// What the report data page reads and saves; the report's items and photos stay out of it.
public record ReportSettings(String summary, String emailBody, Boolean showInvoice, String appendixPdf,
                             CoverPageImage coverPageImage) {

    public record CoverPageImage(String imageUrl) {}

    public static ReportSettings of(InspectionReport report){
        InspectionImage cover = report.getCoverPageImage();
        return new ReportSettings(report.getSummary(), report.getEmailBody(), report.getShowInvoice(),
                report.getAppendixPdf(), cover == null ? null : new CoverPageImage(cover.getImageUrl()));
    }
}
