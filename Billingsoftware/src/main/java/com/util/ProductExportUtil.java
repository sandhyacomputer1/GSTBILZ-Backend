package com.util;

import com.entity.ItemEntity;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility class to export product lists into Excel, CSV, and PDF byte arrays.
 *
 * BUG FIXES applied:
 *  - Replaced IndexedColors.INDIGO (does not exist) with XSSFColor RGB (63,82,227)
 *  - PDF rows use white background + black text to avoid invisible text on dark bg
 *  - PDF price uses "Rs." instead of rupee symbol (avoids Helvetica encoding gap)
 */
@Slf4j
public class ProductExportUtil {

    private static final String[] HEADERS = {
        "Product Name", "SKU", "Barcode", "Category", "Brand",
        "Purchase Price", "Selling Price", "Stock Qty", "GST %", "Unit", "Description"
    };

    // ─── EXCEL ──────────────────────────────────────────────────────────────────

    public static byte[] toExcelBytes(List<ItemEntity> items) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Products");

            // ── Header style (blue background, white bold text) ──
            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hFont);

            // Bulletproof standard colors
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBottomBorderColor(IndexedColors.WHITE.getIndex());

            // ── Alternate row styles ──
            XSSFCellStyle rowStyle1 = wb.createCellStyle();
            rowStyle1.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            rowStyle1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            rowStyle1.setBorderBottom(BorderStyle.THIN);
            rowStyle1.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            XSSFCellStyle rowStyle2 = wb.createCellStyle();
            rowStyle2.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            rowStyle2.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            rowStyle2.setBorderBottom(BorderStyle.THIN);
            rowStyle2.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            // ── Header row ──
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5500);
            }

            // ── Data rows ──
            int rowIdx = 1;
            for (ItemEntity item : items) {
                Row row = sheet.createRow(rowIdx);
                XSSFCellStyle style = (rowIdx % 2 == 0) ? rowStyle1 : rowStyle2;

                setCellWithStyle(row, 0, safe(item.getName()), style, wb);
                setCellWithStyle(row, 1, safe(item.getSku()), style, wb);
                setCellWithStyle(row, 2, safe(item.getBarcode()), style, wb);
                setCellWithStyle(row, 3, item.getCategory() != null ? safe(item.getCategory().getName()) : "", style, wb);
                setCellWithStyle(row, 4, safe(item.getBrand()), style, wb);
                setCellWithStyle(row, 5, String.valueOf(decimal(item.getPurchasePrice())), style, wb);
                setCellWithStyle(row, 6, String.valueOf(decimal(item.getSellingPrice() != null ? item.getSellingPrice() : item.getPrice())), style, wb);
                setCellWithStyle(row, 7, String.valueOf(item.getStockQuantity() != null ? item.getStockQuantity() : 0), style, wb);
                setCellWithStyle(row, 8, String.valueOf(decimal(item.getGstPercentage())), style, wb);
                setCellWithStyle(row, 9, safe(item.getUnit()), style, wb);
                setCellWithStyle(row, 10, safe(item.getDescription()), style, wb);
                rowIdx++;
            }

            // Auto-size name column
            sheet.setColumnWidth(0, 7000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─── CSV ────────────────────────────────────────────────────────────────────

    public static byte[] toCsvBytes(List<ItemEntity> items) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // UTF-8 BOM for Excel compatibility
            writer.write('\uFEFF');
            writer.write(String.join(",", HEADERS) + "\r\n");

            for (ItemEntity item : items) {
                writer.write(String.join(",",
                    csvEscape(item.getName()),
                    csvEscape(item.getSku()),
                    csvEscape(item.getBarcode()),
                    csvEscape(item.getCategory() != null ? item.getCategory().getName() : ""),
                    csvEscape(item.getBrand()),
                    String.valueOf(decimal(item.getPurchasePrice())),
                    String.valueOf(decimal(item.getSellingPrice() != null ? item.getSellingPrice() : item.getPrice())),
                    String.valueOf(item.getStockQuantity() != null ? item.getStockQuantity() : 0),
                    String.valueOf(decimal(item.getGstPercentage())),
                    csvEscape(item.getUnit()),
                    csvEscape(item.getDescription())
                ) + "\r\n");
            }
        }
        return out.toByteArray();
    }

    // ─── PDF ────────────────────────────────────────────────────────────────────

    /**
     * FIX: Use white/light background for rows so text is visible.
     * FIX: Use "Rs." instead of the rupee symbol (iText Helvetica built-in font
     *      does NOT include the Unicode rupee sign U+20B9, causing a blank glyph).
     */
    public static byte[] toPdfBytes(List<ItemEntity> items) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── Title ──
            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 16,
                    com.itextpdf.text.Font.BOLD, new BaseColor(63, 82, 227));
            Paragraph title = new Paragraph("Product Export Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            doc.add(title);

            String timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .format(java.time.LocalDateTime.now());
            com.itextpdf.text.Font subFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 9,
                    com.itextpdf.text.Font.NORMAL, BaseColor.DARK_GRAY);
            Paragraph sub = new Paragraph(
                    "Generated: " + timestamp + "  |  Total Products: " + items.size(), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14);
            doc.add(sub);

            // ── Table (7 cols to fit landscape A4) ──
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{28, 10, 14, 12, 12, 10, 8});
            table.setSpacingBefore(4);

            // Header row — dark blue-grey bg, white bold text
            BaseColor headerBg = new BaseColor(63, 82, 227);
            com.itextpdf.text.Font hFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 9,
                    com.itextpdf.text.Font.BOLD, BaseColor.WHITE);
            String[] pdfHeaders = {"Product Name", "SKU", "Category", "Brand", "Price (Rs.)", "Stock", "GST%"};
            for (String h : pdfHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
                cell.setBackgroundColor(headerBg);
                cell.setPaddingTop(6);
                cell.setPaddingBottom(6);
                cell.setPaddingLeft(4);
                cell.setPaddingRight(4);
                cell.setBorderColor(new BaseColor(40, 60, 180));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Data rows — white / very light grey alternating (black text = readable)
            com.itextpdf.text.Font rowFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 8,
                    com.itextpdf.text.Font.NORMAL, BaseColor.BLACK);
            com.itextpdf.text.Font rowFontAlt = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 8,
                    com.itextpdf.text.Font.NORMAL, new BaseColor(30, 30, 30));
            BaseColor rowBgWhite = BaseColor.WHITE;
            BaseColor rowBgAlt   = new BaseColor(240, 242, 255); // very light indigo tint
            BaseColor borderCol  = new BaseColor(200, 204, 240);

            boolean alt = false;
            for (ItemEntity item : items) {
                BaseColor bg   = alt ? rowBgAlt : rowBgWhite;
                com.itextpdf.text.Font rf = alt ? rowFontAlt : rowFont;
                alt = !alt;

                // FIX: Use "Rs." instead of ₹ — Helvetica can't encode Unicode rupee
                String priceStr = "Rs." + String.format("%.2f",
                        decimal(item.getSellingPrice() != null ? item.getSellingPrice() : item.getPrice()));

                String[] values = {
                    safe(item.getName()),
                    safe(item.getSku()),
                    item.getCategory() != null ? safe(item.getCategory().getName()) : "—",
                    safe(item.getBrand()),
                    priceStr,
                    String.valueOf(item.getStockQuantity() != null ? item.getStockQuantity() : 0),
                    String.format("%.1f%%", decimal(item.getGstPercentage()))
                };

                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, rf));
                    cell.setBackgroundColor(bg);
                    cell.setPaddingTop(5);
                    cell.setPaddingBottom(5);
                    cell.setPaddingLeft(4);
                    cell.setPaddingRight(4);
                    cell.setBorderColor(borderCol);
                    table.addCell(cell);
                }
            }

            doc.add(table);

        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private static void setCellWithStyle(Row row, int col, String value, CellStyle style, Workbook wb) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double decimal(BigDecimal bd) {
        return bd == null ? 0.0 : bd.doubleValue();
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
