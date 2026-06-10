package com.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_whatsapp_message_logs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhatsAppMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceNumber;
    private String customerMobile;
    private String status; // SENT, DELIVERED, FAILED
    private LocalDateTime sentTime;
    
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    private String pdfUrl;
    private String userId; // tenant ID
}
