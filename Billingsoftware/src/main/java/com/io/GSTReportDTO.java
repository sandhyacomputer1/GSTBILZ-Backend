package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GSTReportDTO {
    private Double gstRate;
    private Double taxableAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double totalGst;
    private Double totalSales;
}
