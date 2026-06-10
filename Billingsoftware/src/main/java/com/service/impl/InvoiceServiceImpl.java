package com.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.entity.InvoiceEntity;
import com.entity.OrderEntity;
import com.entity.OrderItemEntity;
import com.entity.UserEntity;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfPCellEvent;
import com.itextpdf.text.pdf.PdfGState;
import com.repository.InvoiceEntityRepository;
import com.repository.OrderEntityRepository;
import com.repository.UserRepository;
import com.service.InvoiceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceEntityRepository invoiceRepository;
    private final OrderEntityRepository orderRepository;
    private final UserRepository userRepository;
    private final Cloudinary cloudinary;

    @Override
    @Transactional
    public InvoiceEntity generateAndSaveInvoice(OrderEntity order) {
        try {
            // Retrieve Shop details using tenant ID (which is the shop owner's userId)
            Optional<UserEntity> shopOwnerOpt = userRepository.findByUserId(order.getUserId());
            UserEntity shopOwner = shopOwnerOpt.orElse(null);

            byte[] pdfBytes = buildInvoicePdf(order, shopOwner);
            String pdfUrl = null;

            // 1. Try Cloudinary (Bypassed - using Option B local storage)
            try {
                if (false) {
                    Map uploadResult = cloudinary.uploader().upload(pdfBytes, ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", "invoices/Invoice_" + order.getOrderId() + ".pdf"));
                    if (uploadResult != null && uploadResult.containsKey("secure_url")) {
                        pdfUrl = uploadResult.get("secure_url").toString();
                    }
                }
            } catch (Exception ex) {
                System.err.println("Cloudinary PDF upload failed, falling back to local storage: " + ex.getMessage());
            }

            // 2. Fallback to Local Storage
            if (pdfUrl == null) {
                File dir = new File("invoices");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                String localFileName = "Invoice_" + order.getOrderId() + ".pdf";
                File localFile = new File(dir, localFileName);
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(pdfBytes);
                }
                pdfUrl = "http://localhost:8080/invoices/" + localFileName;
            }

            // 3. Save Invoice Entity
            InvoiceEntity invoice = InvoiceEntity.builder()
                    .invoiceNumber(order.getOrderId())
                    .orderId(order.getOrderId())
                    .customerName(order.getCustomerName())
                    .customerMobile(order.getPhoneNumber())
                    .totalAmount(order.getGrandTotal())
                    .pdfUrl(pdfUrl)
                    .createdAt(LocalDateTime.now())
                    .userId(order.getUserId())
                    .build();

            invoice = invoiceRepository.save(invoice);

            // 4. Update pdfUrl in OrderEntity
            order.setPdfUrl(pdfUrl);
            orderRepository.save(order);

            return invoice;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate and save invoice: " + e.getMessage(), e);
        }
    }

    @Override
    public InvoiceEntity getInvoiceByNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new RuntimeException("Invoice not found for number: " + invoiceNumber));
    }

    @Override
    public InvoiceEntity getInvoiceByOrderId(String orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Invoice not found for order id: " + orderId));
    }

    @Override
    public byte[] getInvoicePdfBytes(String invoiceNumber) {
        java.io.File file = new java.io.File("invoices/Invoice_" + invoiceNumber + ".pdf");
        if (file.exists()) {
            try {
                return java.nio.file.Files.readAllBytes(file.toPath());
            } catch (Exception e) {
                System.err.println("Failed to read local invoice PDF: " + e.getMessage());
            }
        }
        
        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        if (invoiceOpt.isPresent()) {
            try {
                Optional<OrderEntity> orderOpt = orderRepository.findByOrderId(invoiceOpt.get().getOrderId());
                if (orderOpt.isPresent()) {
                    Optional<UserEntity> shopOwnerOpt = userRepository.findByUserId(orderOpt.get().getUserId());
                    return buildInvoicePdf(orderOpt.get(), shopOwnerOpt.orElse(null));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to rebuild invoice PDF: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Invoice not found or PDF could not be loaded for invoice number: " + invoiceNumber);
    }

    private byte[] buildInvoicePdf(OrderEntity order, UserEntity shopOwner) throws Exception {
        // Setup document margins (left=36, right=36, top=120, bottom=80)
        Document document = new Document(PageSize.A4, 36, 36, 120, 80);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, bos);
        
        // Register custom event helper to draw top/bottom polygons and background watermark
        writer.setPageEvent(new InvoicePageEvent(shopOwner));
        
        document.open();

        // Design Fonts
        Font boldFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK);
        Font regularFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK);
        Font tableHeaderFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);

        // 1. Header Section (Logo on Left, Company Details on Right)
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[] { 25, 75 });
        headerTable.setSpacingAfter(15);

        // Left Cell - Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        String logoUrl = shopOwner != null ? shopOwner.getShopLogoUrl() : null;
        boolean logoLoaded = false;
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                Image img = Image.getInstance(logoUrl);
                img.scaleToFit(70, 70);
                logoCell.addElement(img);
                logoLoaded = true;
            } catch (Exception ex) {
                System.err.println("Could not load shop logo image, falling back to default logo shapes: " + ex.getMessage());
            }
        }

        if (!logoLoaded) {
            // Draw default logo shapes and text side-by-side using nested table
            PdfPTable nestedLogoTable = new PdfPTable(2);
            nestedLogoTable.setWidthPercentage(100);
            nestedLogoTable.setWidths(new float[] { 40, 60 });
            
            PdfPCell drawingCell = new PdfPCell();
            drawingCell.setBorder(Rectangle.NO_BORDER);
            drawingCell.setFixedHeight(40);
            drawingCell.setCellEvent(new DefaultLogoEvent());
            nestedLogoTable.addCell(drawingCell);
            
            PdfPCell textCell = new PdfPCell();
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            Font logoTitleFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK);
            Font logoTaglineFont = new Font(Font.FontFamily.HELVETICA, 7, Font.NORMAL, BaseColor.GRAY);
            
            Paragraph logoTitle = new Paragraph("LOGO HERE", logoTitleFont);
            logoTitle.setLeading(10);
            Paragraph logoTagline = new Paragraph("TAGLINE", logoTaglineFont);
            logoTagline.setLeading(8);
            
            textCell.addElement(logoTitle);
            textCell.addElement(logoTagline);
            nestedLogoTable.addCell(textCell);
            
            logoCell.addElement(nestedLogoTable);
        }
        headerTable.addCell(logoCell);

        // Right Cell - Company Details
        PdfPCell detailsCell = new PdfPCell();
        detailsCell.setBorder(Rectangle.NO_BORDER);
        detailsCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        String shopName = shopOwner != null ? shopOwner.getShopName() : "COMPANY NAME HERE";
        String shopAddress = shopOwner != null ? shopOwner.getShopAddress() : "Your Business Address 0000, Main Street, Unit 000C FEL, 0000";
        String shopMobile = shopOwner != null ? shopOwner.getShopMobile() : "0123-5678900";
        String shopEmail = shopOwner != null ? shopOwner.getShopEmail() : "Your Mail Here";
        String shopWebsite = shopOwner != null ? shopOwner.getShopWebsite() : "";
        String gstNumber = shopOwner != null ? shopOwner.getGstNumber() : null;

        Font nameFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.BLACK);
        Font companyDetailFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.BLACK);
        Font companyDetailBoldFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.BLACK);

        Paragraph namePara = new Paragraph(shopName.toUpperCase(), nameFont);
        namePara.setSpacingAfter(2);
        detailsCell.addElement(namePara);

        detailsCell.addElement(new Paragraph(shopAddress, companyDetailFont));
        
        String contactInfo = "Mob: " + shopMobile + " | Email: " + shopEmail;
        if (shopWebsite != null && !shopWebsite.isBlank()) {
            contactInfo += " | Web: " + shopWebsite;
        }
        detailsCell.addElement(new Paragraph(contactInfo, companyDetailFont));

        if (gstNumber != null && !gstNumber.isBlank()) {
            Paragraph gstPara = new Paragraph("GSTIN: " + gstNumber, companyDetailBoldFont);
            gstPara.setSpacingBefore(2);
            detailsCell.addElement(gstPara);
        }
        headerTable.addCell(detailsCell);
        headerTable.setSpacingAfter(20);
        document.add(headerTable);

        // 2. Orange Invoice Badge Table
        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(30);
        badgeTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeTable.setSpacingAfter(20);

        PdfPCell badgeCell = new PdfPCell(new Phrase("INVOICE / BILL", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE)));
        badgeCell.setBackgroundColor(new BaseColor(248, 158, 49)); // #F89E31
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setPaddingTop(5);
        badgeCell.setPaddingBottom(5);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeTable.addCell(badgeCell);
        document.add(badgeTable);

        // 3. Customer & Invoice Info Metadata Rows
        Font boldLabelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK);
        Font normalValFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK);

        // Row 1: SL No & Date
        PdfPTable metaRow1 = new PdfPTable(2);
        metaRow1.setWidthPercentage(100);
        metaRow1.setWidths(new float[] { 50, 50 });
        metaRow1.setSpacingAfter(6);

        PdfPCell slCell = new PdfPCell();
        slCell.setBorder(Rectangle.NO_BORDER);
        Phrase slPhrase = new Phrase();
        slPhrase.add(new Phrase("SL No : ", boldLabelFont));
        slPhrase.add(new Phrase(order.getOrderId(), normalValFont));
        slCell.addElement(slPhrase);
        metaRow1.addCell(slCell);

        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Phrase datePhrase = new Phrase();
        datePhrase.add(new Phrase("Date : ", boldLabelFont));
        datePhrase.add(new Phrase(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), normalValFont));
        Paragraph datePara = new Paragraph(datePhrase);
        datePara.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(datePara);
        metaRow1.addCell(dateCell);
        document.add(metaRow1);

        // Row 2: Customer Name
        PdfPTable metaRow2 = new PdfPTable(1);
        metaRow2.setWidthPercentage(100);
        metaRow2.setSpacingAfter(6);

        PdfPCell customerNameCell = new PdfPCell();
        customerNameCell.setBorder(Rectangle.NO_BORDER);
        Phrase customerNamePhrase = new Phrase();
        customerNamePhrase.add(new Phrase("Customer Name : ", boldLabelFont));
        customerNamePhrase.add(new Phrase(order.getCustomerName(), normalValFont));
        customerNameCell.addElement(customerNamePhrase);
        metaRow2.addCell(customerNameCell);
        document.add(metaRow2);

        // Row 3: Mobile & Email
        PdfPTable metaRow3 = new PdfPTable(2);
        metaRow3.setWidthPercentage(100);
        metaRow3.setWidths(new float[] { 50, 50 });
        metaRow3.setSpacingAfter(20);

        PdfPCell mobCell = new PdfPCell();
        mobCell.setBorder(Rectangle.NO_BORDER);
        Phrase mobPhrase = new Phrase();
        mobPhrase.add(new Phrase("Mobile : ", boldLabelFont));
        mobPhrase.add(new Phrase(order.getPhoneNumber(), normalValFont));
        mobCell.addElement(mobPhrase);
        metaRow3.addCell(mobCell);

        PdfPCell emailCell = new PdfPCell();
        emailCell.setBorder(Rectangle.NO_BORDER);
        emailCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Phrase emailPhrase = new Phrase();
        emailPhrase.add(new Phrase("Email : ", boldLabelFont));
        emailPhrase.add(new Phrase("N/A", normalValFont));
        Paragraph emailPara = new Paragraph(emailPhrase);
        emailPara.setAlignment(Element.ALIGN_RIGHT);
        emailCell.addElement(emailPara);
        metaRow3.addCell(emailCell);
        document.add(metaRow3);

        // 4. Items Table
        boolean printWithGst = (order.getGstAmount() != null && order.getGstAmount() > 0) || (order.getTax() != null && order.getTax() > 0);
        
        PdfPTable table;
        if (printWithGst) {
            table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 6f, 30f, 8f, 12f, 12f, 10f, 10f, 12f });
        } else {
            table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 6f, 46f, 10f, 12f, 12f, 14f });
        }

        // Add Headers with theme color `#003B5C`
        BaseColor primaryBorderColor = new BaseColor(0, 59, 92);
        String[] headers = printWithGst
            ? new String[] { "Sl", "Description", "Qty", "Rate", "Discount", "GST %", "GST Amt", "Total" }
            : new String[] { "Sl", "Description", "Qty", "Rate", "Discount", "Total" };
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, tableHeaderFont));
            cell.setBackgroundColor(primaryBorderColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6);
            cell.setBorderColor(primaryBorderColor);
            table.addCell(cell);
        }

        // Render line items
        int slNum = 1;
        for (OrderItemEntity item : order.getItem()) {
            table.addCell(createCenterCell(String.valueOf(slNum++), regularFont));
            table.addCell(createLeftCell(item.getName(), regularFont));
            table.addCell(createCenterCell(String.valueOf(item.getQuantity()), regularFont));
            table.addCell(createCenterCell(String.format("%.2f", item.getPrice()), regularFont));
            table.addCell(createCenterCell(String.format("%.2f", item.getDiscount()), regularFont));
            if (printWithGst) {
                table.addCell(createCenterCell(String.format("%.1f%%", item.getGstPercentage()), regularFont));
                table.addCell(createCenterCell(String.format("%.2f", item.getGstAmount()), regularFont));
            }

            double lineTotal = (item.getQuantity() * item.getPrice()) - item.getDiscount() + (printWithGst ? item.getGstAmount() : 0);
            table.addCell(createCenterCell(String.format("%.2f", lineTotal), regularFont));
        }

        // Pad empty rows to maintain professional length (min 8 rows)
        int currentItemsSize = order.getItem().size();
        int padRows = Math.max(0, 8 - currentItemsSize);
        for (int i = 0; i < padRows; i++) {
            table.addCell(createCenterCell("", regularFont));
            table.addCell(createLeftCell("", regularFont));
            table.addCell(createCenterCell("", regularFont));
            table.addCell(createCenterCell("", regularFont));
            table.addCell(createCenterCell("", regularFont));
            if (printWithGst) {
                table.addCell(createCenterCell("", regularFont));
                table.addCell(createCenterCell("", regularFont));
            }
            table.addCell(createCenterCell("", regularFont));
        }

        document.add(table);

        // 5. Summary/Totals Block
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(printWithGst ? 42f : 35f);
        totalsTable.setWidths(new float[] { 55, 45 });
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setSpacingBefore(10);
        totalsTable.setSpacingAfter(10);

        addSummaryRow(totalsTable, "Sub Total", String.format("INR %.2f", order.getSubtotal()), regularFont, false);
        if (order.getDiscount() > 0) {
            addSummaryRow(totalsTable, "Discount", String.format("- INR %.2f", order.getDiscount()), regularFont, false);
        }
        if (printWithGst && order.getGstAmount() > 0) {
            addSummaryRow(totalsTable, "GST Total", String.format("INR %.2f", order.getGstAmount()), regularFont, false);
        }
        addSummaryRow(totalsTable, "Grand Total", String.format("INR %.2f", order.getGrandTotal()), boldFont, true);

        document.add(totalsTable);

        // 6. In Words Row
        Paragraph wordsPara = new Paragraph();
        wordsPara.add(new Phrase("In Words: ", boldFont));
        wordsPara.add(new Phrase(numberToWords(order.getGrandTotal()).toUpperCase(), new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.BLACK)));
        wordsPara.setSpacingBefore(5);
        wordsPara.setSpacingAfter(20);
        document.add(wordsPara);

        // 7. Signatures Row
        PdfPTable sigTable = new PdfPTable(2);
        sigTable.setWidthPercentage(100);
        sigTable.setSpacingBefore(20);

        PdfPCell sigLeft = new PdfPCell();
        sigLeft.setBorder(Rectangle.NO_BORDER);
        sigLeft.setHorizontalAlignment(Element.ALIGN_LEFT);
        Paragraph lineLeft = new Paragraph("_____________________", regularFont);
        lineLeft.setSpacingAfter(4);
        Paragraph lblLeft = new Paragraph("Received by", boldFont);
        sigLeft.addElement(lineLeft);
        sigLeft.addElement(lblLeft);
        sigTable.addCell(sigLeft);

        PdfPCell sigRight = new PdfPCell();
        sigRight.setBorder(Rectangle.NO_BORDER);
        sigRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph lineRight = new Paragraph("_____________________", regularFont);
        lineRight.setAlignment(Element.ALIGN_RIGHT);
        lineRight.setSpacingAfter(4);
        Paragraph lblRight = new Paragraph("Authorized Signatory", boldFont);
        lblRight.setAlignment(Element.ALIGN_RIGHT);
        sigRight.addElement(lineRight);
        sigRight.addElement(lblRight);
        sigTable.addCell(sigRight);

        document.add(sigTable);

        document.close();
        return bos.toByteArray();
    }

    private PdfPCell createCenterCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        cell.setBorderColor(new BaseColor(0, 59, 92)); // #003B5C
        return cell;
    }

    private PdfPCell createLeftCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        cell.setBorderColor(new BaseColor(0, 59, 92)); // #003B5C
        return cell;
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font font, boolean isGrandTotal) {
        PdfPCell cellLabel = new PdfPCell(new Phrase(label, font));
        cellLabel.setBorder(Rectangle.BOTTOM | Rectangle.RIGHT);
        cellLabel.setBorderColor(new BaseColor(0, 59, 92)); // #003B5C
        cellLabel.setPadding(6);
        cellLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        PdfPCell cellValue = new PdfPCell(new Phrase(value, font));
        cellValue.setBorder(Rectangle.BOTTOM);
        cellValue.setBorderColor(new BaseColor(0, 59, 92)); // #003B5C
        cellValue.setPadding(6);
        cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);

        if (isGrandTotal) {
            BaseColor lightBlueBg = new BaseColor(240, 245, 248); // #F0F5F8 (5% opacity blue)
            cellLabel.setBackgroundColor(lightBlueBg);
            cellValue.setBackgroundColor(lightBlueBg);
        }

        table.addCell(cellLabel);
        table.addCell(cellValue);
    }

    private String numberToWords(double num) {
        String[] a = {"", "One ", "Two ", "Three ", "Four ", "Five ", "Six ", "Seven ", "Eight ", "Nine ", "Ten ", "Eleven ", "Twelve ", "Thirteen ", "Fourteen ", "Fifteen ", "Sixteen ", "Seventeen ", "Eighteen ", "Nineteen "};
        String[] b = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        long cleanNum = (long) Math.floor(num);
        if (cleanNum == 0) return "Zero Rupees Only";

        String result = translateNumber((int)(cleanNum / 10000000), a, b) + "Crore " +
                       translateNumber((int)((cleanNum / 100000) % 100), a, b) + "Lakh " +
                       translateNumber((int)((cleanNum / 1000) % 100), a, b) + "Thousand " +
                       translateNumber((int)(cleanNum % 1000), a, b);

        result = result.replaceAll("\\s+", " ").trim();
        return result + " Rupees Only";
    }

    private String translateNumber(int n, String[] a, String[] b) {
        String str = "";
        if (n > 99) {
            str += a[n / 100] + "Hundred ";
            n %= 100;
        }
        if (n > 19) {
            str += b[n / 10] + " ";
            n %= 10;
        }
        if (n > 0) {
            str += a[n];
        }
        return str;
    }
}

