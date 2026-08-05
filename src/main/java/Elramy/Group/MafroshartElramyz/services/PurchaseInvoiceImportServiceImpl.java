package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceImportRow;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseInvoiceImportServiceImpl
        implements PurchaseInvoiceImportService {

    private final PurchaseInvoiceService purchaseInvoiceService;


    // =========================================================
    // IMPORT FILE
    // =========================================================

    @Override
    public List<PurchaseInvoiceResponse> importFile(
            MultipartFile file) {

        validateFile(file);

        String filename =
                file.getOriginalFilename().toLowerCase();

        try {

            List<PurchaseInvoiceImportRow> rows;

            if (filename.endsWith(".xlsx")
                    || filename.endsWith(".xls")) {

                rows = readExcel(file);

            } else if (filename.endsWith(".csv")) {

                rows = readCsv(file);

            } else {

                throw new IllegalArgumentException(
                        "Unsupported file type."
                );
            }


            if (rows.isEmpty()) {

                throw new IllegalArgumentException(
                        "File contains no data."
                );
            }


            // هنا بنسلم البيانات للـ PurchaseInvoiceService
            return purchaseInvoiceService.importInvoices(rows);

        } catch (IOException e) {

            throw new IllegalArgumentException(
                    "Failed to read import file.",
                    e
            );
        }
    }


    // =========================================================
    // EXCEL
    // =========================================================

    private List<PurchaseInvoiceImportRow> readExcel(
            MultipartFile file) throws IOException {

        List<PurchaseInvoiceImportRow> rows =
                new ArrayList<>();

        try (
                InputStream inputStream =
                        file.getInputStream();

                Workbook workbook =
                        WorkbookFactory.create(inputStream)
        ) {

            Sheet sheet =
                    workbook.getSheetAt(0);

            // الصف الأول Header
            for (int i = 1;
                 i <= sheet.getLastRowNum();
                 i++) {

                Row row =
                        sheet.getRow(i);

                if (row == null
                        || isEmptyExcelRow(row)) {

                    continue;
                }

                rows.add(
                        mapExcelRow(row)
                );
            }
        }

        return rows;
    }


    // =========================================================
    // CSV
    // =========================================================

    private List<PurchaseInvoiceImportRow> readCsv(
            MultipartFile file) throws IOException {

        List<PurchaseInvoiceImportRow> rows =
                new ArrayList<>();

        try (
                Reader reader =
                        new InputStreamReader(
                                file.getInputStream(),
                                StandardCharsets.UTF_8
                        );

                CSVParser parser =
                        CSVFormat.DEFAULT
                                .builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .setIgnoreEmptyLines(true)
                                .setTrim(true)
                                .build()
                                .parse(reader)
        ) {

            for (CSVRecord record : parser) {

                rows.add(
                        mapCsvRecord(record)
                );
            }
        }

        return rows;
    }


    // =========================================================
    // MAP EXCEL ROW
    // =========================================================

    private PurchaseInvoiceImportRow mapExcelRow(
            Row row) {

        String invoiceNumber =
                getString(row.getCell(0));

        Long branchId =
                getLong(row.getCell(1));

        String productCode =
                getString(row.getCell(2));

        Integer quantity =
                getInteger(row.getCell(3));

        BigDecimal unitPrice =
                getBigDecimal(row.getCell(4));

        BigDecimal discount =
                getBigDecimal(row.getCell(5));

        PaymentMethod paymentMethod =
                getPaymentMethod(row.getCell(6));

        String notes =
                getString(row.getCell(7));


        return new PurchaseInvoiceImportRow(
                invoiceNumber,
                branchId,
                productCode,
                quantity,
                unitPrice,
                discount,
                paymentMethod,
                notes
        );
    }


    // =========================================================
    // MAP CSV RECORD
    // =========================================================

    private PurchaseInvoiceImportRow mapCsvRecord(
            CSVRecord record) {

        String invoiceNumber =
                getCsvString(record, "invoiceNumber");

        Long branchId =
                getCsvLong(record, "branchId");

        String productCode =
                getCsvString(record, "productCode");

        Integer quantity =
                getCsvInteger(record, "quantity");

        BigDecimal unitPrice =
                getCsvBigDecimal(record, "unitPrice");

        BigDecimal discount =
                getCsvBigDecimal(record, "discount");

        PaymentMethod paymentMethod =
                getCsvPaymentMethod(
                        record,
                        "paymentMethod"
                );

        String notes =
                getCsvString(record, "notes");


        return new PurchaseInvoiceImportRow(
                invoiceNumber,
                branchId,
                productCode,
                quantity,
                unitPrice,
                discount,
                paymentMethod,
                notes
        );
    }


    // =========================================================
    // EXCEL HELPERS
    // =========================================================

    private String getString(Cell cell) {

        if (cell == null) {
            return null;
        }

        DataFormatter formatter =
                new DataFormatter();

        String value =
                formatter.formatCellValue(cell);

        return value.isBlank()
                ? null
                : value.trim();
    }


    private Long getLong(Cell cell) {

        String value =
                getString(cell);

        if (value == null) {
            return null;
        }

        return Long.valueOf(value);
    }


    private Integer getInteger(Cell cell) {

        String value =
                getString(cell);

        if (value == null) {
            return null;
        }

        return Integer.valueOf(value);
    }


    private BigDecimal getBigDecimal(Cell cell) {

        String value =
                getString(cell);

        if (value == null) {
            return null;
        }

        return new BigDecimal(value);
    }


    private PaymentMethod getPaymentMethod(
            Cell cell) {

        String value =
                getString(cell);

        if (value == null) {
            return null;
        }

        try {

            return PaymentMethod.valueOf(
                    value.toUpperCase()
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid payment method: "
                            + value
            );
        }
    }


    // =========================================================
    // CSV HELPERS
    // =========================================================

    private String getCsvString(
            CSVRecord record,
            String column) {

        String value =
                record.get(column);

        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }


    private Long getCsvLong(
            CSVRecord record,
            String column) {

        String value =
                getCsvString(record, column);

        return value == null
                ? null
                : Long.valueOf(value);
    }


    private Integer getCsvInteger(
            CSVRecord record,
            String column) {

        String value =
                getCsvString(record, column);

        return value == null
                ? null
                : Integer.valueOf(value);
    }


    private BigDecimal getCsvBigDecimal(
            CSVRecord record,
            String column) {

        String value =
                getCsvString(record, column);

        return value == null
                ? null
                : new BigDecimal(value);
    }


    private PaymentMethod getCsvPaymentMethod(
            CSVRecord record,
            String column) {

        String value =
                getCsvString(record, column);

        if (value == null) {
            return null;
        }

        try {

            return PaymentMethod.valueOf(
                    value.toUpperCase()
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid payment method: "
                            + value
            );
        }
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateFile(
            MultipartFile file) {

        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "File is required."
            );
        }

        String filename =
                file.getOriginalFilename();

        if (filename == null) {

            throw new IllegalArgumentException(
                    "Invalid file."
            );
        }

        String lower =
                filename.toLowerCase();

        boolean supported =
                lower.endsWith(".xlsx")
                        || lower.endsWith(".xls")
                        || lower.endsWith(".csv");

        if (!supported) {

            throw new IllegalArgumentException(
                    "Only Excel (.xlsx, .xls) or CSV files are supported."
            );
        }
    }


    private boolean isEmptyExcelRow(
            Row row) {

        for (int i = 0; i < 8; i++) {

            Cell cell =
                    row.getCell(i);

            if (cell != null
                    && cell.getCellType()
                    != CellType.BLANK
                    && getString(cell) != null) {

                return false;
            }
        }

        return true;
    }
}