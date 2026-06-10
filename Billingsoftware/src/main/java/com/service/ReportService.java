package com.service;
 
import java.time.LocalDate;
import java.util.List;
 
import com.io.*;
 
public interface ReportService {
 
    List<DailySalesReportDTO> getDailySalesReport(LocalDate startDate, LocalDate endDate);
 
    List<WeeklySalesReportDTO> getWeeklySalesReport(LocalDate startDate, LocalDate endDate);
 
    List<MonthlySalesReportDTO> getMonthlySalesReport(LocalDate startDate, LocalDate endDate);
 
    List<YearlySalesReportDTO> getYearlySalesReport(LocalDate startDate, LocalDate endDate);
 
    List<GSTReportDTO> getGSTReport(LocalDate startDate, LocalDate endDate);
 
    List<ProfitLossReportDTO> getProfitLossReport(LocalDate startDate, LocalDate endDate);
 
    List<BestSellingProductDTO> getBestSellingProducts(LocalDate startDate, LocalDate endDate, int limit);
 
    byte[] exportToExcel(String reportType, LocalDate startDate, LocalDate endDate) throws Exception;
 
    byte[] exportToPdf(String reportType, LocalDate startDate, LocalDate endDate) throws Exception;
}