class InvoicePageEvent extends PdfPageEventHelper {
    private final UserEntity shopOwner;

    public InvoicePageEvent(UserEntity shopOwner) {
        this.shopOwner = shopOwner;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContentUnder();
        float pageWidth = document.getPageSize().getWidth();
        float pageHeight = document.getPageSize().getHeight();

        cb.saveState();

        // 1. TOP SHAPES
        // Orange top shape: clip-path: polygon(0 0, 100% 0, 85% 100%, 0 100%); width: 65%; height: 40px;
        cb.setColorFill(new BaseColor(248, 158, 49)); // #F89E31
        cb.moveTo(0, pageHeight);
        cb.lineTo(pageWidth * 0.65f, pageHeight);
        cb.lineTo(pageWidth * 0.5525f, pageHeight - 40);
        cb.lineTo(0, pageHeight - 40);
        cb.closePath();
        cb.fill();

        // Blue top shape: clip-path: polygon(30% 0, 100% 0, 100% 100%, 0 100%); width: 45%; height: 60px;
        cb.setColorFill(new BaseColor(0, 59, 92)); // #003B5C
        cb.moveTo(pageWidth * 0.685f, pageHeight);
        cb.lineTo(pageWidth, pageHeight);
        cb.lineTo(pageWidth, pageHeight - 60);
        cb.lineTo(pageWidth * 0.55f, pageHeight - 60);
        cb.closePath();
        cb.fill();

        // 2. BOTTOM SHAPES
        // Blue bottom shape: clip-path: polygon(0 0, 80% 0, 100% 100%, 0 100%); width: 50%; height: 35px;
        cb.setColorFill(new BaseColor(0, 59, 92)); // #003B5C
        cb.moveTo(0, 35);
        cb.lineTo(pageWidth * 0.4f, 35);
        cb.lineTo(pageWidth * 0.5f, 0);
        cb.lineTo(0, 0);
        cb.closePath();
        cb.fill();

        // Orange bottom shape: clip-path: polygon(5% 0, 100% 0, 100% 100%, 0 100%); width: 80%; height: 25px;
        cb.setColorFill(new BaseColor(248, 158, 49)); // #F89E31
        cb.moveTo(pageWidth * 0.24f, 25);
        cb.lineTo(pageWidth, 25);
        cb.lineTo(pageWidth, 0);
        cb.lineTo(pageWidth * 0.2f, 0);
        cb.closePath();
        cb.fill();

        // 3. CENTER WATERMARK
        float centerX = pageWidth / 2;
        float centerY = pageHeight / 2;
        float wSize = 250f;

        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.05f);
        cb.setGState(gs);

