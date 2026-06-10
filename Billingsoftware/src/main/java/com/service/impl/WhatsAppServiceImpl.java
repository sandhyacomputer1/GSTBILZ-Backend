package com.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.entity.InvoiceEntity;
import com.entity.UserEntity;
import com.entity.WhatsAppMessageLog;
import com.repository.InvoiceEntityRepository;
import com.repository.UserRepository;
import com.repository.WhatsAppMessageLogRepository;
import com.service.InvoiceService;
import com.service.WhatsAppService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppServiceImpl implements WhatsAppService {

    private final InvoiceEntityRepository invoiceRepository;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${whatsapp.api.url:https://graph.facebook.com/v19.0}")
    private String apiUrl;

    @Value("${whatsapp.phone.number.id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access.token:}")
    private String accessToken;

    @Override
    @Async
    public void sendInvoice(String invoiceNumber) {
        sendInvoiceSync(invoiceNumber);
    }

    @Override
    @Async
    public void sendBulkInvoices(List<String> invoiceNumbers) {
        if (invoiceNumbers == null) return;
        for (String number : invoiceNumbers) {
            try {
                sendInvoiceSync(number);
                // Throttle requests slightly during bulk sending
                Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("Bulk sending delay interrupted: " + e.getMessage());
            }
        }
    }

    @Override
    public WhatsAppMessageLog sendInvoiceSync(String invoiceNumber) {
        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        if (invoiceOpt.isEmpty()) {
            throw new IllegalArgumentException("Invoice number " + invoiceNumber + " does not exist.");
        }
        InvoiceEntity invoice = invoiceOpt.get();

        // 1. Verify tenant toggle settings
        Optional<UserEntity> shopOwnerOpt = userRepository.findByUserId(invoice.getUserId());
        if (shopOwnerOpt.isEmpty()) {
            throw new IllegalArgumentException("Shop owner not found for this invoice.");
        }
        UserEntity shopOwner = shopOwnerOpt.get();
        if (!shopOwner.isWhatsappEnabled()) {
            throw new IllegalArgumentException("WhatsApp service is disabled for this store. Please contact Super Admin.");
        }

        // Format customer mobile (e.g. remove spaces, ensure country code)
        String mobile = invoice.getCustomerMobile();
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer mobile number is missing or empty.");
        }
        mobile = mobile.replaceAll("[^0-9]", "");
        // Default to Indian country code if mobile is 10 digits
        if (mobile.length() == 10) {
            mobile = "91" + mobile;
        } else if (mobile.length() < 10) {
            throw new IllegalArgumentException("Invalid customer mobile number: " + invoice.getCustomerMobile());
        }

        // Create pending log first so we record that we attempted to send
        WhatsAppMessageLog log = WhatsAppMessageLog.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerMobile(invoice.getCustomerMobile())
                .status("PENDING")
                .sentTime(LocalDateTime.now())
                .pdfUrl(invoice.getPdfUrl())
                .userId(invoice.getUserId())
                .build();
        log = messageLogRepository.save(log);

        // 2. Check if configuration is missing (use sandbox / mock mode)
        boolean isMockMode = accessToken == null || accessToken.isBlank() || accessToken.equals("YOUR_ACCESS_TOKEN")
                || phoneNumberId == null || phoneNumberId.isBlank() || phoneNumberId.equals("YOUR_PHONE_NUMBER_ID");

        if (isMockMode) {
            System.out.println("WhatsApp API operating in Mock Mode for: " + mobile);
            log.setStatus("SENT (MOCKED)");
            return messageLogRepository.save(log);
        }

        try {
            // 3. Generate the PDF bytes
            byte[] pdfBytes = invoiceService.getInvoicePdfBytes(invoiceNumber);

            // 4. Upload PDF to WhatsApp Media API
            String mediaId = uploadPdfToWhatsApp(pdfBytes, invoiceNumber);

            // 5. Send PDF document via WhatsApp Messages API using the media ID
            String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            // Escape double quotes in customer name
            String escapedCustomerName = invoice.getCustomerName() != null ? invoice.getCustomerName().replace("\"", "\\\"") : "Customer";

            String jsonPayload = String.format(
                "{\n" +
                "  \"messaging_product\": \"whatsapp\",\n" +
                "  \"recipient_type\": \"individual\",\n" +
                "  \"to\": \"%s\",\n" +
                "  \"type\": \"document\",\n" +
                "  \"document\": {\n" +
                "    \"id\": \"%s\",\n" +
                "    \"filename\": \"Invoice_%s.pdf\",\n" +
                "    \"caption\": \"Hi %s, please find your invoice %s for total amount INR %.2f attached. Thank you!\"\n" +
                "  }\n" +
                "}",
                mobile, mediaId, invoice.getInvoiceNumber(),
                escapedCustomerName, invoice.getInvoiceNumber(), invoice.getTotalAmount()
            );

            HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;
            String lastError = null;

            while (attempt < maxRetries && !success) {
                attempt++;
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        success = true;
                    } else {
                        lastError = "Response code: " + response.getStatusCode() + " - " + response.getBody();
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (success) {
                log.setStatus("SENT");
                log.setErrorMessage(null);
            } else {
                log.setStatus("FAILED");
                log.setErrorMessage(truncateErrorMessage(lastError));
            }
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(truncateErrorMessage(e.getMessage()));
        }

        return messageLogRepository.save(log);
    }

    private String truncateErrorMessage(String error) {
        if (error == null) return null;
        return error.length() > 250 ? error.substring(0, 250) : error;
    }

    private String uploadPdfToWhatsApp(byte[] pdfBytes, String invoiceNumber) {
        String url = String.format("%s/%s/media", apiUrl, phoneNumberId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(accessToken);

        ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "Invoice_" + invoiceNumber + ".pdf";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(fileResource, fileHeaders);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", "application/pdf");
        body.add("file", fileEntity);

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object idObj = response.getBody().get("id");
                if (idObj != null) {
                    return idObj.toString();
                }
            }
            throw new RuntimeException("Media upload response did not return a valid ID: " + response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload PDF to WhatsApp Media API: " + e.getMessage(), e);
        }
    }
}
