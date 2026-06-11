package com;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import com.io.ImportSummaryResponse;
import com.entity.ItemEntity;
import com.repository.ItemRepository;
import com.service.ItemImportService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BillingsoftwareApplicationTests {

	@Autowired
	private ItemImportService itemImportService;

	@Autowired
	private ItemRepository itemRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void testExcelImport() throws IOException {
		// 1. Create a mock Excel sheet
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Products");

		// Header Row
		Row header = sheet.createRow(0);
		header.createCell(0).setCellValue("Product Name");
		header.createCell(1).setCellValue("Price");
		header.createCell(2).setCellValue("Quantity");
		header.createCell(3).setCellValue("Category");

		// Data Rows
		Row row1 = sheet.createRow(1);
		row1.createCell(0).setCellValue("Test Excel Laptop");
		row1.createCell(1).setCellValue(55000.0);
		row1.createCell(2).setCellValue(10);
		row1.createCell(3).setCellValue("TestElectronics");

		Row row2 = sheet.createRow(2);
		row2.createCell(0).setCellValue("Test Excel Mouse");
		row2.createCell(1).setCellValue(450.0);
		row2.createCell(2).setCellValue(15);
		row2.createCell(3).setCellValue("TestAccessories");

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		workbook.write(bos);
		byte[] excelBytes = bos.toByteArray();
		workbook.close();

		MockMultipartFile multipartFile = new MockMultipartFile(
				"file", "products.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				excelBytes);

		// 2. Perform import
		ImportSummaryResponse response = itemImportService.importExcel(multipartFile);

		// 3. Assertions
		assertEquals(2, response.getTotalRecords());
		assertEquals(2, response.getSuccessCount());
		assertEquals(0, response.getFailedCount());

		// Verify database records
		List<ItemEntity> items = itemRepository.findAll();
		boolean laptopFound = items.stream().anyMatch(
				i -> i.getName().equals("Test Excel Laptop") && i.getPrice().compareTo(new BigDecimal("55000.0")) == 0);
		boolean mouseFound = items.stream().anyMatch(
				i -> i.getName().equals("Test Excel Mouse") && i.getPrice().compareTo(new BigDecimal("450.0")) == 0);
		assertTrue(laptopFound);
		assertTrue(mouseFound);
	}

	@Test
	void testPdfImport() throws IOException {
		// 1. Create a mock PDF file using PDFBox 3.x API
		PDDocument doc = new PDDocument();
		PDPage page = new PDPage();
		doc.addPage(page);

		try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
			contentStream.beginText();
			// Using PDFBox 3.x font instantiation
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
			contentStream.newLineAtOffset(50, 700);
			contentStream.showText("Test PDF Keyboard 1200.0 5 TestAccessories");
			contentStream.newLineAtOffset(0, -15);
			contentStream.showText("Test PDF Monitor 15000.0 2 TestElectronics");
			contentStream.endText();
		}

		ByteArrayOutputStream pdfBos = new ByteArrayOutputStream();
		doc.save(pdfBos);
		byte[] pdfBytes = pdfBos.toByteArray();
		doc.close();

		MockMultipartFile multipartFile = new MockMultipartFile(
				"file", "products.pdf",
				"application/pdf",
				pdfBytes);

		// 2. Perform import
		ImportSummaryResponse response = itemImportService.importPdf(multipartFile);

		// 3. Assertions
		assertEquals(2, response.getTotalRecords());
		assertEquals(2, response.getSuccessCount());
		assertEquals(0, response.getFailedCount());

		// Verify database records
		List<ItemEntity> items = itemRepository.findAll();
		boolean keyboardFound = items.stream().anyMatch(
				i -> i.getName().equals("Test PDF Keyboard") && i.getPrice().compareTo(new BigDecimal("1200.0")) == 0);
		boolean monitorFound = items.stream().anyMatch(
				i -> i.getName().equals("Test PDF Monitor") && i.getPrice().compareTo(new BigDecimal("15000.0")) == 0);
		assertTrue(keyboardFound);
		assertTrue(monitorFound);
	}
}
