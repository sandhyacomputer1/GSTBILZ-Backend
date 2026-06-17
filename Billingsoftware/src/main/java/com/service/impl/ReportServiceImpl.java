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
import com.entity.OrderEntity;
import com.entity.OrderItemEntity;
import com.repository.UserRepository;
import com.repository.OrderEntityRepository;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.BaseColor;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
 
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
 
    private final SalesReportRepository salesReportRepository;
    private final ProfitLossReportRepository profitLossReportRepository;
    private final BestSellingProductRepository bestSellingProductRepository;
    private final UserRepository userRepository;
    private final OrderEntityRepository orderEntityRepository;
 
    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private UserEntity getShopOwner(UserEntity loggedInUser) {
        if ("ROLE_EMPLOYEE".equals(loggedInUser.getRole()) && loggedInUser.getShopOwnerId() != null) {
            return userRepository.findByUserId(loggedInUser.getShopOwnerId()).orElse(loggedInUser);
        }
        return loggedInUser;
    }

    private byte[] downloadImageBytes(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return null;
        }
        try {
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                java.net.URL url = new java.net.URL(urlString);
                try (java.io.InputStream in = url.openStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                    return out.toByteArray();
                }
            } else {
                java.io.File file = new java.io.File(urlString);
                if (file.exists()) {
                    return java.nio.file.Files.readAllBytes(file.toPath());
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading image bytes from " + urlString + ": " + e.getMessage());
        }
        return null;
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

        UserEntity user = getLoggedInUser();
        UserEntity shopOwner = getShopOwner(user);
        String shopName = shopOwner.getShopName() != null ? shopOwner.getShopName() : "Billing System";

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

        CellStyle rightAlignedStyle = workbook.createCellStyle();
        rightAlignedStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle totalStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);
        totalStyle.setBorderTop(BorderStyle.THIN);
        totalStyle.setBorderBottom(BorderStyle.DOUBLE);

        CellStyle rightAlignedTotalStyle = workbook.createCellStyle();
        rightAlignedTotalStyle.setFont(totalFont);
        rightAlignedTotalStyle.setAlignment(HorizontalAlignment.RIGHT);
        rightAlignedTotalStyle.setBorderTop(BorderStyle.THIN);
        rightAlignedTotalStyle.setBorderBottom(BorderStyle.DOUBLE);

        // Branding
        Row brandingRow = sheet.createRow(0);
        Cell shopNameCell = brandingRow.createCell(2);
        shopNameCell.setCellValue(shopName);
        org.apache.poi.ss.usermodel.Font shopFont = workbook.createFont();
        shopFont.setBold(true);
        shopFont.setFontHeightInPoints((short) 16);
        CellStyle shopStyle = workbook.createCellStyle();
        shopStyle.setFont(shopFont);
        shopNameCell.setCellStyle(shopStyle);

        Row timeRow = sheet.createRow(1);
        Cell timeCell = timeRow.createCell(2);
        timeCell.setCellValue("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        Row filterRow = sheet.createRow(2);
        Cell filterCell = filterRow.createCell(2);
        filterCell.setCellValue("Filter: " + (startDate != null ? startDate.toString() : "All") + " to " + (endDate != null ? endDate.toString() : "All"));

        byte[] logoBytes = downloadImageBytes(shopOwner.getShopLogoUrl());
        if (logoBytes != null) {
            try {
                int pictureIdx = workbook.addPicture(logoBytes, Workbook.PICTURE_TYPE_PNG);
                CreationHelper helper = workbook.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(0);
                anchor.setRow1(0);
                anchor.setCol2(2);
                anchor.setRow2(3);
                drawing.createPicture(anchor, pictureIdx);
            } catch (Exception e) {
                System.err.println("Failed to insert logo into Excel: " + e.getMessage());
            }
        } else {
            Row fallbackRow = sheet.getRow(0);
            if (fallbackRow == null) fallbackRow = sheet.createRow(0);
            Cell nameCell = fallbackRow.createCell(0);
            nameCell.setCellValue(shopName);
            nameCell.setCellStyle(shopStyle);
        }

        int rowIdx = 5;
        Row headerRow = sheet.createRow(rowIdx++);

        if ("daily".equalsIgnoreCase(reportType) || "weekly".equalsIgnoreCase(reportType) || 
            "monthly".equalsIgnoreCase(reportType) || "yearly".equalsIgnoreCase(reportType)) {
            
            String[] headers = {"Date & Time", "Order ID", "Customer Name", "Purchased Items", "Discount", "GST Tax", "Total Amount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<OrderEntity> orders = orderEntityRepository.findOrdersByDateRange(user.getTenantId(), startDate, endDate);
            double totalSales = 0.0;
            double totalTax = 0.0;
            double totalDiscount = 0.0;

            for (OrderEntity o : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(o.getCreatedAt() != null ? o.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
                row.createCell(1).setCellValue(o.getOrderId() != null ? o.getOrderId() : "");
                row.createCell(2).setCellValue(o.getCustomerName() != null ? o.getCustomerName() : "N/A");
                
                String itemsText = o.getItem().stream()
                        .map(oi -> oi.getName() + " (x" + oi.getQuantity() + ")")
                        .collect(Collectors.joining(", "));
                row.createCell(3).setCellValue(itemsText);
                
                Cell discCell = row.createCell(4);
                discCell.setCellValue(o.getDiscount() != null ? o.getDiscount() : 0.0);
                discCell.setCellStyle(rightAlignedStyle);
                
                Cell gstCell = row.createCell(5);
                gstCell.setCellValue(o.getGstAmount() != null ? o.getGstAmount() : 0.0);
                gstCell.setCellStyle(rightAlignedStyle);
                
                Cell totCell = row.createCell(6);
                totCell.setCellValue(o.getGrandTotal() != null ? o.getGrandTotal() : 0.0);
                totCell.setCellStyle(rightAlignedStyle);

                totalSales += (o.getGrandTotal() != null ? o.getGrandTotal() : 0.0);
                totalTax += (o.getGstAmount() != null ? o.getGstAmount() : 0.0);
                totalDiscount += (o.getDiscount() != null ? o.getDiscount() : 0.0);
            }

            // Summary Row
            Row summaryRow = sheet.createRow(rowIdx++);
            Cell labelCell = summaryRow.createCell(0);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(totalStyle);
            
            Cell countCell = summaryRow.createCell(1);
            countCell.setCellValue(orders.size() + " Orders");
            countCell.setCellStyle(totalStyle);

            for (int i = 2; i <= 3; i++) {
                summaryRow.createCell(i).setCellStyle(totalStyle);
            }

            Cell totDiscCell = summaryRow.createCell(4);
            totDiscCell.setCellValue(totalDiscount);
            totDiscCell.setCellStyle(rightAlignedTotalStyle);

            Cell totGstCell = summaryRow.createCell(5);
            totGstCell.setCellValue(totalTax);
            totGstCell.setCellStyle(rightAlignedTotalStyle);

            Cell totSalesCell = summaryRow.createCell(6);
            totSalesCell.setCellValue(totalSales);
            totSalesCell.setCellStyle(rightAlignedTotalStyle);

        } else if ("gst".equalsIgnoreCase(reportType)) {
            String[] headers = {"GST Rate (%)", "Taxable Amount", "CGST Amount", "SGST Amount", "Total GST", "Total Sales"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            double totalTaxable = 0.0;
            double totalCGST = 0.0;
            double totalSGST = 0.0;
            double totalGst = 0.0;
            double totalSales = 0.0;

            for (GSTReportDTO dto : getGSTReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getGstRate());
                
                Cell cell1 = row.createCell(1); cell1.setCellValue(dto.getTaxableAmount()); cell1.setCellStyle(rightAlignedStyle);
                Cell cell2 = row.createCell(2); cell2.setCellValue(dto.getCgstAmount()); cell2.setCellStyle(rightAlignedStyle);
                Cell cell3 = row.createCell(3); cell3.setCellValue(dto.getSgstAmount()); cell3.setCellStyle(rightAlignedStyle);
                Cell cell4 = row.createCell(4); cell4.setCellValue(dto.getTotalGst()); cell4.setCellStyle(rightAlignedStyle);
                Cell cell5 = row.createCell(5); cell5.setCellValue(dto.getTotalSales()); cell5.setCellStyle(rightAlignedStyle);

                totalTaxable += dto.getTaxableAmount();
                totalCGST += dto.getCgstAmount();
                totalSGST += dto.getSgstAmount();
                totalGst += dto.getTotalGst();
                totalSales += dto.getTotalSales();
            }

            Row summaryRow = sheet.createRow(rowIdx++);
            Cell labelCell = summaryRow.createCell(0);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(totalStyle);

            Cell sum1 = summaryRow.createCell(1); sum1.setCellValue(totalTaxable); sum1.setCellStyle(rightAlignedTotalStyle);
            Cell sum2 = summaryRow.createCell(2); sum2.setCellValue(totalCGST); sum2.setCellStyle(rightAlignedTotalStyle);
            Cell sum3 = summaryRow.createCell(3); sum3.setCellValue(totalSGST); sum3.setCellStyle(rightAlignedTotalStyle);
            Cell sum4 = summaryRow.createCell(4); sum4.setCellValue(totalGst); sum4.setCellStyle(rightAlignedTotalStyle);
            Cell sum5 = summaryRow.createCell(5); sum5.setCellValue(totalSales); sum5.setCellStyle(rightAlignedTotalStyle);

        } else if ("profit-loss".equalsIgnoreCase(reportType)) {
            String[] headers = {"Period", "Total Revenue", "Total Cost (COGS)", "Total Profit", "Total Loss"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            double totalRevenue = 0.0;
            double totalCost = 0.0;
            double totalProfit = 0.0;
            double totalLoss = 0.0;

            for (ProfitLossReportDTO dto : getProfitLossReport(startDate, endDate)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getReportPeriod());
                
                Cell cell1 = row.createCell(1); cell1.setCellValue(dto.getTotalRevenue()); cell1.setCellStyle(rightAlignedStyle);
                Cell cell2 = row.createCell(2); cell2.setCellValue(dto.getTotalCost()); cell2.setCellStyle(rightAlignedStyle);
                Cell cell3 = row.createCell(3); cell3.setCellValue(dto.getTotalProfit()); cell3.setCellStyle(rightAlignedStyle);
                Cell cell4 = row.createCell(4); cell4.setCellValue(dto.getTotalLoss()); cell4.setCellStyle(rightAlignedStyle);

                totalRevenue += dto.getTotalRevenue();
                totalCost += dto.getTotalCost();
                totalProfit += dto.getTotalProfit();
                totalLoss += dto.getTotalLoss();
            }

            Row summaryRow = sheet.createRow(rowIdx++);
            Cell labelCell = summaryRow.createCell(0);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(totalStyle);

            Cell sum1 = summaryRow.createCell(1); sum1.setCellValue(totalRevenue); sum1.setCellStyle(rightAlignedTotalStyle);
            Cell sum2 = summaryRow.createCell(2); sum2.setCellValue(totalCost); sum2.setCellStyle(rightAlignedTotalStyle);
            Cell sum3 = summaryRow.createCell(3); sum3.setCellValue(totalProfit); sum3.setCellStyle(rightAlignedTotalStyle);
            Cell sum4 = summaryRow.createCell(4); sum4.setCellValue(totalLoss); sum4.setCellStyle(rightAlignedTotalStyle);

        } else if ("best-selling".equalsIgnoreCase(reportType)) {
            String[] headers = {"Product ID", "Product Name", "Quantity Sold", "Total Revenue"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            long totalQty = 0;
            double totalRevenue = 0.0;

            for (BestSellingProductDTO dto : getBestSellingProducts(startDate, endDate, 20)) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getProductId());
                row.createCell(1).setCellValue(dto.getProductName());
                
                Cell cell2 = row.createCell(2); cell2.setCellValue(dto.getQuantitySold()); cell2.setCellStyle(rightAlignedStyle);
                Cell cell3 = row.createCell(3); cell3.setCellValue(dto.getTotalRevenue()); cell3.setCellStyle(rightAlignedStyle);

                totalQty += dto.getQuantitySold();
                totalRevenue += dto.getTotalRevenue();
            }

            Row summaryRow = sheet.createRow(rowIdx++);
            Cell labelCell = summaryRow.createCell(0);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(totalStyle);

            summaryRow.createCell(1).setCellStyle(totalStyle);

            Cell sum2 = summaryRow.createCell(2); sum2.setCellValue(totalQty); sum2.setCellStyle(rightAlignedTotalStyle);
            Cell sum3 = summaryRow.createCell(3); sum3.setCellValue(totalRevenue); sum3.setCellStyle(rightAlignedTotalStyle);
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
 
        UserEntity user = getLoggedInUser();
        UserEntity shopOwner = getShopOwner(user);
        String shopName = shopOwner.getShopName() != null ? shopOwner.getShopName() : "Billing System";
        String shopLogoUrl = shopOwner.getShopLogoUrl();

        Font shopFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.BLACK);
        Font metaFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.DARK_GRAY);
        Font headerStyleFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        Font dataFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.BLACK);
        Font itemsFont = new Font(Font.FontFamily.HELVETICA, 7, Font.NORMAL, BaseColor.BLACK);
        Font totalFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.BLACK);
 
        BaseColor primaryColor = new BaseColor(0, 102, 204); // Royal Blue

        // Header Table for Logo and Shop Name
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[] { 15, 85 });
        headerTable.setSpacingAfter(20);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        boolean logoLoaded = false;
        if (shopLogoUrl != null && !shopLogoUrl.isBlank()) {
            try {
                Image img = Image.getInstance(shopLogoUrl);
                img.scaleToFit(60, 60);
                logoCell.addElement(img);
                logoLoaded = true;
            } catch (Exception ex) {
                System.err.println("Could not load shop logo in PDF: " + ex.getMessage());
            }
        }
        if (!logoLoaded) {
            logoCell.addElement(new Paragraph(""));
        }
        headerTable.addCell(logoCell);

        PdfPCell detailsCell = new PdfPCell();
        detailsCell.setBorder(Rectangle.NO_BORDER);
        detailsCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph shopNamePara = new Paragraph(shopName, shopFont);
        Paragraph metaPara = new Paragraph(
                "Report Type: " + reportType.toUpperCase() + " REPORT\n" +
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                "Filter: " + (startDate != null ? startDate.toString() : "All") + " to " + (endDate != null ? endDate.toString() : "All"),
                metaFont
        );
        detailsCell.addElement(shopNamePara);
        detailsCell.addElement(metaPara);
        headerTable.addCell(detailsCell);
        
        document.add(headerTable);
 
        if ("daily".equalsIgnoreCase(reportType) || "weekly".equalsIgnoreCase(reportType) || 
            "monthly".equalsIgnoreCase(reportType) || "yearly".equalsIgnoreCase(reportType)) {
            
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 90f, 80f, 90f, 120f, 45f, 45f, 53f });
 
            addHeaderCell(table, "Date & Time", headerStyleFont, primaryColor);
            addHeaderCell(table, "Order ID", headerStyleFont, primaryColor);
            addHeaderCell(table, "Customer Name", headerStyleFont, primaryColor);
            addHeaderCell(table, "Purchased Items", headerStyleFont, primaryColor);
            addHeaderCell(table, "Discount", headerStyleFont, primaryColor);
            addHeaderCell(table, "GST Tax", headerStyleFont, primaryColor);
            addHeaderCell(table, "Total Amount", headerStyleFont, primaryColor);
 
            List<OrderEntity> orders = orderEntityRepository.findOrdersByDateRange(user.getTenantId(), startDate, endDate);
            double totalSales = 0.0;
            double totalTax = 0.0;
            double totalDiscount = 0.0;

            for (OrderEntity o : orders) {
                addDataCell(table, o.getCreatedAt() != null ? o.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "", dataFont, Element.ALIGN_LEFT);
                addDataCell(table, o.getOrderId() != null ? o.getOrderId() : "", dataFont, Element.ALIGN_LEFT);
                addDataCell(table, o.getCustomerName() != null ? o.getCustomerName() : "N/A", dataFont, Element.ALIGN_LEFT);
                
                String itemsText = o.getItem().stream()
                        .map(oi -> oi.getName() + " (x" + oi.getQuantity() + ")")
                        .collect(Collectors.joining(", "));
                addDataCell(table, itemsText, itemsFont, Element.ALIGN_LEFT);
                
                addDataCell(table, String.format("%.2f", o.getDiscount() != null ? o.getDiscount() : 0.0), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", o.getGstAmount() != null ? o.getGstAmount() : 0.0), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", o.getGrandTotal() != null ? o.getGrandTotal() : 0.0), dataFont, Element.ALIGN_RIGHT);

                totalSales += (o.getGrandTotal() != null ? o.getGrandTotal() : 0.0);
                totalTax += (o.getGstAmount() != null ? o.getGstAmount() : 0.0);
                totalDiscount += (o.getDiscount() != null ? o.getDiscount() : 0.0);
            }
            
            addTotalCell(table, "TOTALS", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, orders.size() + " Orders", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, "", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, "", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, String.format("%.2f", totalDiscount), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalTax), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalSales), totalFont, Element.ALIGN_RIGHT);

            document.add(table);

        } else if ("monthly".equalsIgnoreCase(reportType)) {
            // Already handled by detailed logic above, keeping for safety
        } else if ("gst".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 60f, 90f, 90f, 90f, 90f, 103f });

            addHeaderCell(table, "GST Rate (%)", headerStyleFont, primaryColor);
            addHeaderCell(table, "Taxable Amt", headerStyleFont, primaryColor);
            addHeaderCell(table, "CGST", headerStyleFont, primaryColor);
            addHeaderCell(table, "SGST", headerStyleFont, primaryColor);
            addHeaderCell(table, "Total GST", headerStyleFont, primaryColor);
            addHeaderCell(table, "Total Sales", headerStyleFont, primaryColor);
 
            double totalTaxable = 0.0;
            double totalCGST = 0.0;
            double totalSGST = 0.0;
            double totalGst = 0.0;
            double totalSales = 0.0;

            for (GSTReportDTO dto : getGSTReport(startDate, endDate)) {
                addDataCell(table, String.format("%.1f%%", dto.getGstRate()), dataFont, Element.ALIGN_LEFT);
                addDataCell(table, String.format("%.2f", dto.getTaxableAmount()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getCgstAmount()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getSgstAmount()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalGst()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalSales()), dataFont, Element.ALIGN_RIGHT);

                totalTaxable += dto.getTaxableAmount();
                totalCGST += dto.getCgstAmount();
                totalSGST += dto.getSgstAmount();
                totalGst += dto.getTotalGst();
                totalSales += dto.getTotalSales();
            }

            addTotalCell(table, "TOTALS", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, String.format("%.2f", totalTaxable), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalCGST), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalSGST), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalGst), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalSales), totalFont, Element.ALIGN_RIGHT);

            document.add(table);

        } else if ("profit-loss".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 123f, 100f, 100f, 100f, 100f });

            addHeaderCell(table, "Period", headerStyleFont, primaryColor);
            addHeaderCell(table, "Revenue", headerStyleFont, primaryColor);
            addHeaderCell(table, "Cost (COGS)", headerStyleFont, primaryColor);
            addHeaderCell(table, "Profit", headerStyleFont, primaryColor);
            addHeaderCell(table, "Loss", headerStyleFont, primaryColor);
 
            double totalRevenue = 0.0;
            double totalCost = 0.0;
            double totalProfit = 0.0;
            double totalLoss = 0.0;

            for (ProfitLossReportDTO dto : getProfitLossReport(startDate, endDate)) {
                addDataCell(table, dto.getReportPeriod(), dataFont, Element.ALIGN_LEFT);
                addDataCell(table, String.format("%.2f", dto.getTotalRevenue()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalCost()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalProfit()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalLoss()), dataFont, Element.ALIGN_RIGHT);

                totalRevenue += dto.getTotalRevenue();
                totalCost += dto.getTotalCost();
                totalProfit += dto.getTotalProfit();
                totalLoss += dto.getTotalLoss();
            }

            addTotalCell(table, "TOTALS", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, String.format("%.2f", totalRevenue), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalCost), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalProfit), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalLoss), totalFont, Element.ALIGN_RIGHT);

            document.add(table);

        } else if ("best-selling".equalsIgnoreCase(reportType)) {
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 100f, 223f, 100f, 100f });

            addHeaderCell(table, "Product ID", headerStyleFont, primaryColor);
            addHeaderCell(table, "Product Name", headerStyleFont, primaryColor);
            addHeaderCell(table, "Qty Sold", headerStyleFont, primaryColor);
            addHeaderCell(table, "Revenue", headerStyleFont, primaryColor);
 
            long totalQty = 0;
            double totalRevenue = 0.0;

            for (BestSellingProductDTO dto : getBestSellingProducts(startDate, endDate, 20)) {
                addDataCell(table, dto.getProductId(), dataFont, Element.ALIGN_LEFT);
                addDataCell(table, dto.getProductName(), dataFont, Element.ALIGN_LEFT);
                addDataCell(table, String.valueOf(dto.getQuantitySold()), dataFont, Element.ALIGN_RIGHT);
                addDataCell(table, String.format("%.2f", dto.getTotalRevenue()), dataFont, Element.ALIGN_RIGHT);

                totalQty += dto.getQuantitySold();
                totalRevenue += dto.getTotalRevenue();
            }

            addTotalCell(table, "TOTALS", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, "", totalFont, Element.ALIGN_LEFT);
            addTotalCell(table, String.valueOf(totalQty), totalFont, Element.ALIGN_RIGHT);
            addTotalCell(table, String.format("%.2f", totalRevenue), totalFont, Element.ALIGN_RIGHT);

            document.add(table);
        }
 
        document.close();
        return bos.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTotalCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        cell.setBorderWidthTop(1f);
        cell.setBorderWidthBottom(1.5f);
        table.addCell(cell);
    }
}
