package com.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.io.BulkWhatsAppRequest;
import com.service.WhatsAppService;
import com.entity.WhatsAppMessageLog;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final WhatsAppService whatsAppService;

    @PostMapping("/{id}/send-whatsapp")
    public ResponseEntity<?> sendWhatsApp(@PathVariable("id") String id) {
        try {
            WhatsAppMessageLog log = whatsAppService.sendInvoiceSync(id);
            if ("SENT".equals(log.getStatus()) || "SENT (MOCKED)".equals(log.getStatus())) {
                return ResponseEntity.ok(Map.of("status", log.getStatus(), "message", "Invoice sent on WhatsApp successfully!"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", log.getStatus(), "message", log.getErrorMessage() != null ? log.getErrorMessage() : "Failed to send invoice on WhatsApp."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "FAILED", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "FAILED", "message", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @PostMapping("/send-bulk-whatsapp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendBulkWhatsApp(@RequestBody BulkWhatsAppRequest request) {
        whatsAppService.sendBulkInvoices(request.getInvoiceNumbers());
    }
}
