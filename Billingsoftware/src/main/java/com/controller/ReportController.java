package com.controller;
 
import java.time.LocalDate;
import java.util.List;
 
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import com.io.*;
import com.service.ReportService;
 
import lombok.RequiredArgsConstructor;
 
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
 
    private final ReportService reportService;
 
    @GetMapping("/daily")
    public List<DailySalesReportDTO> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getDailySalesReport(startDate, endDate);
    }
 
    @GetMapping("/weekly")
    public List<WeeklySalesReportDTO> getWeeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getWeeklySalesReport(startDate, endDate);
    }
 
    @GetMapping("/monthly")
    public List<MonthlySalesReportDTO> getMonthlyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getMonthlySalesReport(startDate, endDate);
    }
 
    @GetMapping("/yearly")
    public List<YearlySalesReportDTO> getYearlyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getYearlySalesReport(startDate, endDate);
    }
 
    @GetMapping("/gst")
    public List<GSTReportDTO> getGSTReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getGSTReport(startDate, endDate);
    }
 
    @GetMapping("/profit-loss")
    public List<ProfitLossReportDTO> getProfitLossReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getProfitLossReport(startDate, endDate);
    }
 
    @GetMapping("/best-selling")
    public List<BestSellingProductDTO> getBestSelling(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.getBestSellingProducts(startDate, endDate, limit);
    }
 
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws Exception {
        
        byte[] data = reportService.exportToExcel(type, startDate, endDate);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", type + "-report.xlsx");
        
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
 
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws Exception {
        
        byte[] data = reportService.exportToPdf(type, startDate, endDate);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", type + "-report.pdf");
        
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
