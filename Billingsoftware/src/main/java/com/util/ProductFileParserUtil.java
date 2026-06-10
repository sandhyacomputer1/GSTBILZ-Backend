package com.util;

import com.io.ProductImportDTO;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing product data from various file formats.
 * All parsers return a list of ProductImportDTO without validation or DB interaction.
 *
 * Expected Excel / CSV column order (headers are auto-detected, fallback to positional):
 *   0: Product Name  1: SKU  2: Barcode  3: Category  4: Brand
 *   5: Purchase Price  6: Selling Price  7: Stock Qty  8: GST%  9: Unit  10: Description
 */
@Slf4j
public class ProductFileParserUtil {

    // ─── EXCEL ──────────────────────────────────────────────────────────────────

    public static List<ProductImportDTO> parseExcel(MultipartFile file) throws IOException {
        List<ProductImportDTO> results = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int[] colMap = detectExcelColumns(sheet.getRow(0));

            for (int rn = 1; rn <= sheet.getLastRowNum(); rn++) {
                Row row = sheet.getRow(rn);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    ProductImportDTO dto = mapExcelRow(row, rn + 1, colMap);
                    results.add(dto);
                } catch (Exception e) {
                    log.warn("Skipping Excel row {}: {}", rn + 1, e.getMessage());
                    results.add(ProductImportDTO.builder()
                            .rowNumber(rn + 1)
                            .rowStatus("INVALID")
                            .validationError("Parse error: " + e.getMessage())
                            .build());
                }
            }
        }
        return results;
    }

    // ─── CSV ────────────────────────────────────────────────────────────────────

    public static List<ProductImportDTO> parseCsv(MultipartFile file) throws IOException, CsvException {
        List<ProductImportDTO> results = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return results;

            // Detect header row
            String[] header = rows.get(0);
            int[] colMap = detectCsvColumns(header);

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (isEmptyCsvRow(row)) continue;
                try {
                    ProductImportDTO dto = mapCsvRow(row, i + 1, colMap);
                    results.add(dto);
                } catch (Exception e) {
                    log.warn("Skipping CSV row {}: {}", i + 1, e.getMessage());
                    results.add(ProductImportDTO.builder()
                            .rowNumber(i + 1)
                            .rowStatus("INVALID")
                            .validationError("Parse error: " + e.getMessage())
                            .build());
                }
            }
        }
        return results;
    }

    // ─── PDF ────────────────────────────────────────────────────────────────────

    public static List<ProductImportDTO> parsePdf(MultipartFile file) throws IOException {
        List<ProductImportDTO> results = new ArrayList<>();

        byte[] bytes = file.getBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("PDF is empty or unreadable");
            }

            String[] lines = text.split("\\r?\\n");
            int rowNumber = 0;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || isHeaderLine(line)) continue;
                rowNumber++;

                try {
                    // Try comma / tab split → expect: name, sku, barcode, cat, brand, purchPrice, sellPrice, qty, gst, unit, desc
                    String[] parts = line.split("[,\\t|]", -1);
                    ProductImportDTO dto = mapPartsToDto(parts, rowNumber);
                    results.add(dto);
                } catch (Exception e) {
                    log.warn("Skipping PDF line {}: {}", rowNumber, e.getMessage());
                    results.add(ProductImportDTO.builder()
                            .rowNumber(rowNumber)
                            .rowStatus("INVALID")
                            .validationError("Parse error: " + e.getMessage())
                            .build());
                }
            }
        }
        return results;
    }

    // ─── DOCX ───────────────────────────────────────────────────────────────────

    public static List<ProductImportDTO> parseDocx(MultipartFile file) throws IOException {
        List<ProductImportDTO> results = new ArrayList<>();

        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            int rowNumber = 0;

            for (XWPFParagraph para : paragraphs) {
                String line = para.getText().trim();
                if (line.isEmpty() || isHeaderLine(line)) continue;
                rowNumber++;

                try {
                    String[] parts = line.split("[,\\t|]", -1);
                    ProductImportDTO dto = mapPartsToDto(parts, rowNumber);
                    results.add(dto);
                } catch (Exception e) {
                    log.warn("Skipping DOCX line {}: {}", rowNumber, e.getMessage());
                    results.add(ProductImportDTO.builder()
                            .rowNumber(rowNumber)
                            .rowStatus("INVALID")
                            .validationError("Parse error: " + e.getMessage())
                            .build());
                }
            }
        }
        return results;
    }

    // ─── EXCEL TEMPLATE GENERATOR ───────────────────────────────────────────────

    public static byte[] generateExcelTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Products");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] headers = {
                "Product Name*", "SKU*", "Barcode", "Category", "Brand",
                "Purchase Price", "Selling Price*", "Stock Qty", "GST %", "Unit", "Description"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Sample data row
            String[] sample = {
                "Laptop Pro X1", "LAP001", "8901234567890", "Electronics", "Dell",
                "45000", "55000", "10", "18", "PCS", "High-performance laptop"
            };
            Row sampleRow = sheet.createRow(1);
            for (int i = 0; i < sample.length; i++) {
                sampleRow.createCell(i).setCellValue(sample[i]);
            }

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ─── INTERNAL HELPERS ───────────────────────────────────────────────────────

    private static final String[] NAME_HINTS    = {"name", "product", "item", "title"};
    private static final String[] SKU_HINTS     = {"sku", "code", "stock", "keeping"};
    private static final String[] BARCODE_HINTS = {"barcode", "bar", "ean", "upc"};
    private static final String[] CAT_HINTS     = {"category", "cat", "group", "type"};
    private static final String[] BRAND_HINTS   = {"brand", "make", "manufacturer"};
    private static final String[] PPRICE_HINTS  = {"purchase", "cost", "buy"};
    private static final String[] SPRICE_HINTS  = {"selling", "sell", "price", "rate", "mrp"};
    private static final String[] QTY_HINTS     = {"stock", "qty", "quantity"};
    private static final String[] GST_HINTS     = {"gst", "tax", "vat", "%"};
    private static final String[] UNIT_HINTS    = {"unit", "uom", "measure"};
    private static final String[] DESC_HINTS    = {"desc", "detail", "info", "note"};

    private static int[] detectExcelColumns(Row headerRow) {
        // Returns index array: [name, sku, barcode, cat, brand, pprice, sprice, qty, gst, unit, desc]
        int[] cols = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        if (headerRow == null) return cols;

        for (int cn = 0; cn < headerRow.getLastCellNum(); cn++) {
            Cell cell = headerRow.getCell(cn);
            if (cell == null) continue;
            String h = cell.getStringCellValue().toLowerCase().trim();
            if (matchesAny(h, NAME_HINTS) && cols[0] == 0 && cn != 0) cols[0] = cn;
            else if (matchesAny(h, SKU_HINTS))     cols[1] = cn;
            else if (matchesAny(h, BARCODE_HINTS)) cols[2] = cn;
            else if (matchesAny(h, CAT_HINTS))     cols[3] = cn;
            else if (matchesAny(h, BRAND_HINTS))   cols[4] = cn;
            else if (matchesAny(h, PPRICE_HINTS) && !matchesAny(h, SPRICE_HINTS)) cols[5] = cn;
            else if (matchesAny(h, SPRICE_HINTS))  cols[6] = cn;
            else if (matchesAny(h, QTY_HINTS))     cols[7] = cn;
            else if (matchesAny(h, GST_HINTS))     cols[8] = cn;
            else if (matchesAny(h, UNIT_HINTS))    cols[9] = cn;
            else if (matchesAny(h, DESC_HINTS))    cols[10] = cn;
        }
        return cols;
    }

    private static int[] detectCsvColumns(String[] header) {
        int[] cols = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        for (int i = 0; i < header.length; i++) {
            String h = header[i].toLowerCase().trim();
            if (matchesAny(h, NAME_HINTS) && i != 0) cols[0] = i;
            else if (matchesAny(h, SKU_HINTS))     cols[1] = i;
            else if (matchesAny(h, BARCODE_HINTS)) cols[2] = i;
            else if (matchesAny(h, CAT_HINTS))     cols[3] = i;
            else if (matchesAny(h, BRAND_HINTS))   cols[4] = i;
            else if (matchesAny(h, PPRICE_HINTS) && !matchesAny(h, SPRICE_HINTS)) cols[5] = i;
            else if (matchesAny(h, SPRICE_HINTS))  cols[6] = i;
            else if (matchesAny(h, QTY_HINTS))     cols[7] = i;
            else if (matchesAny(h, GST_HINTS))     cols[8] = i;
            else if (matchesAny(h, UNIT_HINTS))    cols[9] = i;
            else if (matchesAny(h, DESC_HINTS))    cols[10] = i;
        }
        return cols;
    }

    private static boolean matchesAny(String value, String[] hints) {
        for (String hint : hints) {
            if (value.contains(hint)) return true;
        }
        return false;
    }

    private static ProductImportDTO mapExcelRow(Row row, int rowNum, int[] cm) {
        return ProductImportDTO.builder()
                .rowNumber(rowNum)
                .productName(getCellStr(row, cm[0]))
                .sku(getCellStr(row, cm[1]))
                .barcode(getCellStr(row, cm[2]))
                .category(getCellStr(row, cm[3]))
                .brand(getCellStr(row, cm[4]))
                .purchasePrice(getCellDecimal(row, cm[5]))
                .sellingPrice(getCellDecimal(row, cm[6]))
                .stockQuantity(getCellInt(row, cm[7]))
                .gstPercentage(getCellDecimal(row, cm[8]))
                .unit(getCellStr(row, cm[9]))
                .description(getCellStr(row, cm[10]))
                .rowStatus("VALID")
                .build();
    }

    private static ProductImportDTO mapCsvRow(String[] row, int rowNum, int[] cm) {
        return ProductImportDTO.builder()
                .rowNumber(rowNum)
                .productName(safeGet(row, cm[0]))
                .sku(safeGet(row, cm[1]))
                .barcode(safeGet(row, cm[2]))
                .category(safeGet(row, cm[3]))
                .brand(safeGet(row, cm[4]))
                .purchasePrice(parseDecimal(safeGet(row, cm[5])))
                .sellingPrice(parseDecimal(safeGet(row, cm[6])))
                .stockQuantity(parseInt(safeGet(row, cm[7])))
                .gstPercentage(parseDecimal(safeGet(row, cm[8])))
                .unit(safeGet(row, cm[9]))
                .description(safeGet(row, cm[10]))
                .rowStatus("VALID")
                .build();
    }

    private static ProductImportDTO mapPartsToDto(String[] parts, int rowNum) {
        return ProductImportDTO.builder()
                .rowNumber(rowNum)
                .productName(safeGet(parts, 0))
                .sku(safeGet(parts, 1))
                .barcode(safeGet(parts, 2))
                .category(safeGet(parts, 3))
                .brand(safeGet(parts, 4))
                .purchasePrice(parseDecimal(safeGet(parts, 5)))
                .sellingPrice(parseDecimal(safeGet(parts, 6)))
                .stockQuantity(parseInt(safeGet(parts, 7)))
                .gstPercentage(parseDecimal(safeGet(parts, 8)))
                .unit(safeGet(parts, 9))
                .description(safeGet(parts, 10))
                .rowStatus("VALID")
                .build();
    }

    // ─── Cell value helpers ──────────────────────────────────────────────────────

    private static String getCellStr(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == (long) v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private static BigDecimal getCellDecimal(Row row, int col) {
        String s = getCellStr(row, col);
        return parseDecimal(s);
    }

    private static Integer getCellInt(Row row, int col) {
        String s = getCellStr(row, col);
        return parseInt(s);
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.replaceAll("[^0-9.]", "")); }
        catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    private static String safeGet(String[] arr, int idx) {
        return (arr != null && idx < arr.length) ? arr[idx].trim() : "";
    }

    private static boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private static boolean isEmptyCsvRow(String[] row) {
        if (row == null || row.length == 0) return true;
        for (String s : row) { if (!s.isBlank()) return false; }
        return true;
    }

    private static boolean isHeaderLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("product name") || lower.contains("sku") ||
               lower.startsWith("----") || lower.startsWith("s.no") ||
               lower.startsWith("sr.") || lower.startsWith("#");
    }
}
