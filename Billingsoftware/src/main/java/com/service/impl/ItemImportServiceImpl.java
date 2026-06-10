package com.service.impl;

import com.entity.CategoryEntity;
import com.entity.ImportHistoryEntity;
import com.entity.ItemEntity;
import com.io.*;
import com.repository.CategoryRepository;
import com.repository.ImportHistoryRepository;
import com.repository.ItemRepository;
import com.repository.UserRepository;
import com.service.ItemImportService;
import com.entity.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.util.ProductExportUtil;
import com.util.ProductFileParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Full implementation of the Product Import & Export service.
 *
 * IMPORT FLOW:
 *  Phase 1 — previewFile()    → parse file, detect duplicates, return preview DTOs (NO DB write)
 *  Phase 2 — confirmImport()  → batch insert with chosen duplicate strategy
 *
 * EXPORT FLOW:
 *  exportProducts() → filter items, serialise to EXCEL / CSV / PDF bytes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemImportServiceImpl implements ItemImportService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ImportHistoryRepository importHistoryRepository;
    private final UserRepository userRepository;

    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // ─── PHASE 1: PREVIEW ────────────────────────────────────────────────────────

    @Override
    public ImportSummaryResponse previewFile(MultipartFile file) {
        long start = System.currentTimeMillis();
        String ext = getExtension(file.getOriginalFilename());

        List<ProductImportDTO> parsed;
        try {
            parsed = switch (ext) {
                case "xlsx", "xls" -> ProductFileParserUtil.parseExcel(file);
                case "csv"         -> ProductFileParserUtil.parseCsv(file);
                case "pdf"         -> ProductFileParserUtil.parsePdf(file);
                case "docx"        -> ProductFileParserUtil.parseDocx(file);
                default -> throw new IllegalArgumentException("Unsupported file type: " + ext);
            };
        } catch (Exception e) {
            log.error("File parse error: ", e);
            throw new RuntimeException("Could not parse file: " + e.getMessage(), e);
        }

        // Validation & duplicate detection
        int duplicates = 0;
        for (ProductImportDTO dto : parsed) {
            if ("INVALID".equals(dto.getRowStatus())) continue;

            // Field validation
            String err = validateProductDto(dto);
            if (err != null) {
                dto.setRowStatus("INVALID");
                dto.setValidationError(err);
                continue;
            }

            // Duplicate detection (SKU or Barcode exists in DB)
            UserEntity currentUser = getLoggedInUser();
            boolean skuDup = dto.getSku() != null && !dto.getSku().isBlank()
                    && !itemRepository.findBySkuAndUserId(dto.getSku(), currentUser.getTenantId()).isEmpty();
            boolean bcDup  = dto.getBarcode() != null && !dto.getBarcode().isBlank()
                    && !itemRepository.findByBarcodeAndUserId(dto.getBarcode(), currentUser.getTenantId()).isEmpty();

            if (skuDup) {
                dto.setRowStatus("DUPLICATE_SKU");
                duplicates++;
            } else if (bcDup) {
                dto.setRowStatus("DUPLICATE_BARCODE");
                duplicates++;
            }
            // else stays VALID
        }

        long elapsed = System.currentTimeMillis() - start;
        long valid  = parsed.stream().filter(d -> "VALID".equals(d.getRowStatus())).count();
        long inv    = parsed.stream().filter(d -> "INVALID".equals(d.getRowStatus())).count();

        return ImportSummaryResponse.builder()
                .totalRecords(parsed.size())
                .successCount((int) valid)
                .failedCount((int) inv)
                .duplicatesFound(duplicates)
                .processingTimeMs(elapsed)
                .previewItems(parsed)
                .build();
    }

    // ─── PHASE 2: CONFIRM ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ImportSummaryResponse confirmImport(ImportConfirmRequest request) {
        long start = System.currentTimeMillis();
        List<ImportRowStatus> statuses = new ArrayList<>();
        int success = 0, failed = 0, skipped = 0, updated = 0, duplicates = 0;

        String mode = request.getDuplicateMode() == null ? "SKIP" : request.getDuplicateMode().toUpperCase();
        List<ItemEntity> batchSave = new ArrayList<>();
        Map<String, CategoryEntity> categoryCache = new HashMap<>();

        for (ProductImportDTO dto : request.getProducts()) {
            // Skip rows that were already invalid in preview
            if ("INVALID".equals(dto.getRowStatus())) {
                failed++;
                statuses.add(rowStatus(dto, "FAILED", null, dto.getValidationError()));
                continue;
            }

            try {
                boolean isDuplicate = "DUPLICATE_SKU".equals(dto.getRowStatus())
                        || "DUPLICATE_BARCODE".equals(dto.getRowStatus());

                if (isDuplicate) {
                    duplicates++;
                    switch (mode) {
                        case "SKIP" -> {
                            skipped++;
                            statuses.add(rowStatus(dto, "SKIPPED", "SKIP", "Duplicate — skipped"));
                            continue;
                        }
                        case "UPDATE" -> {
                            ItemEntity existing = findExistingBySkuOrBarcode(dto);
                            if (existing != null) {
                                updateEntity(existing, dto);
                                batchSave.add(existing);
                                updated++;
                                statuses.add(rowStatus(dto, "UPDATED", "UPDATE", null));
                                continue;
                            }
                        }
                        // CREATE → fall through and insert as new
                    }
                }

                UserEntity currentUser = getLoggedInUser();

                CategoryEntity category = resolveOrCreateCategory(
                        dto.getCategory() != null && !dto.getCategory().isBlank() ? dto.getCategory() : "General", currentUser.getTenantId(), categoryCache);

                ItemEntity item = ItemEntity.builder()
                        .itemId(UUID.randomUUID().toString())
                        .name(dto.getProductName())
                        .sku(dto.getSku())
                        .barcode(dto.getBarcode())
                        .brand(dto.getBrand())
                        .unit(dto.getUnit())
                        .purchasePrice(dto.getPurchasePrice())
                        .sellingPrice(dto.getSellingPrice())
                        .price(dto.getSellingPrice())   // backward-compat
                        .stockQuantity(dto.getStockQuantity() != null ? dto.getStockQuantity() : 0)
                        .gstPercentage(dto.getGstPercentage())
                        .description(dto.getDescription() != null && !dto.getDescription().isBlank()
                                ? dto.getDescription() : "Imported — " + dto.getProductName())
                        .imgUrl(dto.getImageUrl() != null && !dto.getImageUrl().isBlank() 
                                ? dto.getImageUrl() : "https://placehold.co/150x150/202c33/ffffff/png?text=No+Image")
                        .category(category)
                        .userId(currentUser.getTenantId())
                        .build();

                batchSave.add(item);
                success++;
                statuses.add(rowStatus(dto, "SUCCESS", isDuplicate ? "CREATE" : null, null));

            } catch (Exception e) {
                failed++;
                log.error("Row {} import failed: {}", dto.getRowNumber(), e.getMessage());
                statuses.add(rowStatus(dto, "FAILED", null, e.getMessage()));
            }
        }

        // Batch save
        itemRepository.saveAll(batchSave);

        long elapsed = System.currentTimeMillis() - start;

        // Persist history
        UserEntity currentUser = getLoggedInUser();
        importHistoryRepository.save(ImportHistoryEntity.builder()
                .importId(UUID.randomUUID().toString())
                .importedBy(currentUser.getEmail())
                .fileName("batch-import")
                .fileType("MULTI")
                .totalRecords(request.getProducts().size())
                .successCount(success + updated)
                .failedCount(failed)
                .duplicatesFound(duplicates)
                .processingTimeMs(elapsed)
                .userId(currentUser.getTenantId())
                .build());

        return ImportSummaryResponse.builder()
                .totalRecords(request.getProducts().size())
                .successCount(success + updated)
                .failedCount(failed)
                .duplicatesFound(duplicates)
                .processingTimeMs(elapsed)
                .importedItems(statuses)
                .build();
    }

    // ─── EXPORT ───────────────────────────────────────────────────────────────────

    @Override
    public byte[] exportProducts(ExportRequest request) {
        UserEntity currentUser = getLoggedInUser();
        String cat  = request.getCategory() != null && request.getCategory().isBlank() ? null : request.getCategory();
        String brand = request.getBrand() != null && request.getBrand().isBlank() ? null : request.getBrand();

        List<ItemEntity> items = itemRepository.findByFilters(currentUser.getTenantId(), cat, brand, request.isInStockOnly());

        try {
            return switch (request.getFormat().toUpperCase()) {
                case "EXCEL" -> ProductExportUtil.toExcelBytes(items);
                case "CSV"   -> ProductExportUtil.toCsvBytes(items);
                case "PDF"   -> ProductExportUtil.toPdfBytes(items);
                default -> throw new IllegalArgumentException("Unsupported export format: " + request.getFormat());
            };
        } catch (Exception e) {
            log.error("Export failed: ", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    // ─── TEMPLATE DOWNLOAD ────────────────────────────────────────────────────────

    @Override
    public byte[] downloadTemplate() {
        try {
            return ProductFileParserUtil.generateExcelTemplate();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate template: " + e.getMessage(), e);
        }
    }

    // ─── IMPORT HISTORY ───────────────────────────────────────────────────────────

    @Override
    public List<ImportHistoryResponse> getImportHistory() {
        UserEntity currentUser = getLoggedInUser();
        return importHistoryRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getTenantId()).stream()
                .map(h -> ImportHistoryResponse.builder()
                        .id(h.getId())
                        .importId(h.getImportId())
                        .importedBy(h.getImportedBy())
                        .fileName(h.getFileName())
                        .fileType(h.getFileType())
                        .totalRecords(h.getTotalRecords())
                        .successCount(h.getSuccessCount())
                        .failedCount(h.getFailedCount())
                        .duplicatesFound(h.getDuplicatesFound())
                        .processingTimeMs(h.getProcessingTimeMs())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    // ─── ZIP IMAGE IMPORT ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int importZipImages(MultipartFile zipFile) {
        int linked = 0;
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory() || !isImageFile(entryName)) continue;

                // Extract SKU/barcode from filename (e.g. LAP001.jpg → SKU=LAP001)
                String baseName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf('/') + 1)
                        : entryName;
                String sku = baseName.substring(0, baseName.lastIndexOf('.'));

                UserEntity currentUser = getLoggedInUser();
                List<ItemEntity> found = itemRepository.findBySkuAndUserId(sku, currentUser.getTenantId());
                if (found.isEmpty()) found = itemRepository.findByBarcodeAndUserId(sku, currentUser.getTenantId());

                if (!found.isEmpty()) {
                    // Read image bytes and store as Base64 data URL (no external upload needed)
                    byte[] imgBytes = zis.readAllBytes();
                    String mimeType = getMimeType(entryName);
                    String dataUrl  = "data:" + mimeType + ";base64," +
                            Base64.getEncoder().encodeToString(imgBytes);

                    ItemEntity item = found.get(0);
                    item.setImgUrl(dataUrl);
                    itemRepository.save(item);
                    linked++;
                    log.info("Linked image {} to SKU {}", entryName, sku);
                } else {
                    log.warn("No product found for image: {}", entryName);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("ZIP image import failed: " + e.getMessage(), e);
        }
        return linked;
    }

    // ─── LEGACY ENDPOINTS ─────────────────────────────────────────────────────────

    @Override
    public ImportSummaryResponse importExcel(MultipartFile file) {
        ImportSummaryResponse preview = previewFile(file);
        ImportConfirmRequest req = ImportConfirmRequest.builder()
                .products(preview.getPreviewItems())
                .duplicateMode("SKIP")
                .build();
        return confirmImport(req);
    }

    @Override
    public ImportSummaryResponse importPdf(MultipartFile file) {
        ImportSummaryResponse preview = previewFile(file);
        ImportConfirmRequest req = ImportConfirmRequest.builder()
                .products(preview.getPreviewItems())
                .duplicateMode("SKIP")
                .build();
        return confirmImport(req);
    }

    // ─── INTERNAL HELPERS ─────────────────────────────────────────────────────────

    private String validateProductDto(ProductImportDTO dto) {
        if (dto.getProductName() == null || dto.getProductName().isBlank())
            return "Product name is required";
        if (dto.getSellingPrice() == null && dto.getPurchasePrice() == null)
            return "At least one price (purchase or selling) is required";
        if (dto.getSellingPrice() != null && dto.getSellingPrice().compareTo(BigDecimal.ZERO) < 0)
            return "Selling price cannot be negative";
        return null;
    }

    private ItemEntity findExistingBySkuOrBarcode(ProductImportDTO dto) {
        UserEntity currentUser = getLoggedInUser();
        if (dto.getSku() != null && !dto.getSku().isBlank()) {
            List<ItemEntity> found = itemRepository.findBySkuAndUserId(dto.getSku(), currentUser.getTenantId());
            if (!found.isEmpty()) return found.get(0);
        }
        if (dto.getBarcode() != null && !dto.getBarcode().isBlank()) {
            List<ItemEntity> found = itemRepository.findByBarcodeAndUserId(dto.getBarcode(), currentUser.getTenantId());
            if (!found.isEmpty()) return found.get(0);
        }
        return null;
    }

    private void updateEntity(ItemEntity existing, ProductImportDTO dto) {
        existing.setName(dto.getProductName());
        existing.setBrand(dto.getBrand());
        existing.setUnit(dto.getUnit());
        existing.setPurchasePrice(dto.getPurchasePrice());
        existing.setSellingPrice(dto.getSellingPrice());
        existing.setPrice(dto.getSellingPrice());
        if (dto.getStockQuantity() != null) existing.setStockQuantity(dto.getStockQuantity());
        existing.setGstPercentage(dto.getGstPercentage());
        if (dto.getDescription() != null && !dto.getDescription().isBlank())
            existing.setDescription(dto.getDescription());
        if (dto.getImageUrl() != null && !dto.getImageUrl().isBlank())
            existing.setImgUrl(dto.getImageUrl());
    }

    private CategoryEntity resolveOrCreateCategory(String categoryName, String userId, Map<String, CategoryEntity> cache) {
        String key = categoryName.trim().toLowerCase() + "_" + userId;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        List<CategoryEntity> found = categoryRepository.findByNameIgnoreCaseAndUserId(categoryName.trim(), userId);
        CategoryEntity cat;
        if (!found.isEmpty()) {
            cat = found.get(0);
        } else {
            CategoryEntity newCat = CategoryEntity.builder()
                    .categoryId(UUID.randomUUID().toString())
                    .name(categoryName.trim())
                    .description("Auto-created during import")
                    .bgColor("#6366f1")
                    .imgUrl(null)
                    .userId(userId)
                    .build();
            cat = categoryRepository.save(newCat);
        }
        
        cache.put(key, cat);
        return cat;
    }

    private ImportRowStatus rowStatus(ProductImportDTO dto, String status, String dupAction, String error) {
        return ImportRowStatus.builder()
                .rowNumber(dto.getRowNumber())
                .name(dto.getProductName())
                .sku(dto.getSku())
                .price(dto.getSellingPrice() != null ? dto.getSellingPrice() : dto.getPurchasePrice())
                .stockQuantity(dto.getStockQuantity())
                .status(status)
                .duplicateAction(dupAction)
                .errorMessage(error)
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String getMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
