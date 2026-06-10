package com.service.impl;
 
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;
 
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
 
import com.io.*;
import com.repository.*;
import com.service.ReportService;
import com.entity.UserEntity;
import com.repository.UserRepository;
 
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
 
import lombok.RequiredArgsConstructor;
 
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
 
    private final SalesReportRepository salesReportRepository;
    private final ProfitLossReportRepository profitLossReportRepository;
    private final BestSellingProductRepository bestSellingProductRepository;
    private final UserRepository userRepository;
 
    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
 
    private Double getDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
 
    private Long getLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
 
    @Override
    public List<DailySalesReportDTO> getDailySalesReport(LocalDate startDate, LocalDate endDate) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = salesReportRepository.getDailySalesReport(user.getTenantId(), startDate, endDate);
        List<DailySalesReportDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            LocalDate date = null;
            if (row[0] != null) {
                if (row[0] instanceof java.sql.Date) {
                    date = ((java.sql.Date) row[0]).toLocalDate();
                } else if (row[0] instanceof LocalDate) {
                    date = (LocalDate) row[0];
                } else {
                    date = LocalDate.parse(row[0].toString());
                }
            }
            dtos.add(DailySalesReportDTO.builder()
                    .date(date)
                    .totalSales(getDouble(row[1]))
                    .totalOrders(getLong(row[2]))
                    .totalTax(getDouble(row[3]))
                    .totalDiscount(getDouble(row[4]))
                    .build());
        }
        return dtos;
    }
 
    @Override
    public List<WeeklySalesReportDTO> getWeeklySalesReport(LocalDate startDate, LocalDate endDate) {
        List<DailySalesReportDTO> dailyReports = getDailySalesReport(startDate, endDate);
        
        // Group daily reports by week range
        Map<String, List<DailySalesReportDTO>> grouped = new LinkedHashMap<>();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM dd");
 
        for (DailySalesReportDTO daily : dailyReports) {
            if (daily.getDate() == null) continue;
            LocalDate date = daily.getDate();
            int weekNum = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());
            
            // Calculate start and end of week
            LocalDate firstDayOfWeek = date.with(weekFields.dayOfWeek(), 1);
            LocalDate lastDayOfWeek = date.with(weekFields.dayOfWeek(), 7);
            
            String weekRange = String.format("Year %d - Week %d (%s - %s)", 
                    year, weekNum, firstDayOfWeek.format(dayFormatter), lastDayOfWeek.format(dayFormatter));
            
            grouped.computeIfAbsent(weekRange, k -> new ArrayList<>()).add(daily);
        }
 
        List<WeeklySalesReportDTO> weeklyReports = new ArrayList<>();
        for (Map.Entry<String, List<DailySalesReportDTO>> entry : grouped.entrySet()) {
            double totalSales = 0.0;
            long totalOrders = 0;
            double totalTax = 0.0;
            double totalDiscount = 0.0;
 
            for (DailySalesReportDTO daily : entry.getValue()) {
                totalSales += daily.getTotalSales();
                totalOrders += daily.getTotalOrders();
                totalTax += daily.getTotalTax();
                totalDiscount += daily.getTotalDiscount();
            }
 
            weeklyReports.add(WeeklySalesReportDTO.builder()
                    .weekRange(entry.getKey())
                    .totalSales(totalSales)
                    .totalOrders(totalOrders)
                    .totalTax(totalTax)
                    .totalDiscount(totalDiscount)
                    .build());
        }
 
        return weeklyReports;
    }
 
    @Override
    public List<MonthlySalesReportDTO> getMonthlySalesReport(LocalDate startDate, LocalDate endDate) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = salesReportRepository.getMonthlySalesReport(user.getTenantId(), startDate, endDate);
        List<MonthlySalesReportDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(MonthlySalesReportDTO.builder()
                    .month(row[0] != null ? row[0].toString() : "")
                    .totalSales(getDouble(row[1]))
                    .totalOrders(getLong(row[2]))
                    .totalTax(getDouble(row[3]))
                    .totalDiscount(getDouble(row[4]))
                    .build());
        }
        return dtos;
    }
 
    @Override
    public List<YearlySalesReportDTO> getYearlySalesReport(LocalDate startDate, LocalDate endDate) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = salesReportRepository.getYearlySalesReport(user.getTenantId(), startDate, endDate);
        List<YearlySalesReportDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(YearlySalesReportDTO.builder()
                    .year(row[0] != null ? getLong(row[0]).intValue() : 0)
                    .totalSales(getDouble(row[1]))
                    .totalOrders(getLong(row[2]))
                    .totalTax(getDouble(row[3]))
                    .totalDiscount(getDouble(row[4]))
                    .build());
        }
        return dtos;
    }
 
    @Override
    public List<GSTReportDTO> getGSTReport(LocalDate startDate, LocalDate endDate) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = salesReportRepository.getGSTReport(user.getTenantId(), startDate, endDate);
        List<GSTReportDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(GSTReportDTO.builder()
                    .gstRate(getDouble(row[0]))
                    .taxableAmount(getDouble(row[1]))
                    .cgstAmount(getDouble(row[2]))
                    .sgstAmount(getDouble(row[3]))
                    .totalGst(getDouble(row[4]))
                    .totalSales(getDouble(row[5]))
                    .build());
        }
        return dtos;
    }
 
    @Override
    public List<ProfitLossReportDTO> getProfitLossReport(LocalDate startDate, LocalDate endDate) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = profitLossReportRepository.getProfitLossReport(user.getTenantId(), startDate, endDate);
        List<ProfitLossReportDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            double revenue = getDouble(row[1]);
            double cost = getDouble(row[2]);
            double profit = revenue > cost ? revenue - cost : 0.0;
            double loss = cost > revenue ? cost - revenue : 0.0;
 
            dtos.add(ProfitLossReportDTO.builder()
                    .reportPeriod(row[0] != null ? row[0].toString() : "")
                    .totalRevenue(revenue)
                    .totalCost(cost)
                    .totalProfit(profit)
                    .totalLoss(loss)
                    .build());
        }
        return dtos;
    }
 
    @Override
    public List<BestSellingProductDTO> getBestSellingProducts(LocalDate startDate, LocalDate endDate, int limit) {
        UserEntity user = getLoggedInUser();
        List<Object[]> results = bestSellingProductRepository.getBestSellingProducts(user.getTenantId(), startDate, endDate, limit);
        List<BestSellingProductDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(BestSellingProductDTO.builder()
                    .productId(row[0] != null ? row[0].toString() : "")
                    .productName(row[1] != null ? row[1].toString() : "")
                    .quantitySold(getLong(row[2]))
                    .totalRevenue(getDouble(row[3]))
                    .build());
        }
        return dtos;
    }
 
    @Override
    public byte[] exportToExcel(String reportType, LocalDate startDate, LocalDate endDate) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(reportType + " Report");
 
        // Define premium table styling
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
 
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
 
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setAlignment(HorizontalAlignment.LEFT);
 
        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
 
        if ("daily".equalsIgnoreCase(reportType)) {
            String[] headers = {"Date", "Total Sales", "Total Orders", "Total Tax (GST)", "Total Discount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (DailySalesReportDTO dto : getDailySalesReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getDate() != null ? dto.getDate().toString() : "");
                row.createCell(1).setCellValue(dto.getTotalSales());
                row.createCell(2).setCellValue(dto.getTotalOrders());
                row.createCell(3).setCellValue(dto.getTotalTax());
                row.createCell(4).setCellValue(dto.getTotalDiscount());
            }
        } else if ("monthly".equalsIgnoreCase(reportType)) {
            String[] headers = {"Month", "Total Sales", "Total Orders", "Total Tax (GST)", "Total Discount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (MonthlySalesReportDTO dto : getMonthlySalesReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getMonth());
                row.createCell(1).setCellValue(dto.getTotalSales());
                row.createCell(2).setCellValue(dto.getTotalOrders());
                row.createCell(3).setCellValue(dto.getTotalTax());
                row.createCell(4).setCellValue(dto.getTotalDiscount());
            }
        } else if ("gst".equalsIgnoreCase(reportType)) {
            String[] headers = {"GST Rate (%)", "Taxable Amount", "CGST Amount", "SGST Amount", "Total GST", "Total Sales"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (GSTReportDTO dto : getGSTReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getGstRate());
                row.createCell(1).setCellValue(dto.getTaxableAmount());
                row.createCell(2).setCellValue(dto.getCgstAmount());
                row.createCell(3).setCellValue(dto.getSgstAmount());
                row.createCell(4).setCellValue(dto.getTotalGst());
                row.createCell(5).setCellValue(dto.getTotalSales());
            }
        } else if ("profit-loss".equalsIgnoreCase(reportType)) {
            String[] headers = {"Period", "Total Revenue", "Total Cost (COGS)", "Total Profit", "Total Loss"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (ProfitLossReportDTO dto : getProfitLossReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getReportPeriod());
                row.createCell(1).setCellValue(dto.getTotalRevenue());
                row.createCell(2).setCellValue(dto.getTotalCost());
                row.createCell(3).setCellValue(dto.getTotalProfit());
                row.createCell(4).setCellValue(dto.getTotalLoss());
            }
        } else if ("best-selling".equalsIgnoreCase(reportType)) {
            String[] headers = {"Product ID", "Product Name", "Quantity Sold", "Total Revenue"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (BestSellingProductDTO dto : getBestSellingProducts(startDate, endDate, 20)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getProductId());
                row.createCell(1).setCellValue(dto.getProductName());
                row.createCell(2).setCellValue(dto.getQuantitySold());
                row.createCell(3).setCellValue(dto.getTotalRevenue());
            }
        }
 
        // Auto-fit column widths
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            sheet.autoSizeColumn(i);
        }
 
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        workbook.close();
        return bos.toByteArray();
    }
 
    @Override
    public byte[] exportToPdf(String reportType, LocalDate startDate, LocalDate endDate) throws Exception {
        Document document = new Document();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, bos);
        document.open();
 
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Font subTitleFont = new Font(Font.FontFamily.HELVETICA, 12, Font.ITALIC);
        Font boldFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
 
        // Header Title
        Paragraph title = new Paragraph(reportType.toUpperCase() + " SALES REPORT", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
 
        Paragraph subtitle = new Paragraph("Generated on: " + LocalDate.now().toString() +
                (startDate != null ? " | Filter: " + startDate.toString() + " to " + endDate.toString() : ""), subTitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);
 
        if ("daily".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("Date", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total Sales", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total Orders", boldFont)));
            table.addCell(new PdfPCell(new Phrase("GST Tax", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Discount", boldFont)));
 
            for (DailySalesReportDTO dto : getDailySalesReport(startDate, endDate)) {
                table.addCell(dto.getDate() != null ? dto.getDate().toString() : "");
                table.addCell(String.format("%.2f", dto.getTotalSales()));
                table.addCell(String.valueOf(dto.getTotalOrders()));
                table.addCell(String.format("%.2f", dto.getTotalTax()));
                table.addCell(String.format("%.2f", dto.getTotalDiscount()));
            }
            document.add(table);
        } else if ("monthly".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("Month", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total Sales", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total Orders", boldFont)));
            table.addCell(new PdfPCell(new Phrase("GST Tax", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Discount", boldFont)));
 
            for (MonthlySalesReportDTO dto : getMonthlySalesReport(startDate, endDate)) {
                table.addCell(dto.getMonth());
                table.addCell(String.format("%.2f", dto.getTotalSales()));
                table.addCell(String.valueOf(dto.getTotalOrders()));
                table.addCell(String.format("%.2f", dto.getTotalTax()));
                table.addCell(String.format("%.2f", dto.getTotalDiscount()));
            }
            document.add(table);
        } else if ("gst".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("GST Rate (%)", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Taxable Amt", boldFont)));
            table.addCell(new PdfPCell(new Phrase("CGST", boldFont)));
            table.addCell(new PdfPCell(new Phrase("SGST", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total GST", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Total Sales", boldFont)));
 
            for (GSTReportDTO dto : getGSTReport(startDate, endDate)) {
                table.addCell(String.format("%.1f%%", dto.getGstRate()));
                table.addCell(String.format("%.2f", dto.getTaxableAmount()));
                table.addCell(String.format("%.2f", dto.getCgstAmount()));
                table.addCell(String.format("%.2f", dto.getSgstAmount()));
                table.addCell(String.format("%.2f", dto.getTotalGst()));
                table.addCell(String.format("%.2f", dto.getTotalSales()));
            }
            document.add(table);
        } else if ("profit-loss".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("Period", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Revenue", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Cost (COGS)", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Profit", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Loss", boldFont)));
 
            for (ProfitLossReportDTO dto : getProfitLossReport(startDate, endDate)) {
                table.addCell(dto.getReportPeriod());
                table.addCell(String.format("%.2f", dto.getTotalRevenue()));
                table.addCell(String.format("%.2f", dto.getTotalCost()));
                table.addCell(String.format("%.2f", dto.getTotalProfit()));
                table.addCell(String.format("%.2f", dto.getTotalLoss()));
            }
            document.add(table);
        } else if ("best-selling".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("Product ID", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Product Name", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Qty Sold", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Revenue", boldFont)));
 
            for (BestSellingProductDTO dto : getBestSellingProducts(startDate, endDate, 20)) {
                table.addCell(dto.getProductId());
                table.addCell(dto.getProductName());
                table.addCell(String.valueOf(dto.getQuantitySold()));
                table.addCell(String.format("%.2f", dto.getTotalRevenue()));
            }
            document.add(table);
        }
 
        document.close();
        return bos.toByteArray();
    }
}
