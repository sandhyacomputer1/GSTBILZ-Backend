package com.controller;

import com.io.*;
import com.service.ItemImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller for all Product Import & Export operations.
 *
 * All endpoints are secured under /admin/** — ADMIN role required (enforced by SecurityConfig).
 *
 * Endpoints:
 *   POST   /admin/items/import/preview        → Parse file, return preview (no DB write)
 *   POST   /admin/items/import/confirm        → Confirm import with duplicate strategy
 *   GET    /admin/items/export                → Export products (format, category, brand, inStockOnly)
 *   GET    /admin/items/import/template       → Download blank Excel import template
 *   GET    /admin/items/import/history        → Fetch past import history
 *   POST   /admin/items/import/images         → Upload ZIP → link images by SKU/barcode
 *
 *   POST   /admin/items/import-excel          → Legacy Excel import (backward-compat)
 *   POST   /admin/items/import-pdf            → Legacy PDF import (backward-compat)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ItemImportController {

    private final ItemImportService itemImportService;

    // Maximum allowed upload size: 50 MB
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    // ─── PHASE 1: PREVIEW ────────────────────────────────────────────────────────

    @PostMapping("/admin/items/import/preview")
    public ResponseEntity<ImportSummaryResponse> previewImport(
            @RequestParam("file") MultipartFile file) {

        log.info("Import preview request: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
        validateFile(file, MAX_FILE_SIZE, "xlsx", "xls", "csv", "pdf", "docx");

        try {
            ImportSummaryResponse response = itemImportService.previewFile(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Preview failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Preview failed: " + e.getMessage(), e);
        }
    }

    // ─── PHASE 2: CONFIRM ────────────────────────────────────────────────────────

    @PostMapping("/admin/items/import/confirm")
    public ResponseEntity<ImportSummaryResponse> confirmImport(
            @RequestBody ImportConfirmRequest request) {

        log.info("Import confirm request: {} products, mode={}", request.getProducts().size(), request.getDuplicateMode());

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No products to import");
        }
        if (request.getDuplicateMode() == null ||
                !List.of("SKIP", "UPDATE", "CREATE").contains(request.getDuplicateMode().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "duplicateMode must be one of: SKIP, UPDATE, CREATE");
        }

        try {
            ImportSummaryResponse response = itemImportService.confirmImport(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Confirm import failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Import confirm failed: " + e.getMessage(), e);
        }
    }

    // ─── EXPORT ──────────────────────────────────────────────────────────────────

    @GetMapping("/admin/items/export")
    public ResponseEntity<byte[]> exportProducts(
            @RequestParam(defaultValue = "EXCEL") String format,
            @RequestParam(required = false)       String category,
            @RequestParam(required = false)       String brand,
            @RequestParam(defaultValue = "false") boolean inStockOnly) {

        log.info("Export request: format={}, category={}, brand={}, inStockOnly={}", format, category, brand, inStockOnly);

        ExportRequest request = ExportRequest.builder()
                .format(format.toUpperCase())
                .category(category)
                .brand(brand)
                .inStockOnly(inStockOnly)
                .build();

        try {
            byte[] data = itemImportService.exportProducts(request);

            String filename;
            MediaType mediaType;
            switch (format.toUpperCase()) {
                case "CSV" -> {
                    filename = "products_export.csv";
                    mediaType = MediaType.parseMediaType("text/csv");
                }
                case "PDF" -> {
                    filename = "products_export.pdf";
                    mediaType = MediaType.APPLICATION_PDF;
                }
                default -> {
                    filename = "products_export.xlsx";
                    mediaType = MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(data);
        } catch (Exception e) {
            log.error("Export failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Export failed: " + e.getMessage(), e);
        }
    }

    // ─── TEMPLATE DOWNLOAD ────────────────────────────────────────────────────────

    @GetMapping("/admin/items/import/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("Template download request");
        try {
            byte[] data = itemImportService.downloadTemplate();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"product_import_template.xlsx\"")
                    .body(data);
        } catch (Exception e) {
            log.error("Template generation failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Template generation failed: " + e.getMessage(), e);
        }
    }

    // ─── IMPORT HISTORY ───────────────────────────────────────────────────────────

    @GetMapping("/admin/items/import/history")
    public ResponseEntity<List<ImportHistoryResponse>> getImportHistory() {
        log.info("Import history request");
        try {
            return ResponseEntity.ok(itemImportService.getImportHistory());
        } catch (Exception e) {
            log.error("Import history fetch failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not fetch import history: " + e.getMessage(), e);
        }
    }

    // ─── ZIP IMAGE IMPORT ─────────────────────────────────────────────────────────

    @PostMapping("/admin/items/import/images")
    public ResponseEntity<String> importImages(
            @RequestParam("file") MultipartFile file) {

        log.info("ZIP image import: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
        validateFile(file, MAX_FILE_SIZE, "zip");

        try {
            int linked = itemImportService.importZipImages(file);
            return ResponseEntity.ok(linked + " images linked to products successfully.");
        } catch (Exception e) {
            log.error("ZIP image import failed: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ZIP image import failed: " + e.getMessage(), e);
        }
    }

    // ─── LEGACY ENDPOINTS (backward-compat) ──────────────────────────────────────

    @PostMapping("/admin/items/import-excel")
    public ResponseEntity<ImportSummaryResponse> importExcel(@RequestParam("file") MultipartFile file) {
        log.info("[LEGACY] Excel import: {}", file.getOriginalFilename());
        validateFile(file, MAX_FILE_SIZE, "xlsx", "xls");
        try {
            return ResponseEntity.ok(itemImportService.importExcel(file));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Excel import failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/admin/items/import-pdf")
    public ResponseEntity<ImportSummaryResponse> importPdf(@RequestParam("file") MultipartFile file) {
        log.info("[LEGACY] PDF import: {}", file.getOriginalFilename());
        validateFile(file, MAX_FILE_SIZE, "pdf");
        try {
            return ResponseEntity.ok(itemImportService.importPdf(file));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF import failed: " + e.getMessage(), e);
        }
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file, long maxBytes, String... allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty or missing");
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File too large. Maximum allowed size is " + (maxBytes / 1024 / 1024) + " MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        for (String allowed : allowedExtensions) {
            if (ext.equals(allowed)) return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported file type: ." + ext + ". Allowed: " + String.join(", ", allowedExtensions));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
