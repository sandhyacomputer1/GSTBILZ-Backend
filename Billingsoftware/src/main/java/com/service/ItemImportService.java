package com.service;

import com.io.ExportRequest;
import com.io.ImportConfirmRequest;
import com.io.ImportHistoryResponse;
import com.io.ImportSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ItemImportService {

    // ─── Import Phase 1: Parse & Preview (no DB write) ──────────────────────────
    ImportSummaryResponse previewFile(MultipartFile file);

    // ─── Import Phase 2: Confirm & Batch Insert ──────────────────────────────────
    ImportSummaryResponse confirmImport(ImportConfirmRequest request);

    // ─── Export ──────────────────────────────────────────────────────────────────
    byte[] exportProducts(ExportRequest request);

    // ─── Template download ────────────────────────────────────────────────────────
    byte[] downloadTemplate();

    // ─── Import History ───────────────────────────────────────────────────────────
    List<ImportHistoryResponse> getImportHistory();

    // ─── Bulk ZIP Image Import ────────────────────────────────────────────────────
    int importZipImages(MultipartFile zipFile);

    // ─── Legacy endpoints (kept for backward compatibility) ───────────────────────
    ImportSummaryResponse importExcel(MultipartFile file);
    ImportSummaryResponse importPdf(MultipartFile file);
}