        // Watermark orange: top-left polygon
        cb.setColorFill(new BaseColor(248, 158, 49));
        cb.moveTo(centerX - wSize/2, centerY - wSize/2);
        cb.lineTo(centerX + wSize/2, centerY + wSize/2);
        cb.lineTo(centerX - wSize/2, centerY + wSize/2);
        cb.closePath();
        cb.fill();

        // Watermark blue: bottom-right polygon
        cb.setColorFill(new BaseColor(0, 59, 92));
        cb.moveTo(centerX - wSize/2, centerY - wSize/2);
        cb.lineTo(centerX + wSize/2, centerY - wSize/2);
        cb.lineTo(centerX + wSize/2, centerY + wSize/2);
        cb.closePath();
        cb.fill();

        cb.restoreState();
    }
}

class DefaultLogoEvent implements PdfPCellEvent {
    @Override
    public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
        PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
        cb.saveState();
        
        float x = position.getLeft();
        float y = position.getBottom();
        float w = 35;
        float h = 35;
        
        // Vertically center the drawing in the cell if needed
        float offset = (position.getHeight() - h) / 2;
        y += offset;
        
        // Draw logo blue polygon: top-left half
        cb.setColorFill(new BaseColor(0, 59, 92)); // #003B5C
        cb.moveTo(x, y + h);
        cb.lineTo(x + w * 0.625f, y + h);
        cb.lineTo(x, y + h - h * 0.625f);
        cb.closePath();
        cb.fill();
        
        // Draw logo orange polygon: bottom-right half
        cb.setColorFill(new BaseColor(248, 158, 49)); // #F89E31
        cb.moveTo(x + w, y + h - h * 0.125f);
        cb.lineTo(x + w, y);
        cb.lineTo(x + w * 0.125f, y);
        cb.closePath();
        cb.fill();
        
        cb.restoreState();
    }
}
