package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.AnalyticsExportRow;
import ca.inspection.home.inspection.DTO.InvoiceAmount;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public final class AnalyticsSpreadsheet {
    private static final String[] HEADERS = {
            "Inspection #", "Date", "Address",
            "Client", "Client Email", "Client Phone",
            "Agent", "Agent Phone", "Agent Email",
            "Inspection Type", "Subtotal", "HST", "GST", "Total"
    };
    // In characters; autoSizeColumn needs AWT fonts, which the Docker JRE may not have.
    private static final int[] WIDTHS = {12, 12, 40, 28, 32, 18, 24, 18, 30, 30, 12, 11, 11, 12};
    private static final int FIRST_MONEY_COLUMN = 10;
    private static final List<Function<InvoiceAmount, BigDecimal>> MONEY = List.of(
            InvoiceAmount::getSubtotal, InvoiceAmount::getHst, InvoiceAmount::getGst, InvoiceAmount::getTotal);

    private AnalyticsSpreadsheet() {}

    public static byte[] write(List<AnalyticsExportRow> rows){
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inspections");

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setBorderBottom(BorderStyle.THIN);
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            CellStyle totalLabel = workbook.createCellStyle();
            totalLabel.setFont(bold);
            totalLabel.setBorderTop(BorderStyle.THIN);
            CellStyle totalMoney = workbook.createCellStyle();
            totalMoney.cloneStyleFrom(money);
            totalMoney.setFont(bold);
            totalMoney.setBorderTop(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(header);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            int r = 1;
            for (AnalyticsExportRow row : rows) {
                Row line = sheet.createRow(r++);
                if (row.inspectionNumber() != null) line.createCell(0).setCellValue(row.inspectionNumber());
                if (row.inspectionDate() != null) {
                    Cell cell = line.createCell(1);
                    cell.setCellValue(row.inspectionDate());
                    cell.setCellStyle(date);
                }
                text(line, 2, row.address());
                text(line, 3, row.clientNames());
                text(line, 4, row.clientEmails());
                text(line, 5, row.clientPhones());
                text(line, 6, row.agentName());
                text(line, 7, row.agentPhone());
                text(line, 8, row.agentEmail());
                text(line, 9, row.inspectionTypes());
                for (int m = 0; m < MONEY.size(); m++) {
                    BigDecimal value = MONEY.get(m).apply(row.amount());
                    sums[m] = sums[m].add(value);
                    Cell cell = line.createCell(FIRST_MONEY_COLUMN + m);
                    cell.setCellValue(value.doubleValue());
                    cell.setCellStyle(money);
                }
            }

            // Written as values rather than SUM formulas so previewers that don't recalculate still show them.
            Row totals = sheet.createRow(r);
            for (int i = 0; i < FIRST_MONEY_COLUMN; i++) totals.createCell(i).setCellStyle(totalLabel);
            totals.getCell(0).setCellValue("Total (" + rows.size() + (rows.size() == 1 ? " inspection)" : " inspections)"));
            for (int m = 0; m < sums.length; m++) {
                Cell cell = totals.createCell(FIRST_MONEY_COLUMN + m);
                cell.setCellValue(sums[m].doubleValue());
                cell.setCellStyle(totalMoney);
            }

            sheet.createFreezePane(0, 1);
            if (!rows.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, HEADERS.length - 1));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the analytics spreadsheet", e);
        }
    }

    private static void text(Row row, int column, String value){
        if (HelperFunctions.notBlank(value)) row.createCell(column).setCellValue(value.trim());
    }
}
