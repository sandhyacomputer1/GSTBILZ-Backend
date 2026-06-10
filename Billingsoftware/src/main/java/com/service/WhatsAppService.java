package com.service;

import java.util.List;

public interface WhatsAppService {
    void sendInvoice(String invoiceNumber);
    com.entity.WhatsAppMessageLog sendInvoiceSync(String invoiceNumber);
    void sendBulkInvoices(List<String> invoiceNumbers);
}
