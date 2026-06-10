package com.service;

import com.entity.InvoiceEntity;
import com.entity.OrderEntity;

public interface InvoiceService {
    InvoiceEntity generateAndSaveInvoice(OrderEntity order);
    InvoiceEntity getInvoiceByNumber(String invoiceNumber);
    InvoiceEntity getInvoiceByOrderId(String orderId);
    byte[] getInvoicePdfBytes(String invoiceNumber);
}
