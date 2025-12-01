package com.pb.synth.tradecapture.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing trade files in various formats (CSV, JSON, Excel, JSONL).
 * Supports streaming for large files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeFileParser {
    
    private final ObjectMapper objectMapper;
    
    private static final int MAX_TRADES = 5000; // 5K max trades per file
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /**
     * Parse a file and extract trades.
     * Automatically detects file format based on content type or file extension.
     * 
     * @param file The uploaded file
     * @return List of parsed trades
     * @throws IllegalArgumentException if file format is not supported or exceeds max trades
     */
    public List<TradeCaptureRequest> parseFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        log.info("Parsing file: filename={}, contentType={}, size={}", 
            filename, contentType, file.getSize());
        
        try {
            if (contentType != null) {
                if (contentType.contains("csv") || (filename != null && filename.endsWith(".csv"))) {
                    return parseCsv(file);
                } else if (contentType.contains("json") || (filename != null && filename.endsWith(".json"))) {
                    return parseJson(file);
                } else if (contentType.contains("excel") || contentType.contains("spreadsheet") || 
                          (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls")))) {
                    return parseExcel(file);
                } else if (filename != null && filename.endsWith(".jsonl")) {
                    return parseJsonl(file);
                }
            }
            
            // Fallback: try to detect by extension
            if (filename != null) {
                if (filename.endsWith(".csv")) {
                    return parseCsv(file);
                } else if (filename.endsWith(".json")) {
                    return parseJson(file);
                } else if (filename.endsWith(".jsonl")) {
                    return parseJsonl(file);
                } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                    return parseExcel(file);
                }
            }
            
            throw new IllegalArgumentException("Unsupported file format. Supported formats: CSV, JSON, JSONL, Excel (XLSX)");
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing file: filename={}", filename, e);
            throw new RuntimeException("Failed to parse file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse CSV file with streaming.
     */
    public List<TradeCaptureRequest> parseCsv(MultipartFile file) throws Exception {
        List<TradeCaptureRequest> trades = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .build()
                .parse(reader);
            
            for (CSVRecord record : parser) {
                if (trades.size() >= MAX_TRADES) {
                    throw new IllegalArgumentException(
                        "File exceeds maximum of " + MAX_TRADES + " trades");
                }
                
                TradeCaptureRequest trade = parseCsvRecord(record);
                trades.add(trade);
            }
        }
        
        log.info("Parsed {} trades from CSV file", trades.size());
        return trades;
    }
    
    /**
     * Parse JSON file (array of trades).
     */
    public List<TradeCaptureRequest> parseJson(MultipartFile file) throws Exception {
        try (var inputStream = file.getInputStream()) {
            TradeCaptureRequest[] tradesArray = objectMapper.readValue(
                inputStream, TradeCaptureRequest[].class);
            
            List<TradeCaptureRequest> trades = List.of(tradesArray);
            
            if (trades.size() > MAX_TRADES) {
                throw new IllegalArgumentException(
                    "File exceeds maximum of " + MAX_TRADES + " trades");
            }
            
            log.info("Parsed {} trades from JSON file", trades.size());
            return trades;
        }
    }
    
    /**
     * Parse JSONL file (one JSON object per line) with streaming.
     */
    public List<TradeCaptureRequest> parseJsonl(MultipartFile file) throws Exception {
        List<TradeCaptureRequest> trades = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                if (trades.size() >= MAX_TRADES) {
                    throw new IllegalArgumentException(
                        "File exceeds maximum of " + MAX_TRADES + " trades");
                }
                
                TradeCaptureRequest trade = objectMapper.readValue(line, TradeCaptureRequest.class);
                trades.add(trade);
            }
        }
        
        log.info("Parsed {} trades from JSONL file", trades.size());
        return trades;
    }
    
    /**
     * Parse Excel file (XLSX).
     */
    public List<TradeCaptureRequest> parseExcel(MultipartFile file) throws Exception {
        List<TradeCaptureRequest> trades = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); // First sheet
            
            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file must have a header row");
            }
            
            // Map column indices
            int tradeIdCol = findColumn(headerRow, "tradeId", "trade_id");
            int accountIdCol = findColumn(headerRow, "accountId", "account_id");
            int bookIdCol = findColumn(headerRow, "bookId", "book_id");
            int securityIdCol = findColumn(headerRow, "securityId", "security_id");
            int tradeDateCol = findColumn(headerRow, "tradeDate", "trade_date");
            
            // Read data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (trades.size() >= MAX_TRADES) {
                    throw new IllegalArgumentException(
                        "File exceeds maximum of " + MAX_TRADES + " trades");
                }
                
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                TradeCaptureRequest trade = parseExcelRow(row, 
                    tradeIdCol, accountIdCol, bookIdCol, securityIdCol, tradeDateCol);
                trades.add(trade);
            }
        }
        
        log.info("Parsed {} trades from Excel file", trades.size());
        return trades;
    }
    
    /**
     * Parse a CSV record into TradeCaptureRequest.
     */
    private TradeCaptureRequest parseCsvRecord(CSVRecord record) {
        // This is a simplified parser - in production, you'd want more robust parsing
        return TradeCaptureRequest.builder()
            .tradeId(record.get("tradeId"))
            .accountId(record.get("accountId"))
            .bookId(record.get("bookId"))
            .securityId(record.get("securityId"))
            .tradeDate(parseDate(record.get("tradeDate")))
            // TODO: Parse trade lots, counterparties, etc. from CSV
            .build();
    }
    
    /**
     * Parse an Excel row into TradeCaptureRequest.
     */
    private TradeCaptureRequest parseExcelRow(Row row, int tradeIdCol, int accountIdCol, 
                                             int bookIdCol, int securityIdCol, int tradeDateCol) {
        return TradeCaptureRequest.builder()
            .tradeId(getCellValue(row, tradeIdCol))
            .accountId(getCellValue(row, accountIdCol))
            .bookId(getCellValue(row, bookIdCol))
            .securityId(getCellValue(row, securityIdCol))
            .tradeDate(parseDate(getCellValue(row, tradeDateCol)))
            // TODO: Parse trade lots, counterparties, etc. from Excel
            .build();
    }
    
    /**
     * Find column index by name (case-insensitive).
     */
    private int findColumn(Row headerRow, String... names) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String cellValue = cell.getStringCellValue();
                for (String name : names) {
                    if (name.equalsIgnoreCase(cellValue)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    
    /**
     * Get cell value as string.
     */
    private String getCellValue(Row row, int colIndex) {
        if (colIndex < 0) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DATE_FORMATTER);
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }
    
    /**
     * Parse date string.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Error parsing date: {}", dateStr, e);
            return null;
        }
    }
}

