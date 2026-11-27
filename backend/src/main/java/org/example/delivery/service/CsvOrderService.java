package org.example.delivery.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.example.delivery.config.AppProperties;
import org.example.delivery.domain.Client;
import org.example.delivery.domain.DeliveryOrder;
import org.example.delivery.domain.UserAccount;
import org.example.delivery.dto.ImportDtos.ImportMode;
import org.example.delivery.dto.ImportDtos.ImportPreview;
import org.example.delivery.dto.ImportDtos.ImportResult;
import org.example.delivery.dto.ImportDtos.RowIssue;
import org.example.delivery.dto.OrderDtos.OrderRequest;
import org.example.delivery.dto.OrderDtos.OrderResponse;
import org.example.delivery.error.BadRequestException;
import org.example.delivery.filter.OrderSpecifications;
import org.example.delivery.filter.SmartFilterParser;
import org.example.delivery.repository.ClientRepository;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.example.delivery.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CSV-импорт и экспорт заказов.
 *
 * <p>Предварительная проверка (preview) отделена от применения (apply): ошибки видны до записи
 * в базу. Клиент в файле указывается телефоном. При экспорте нейтрализуются значения,
 * которые Excel может воспринять как формулы (CSV injection).</p>
 */
@Service
public class CsvOrderService {
    static final String[] HEADERS = {
            "orderNumber", "clientPhone", "pickupAddress", "deliveryAddress", "description",
            "weight", "price", "fragile", "status", "tags"
    };

    private final DeliveryOrderRepository orders;
    private final ClientRepository clients;
    private final OrderService orderService;
    private final CurrentUserService currentUser;
    private final SmartFilterParser parser;
    private final OrderMapper mapper;
    private final Validator validator;
    private final AppProperties properties;
    private final AuditService audit;

    public CsvOrderService(DeliveryOrderRepository orders, ClientRepository clients, OrderService orderService,
                           CurrentUserService currentUser, SmartFilterParser parser, OrderMapper mapper,
                           Validator validator, AppProperties properties, AuditService audit) {
        this.orders = orders;
        this.clients = clients;
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.parser = parser;
        this.mapper = mapper;
        this.validator = validator;
        this.properties = properties;
        this.audit = audit;
    }

    /** Проверяет CSV без записи в базу. */
    @Transactional(readOnly = true)
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        ParsedFile parsed = parse(file, mode == null ? ImportMode.CREATE_ONLY : mode);
        return new ImportPreview(parsed.totalRows(), parsed.rows().size(), parsed.totalRows() - parsed.rows().size(),
                parsed.issues());
    }

    /** Применяет только корректные строки и возвращает подробный итог. */
    @Transactional
    public ImportResult apply(MultipartFile file, ImportMode mode) {
        ImportMode effectiveMode = mode == null ? ImportMode.CREATE_ONLY : mode;
        ParsedFile parsed = parse(file, effectiveMode);
        UserAccount actor = currentUser.requireEntity();
        int created = 0;
        int updated = 0;
        int skipped = parsed.totalRows() - parsed.rows().size();
        List<RowIssue> issues = new ArrayList<>(parsed.issues());

        for (ParsedRow row : parsed.rows()) {
            try {
                DeliveryOrder existing = orders.findByOrderNumberIgnoreCase(row.request().orderNumber()).orElse(null);
                if (existing != null && effectiveMode == ImportMode.CREATE_ONLY) {
                    issues.add(new RowIssue(row.rowNumber(), "orderNumber", "Заказ с таким номером уже существует"));
                    skipped++;
                    continue;
                }
                orderService.upsertFromImport(existing, row.request(), actor);
                if (existing == null) created++; else updated++;
            } catch (RuntimeException ex) {
                issues.add(new RowIssue(row.rowNumber(), "row", safeMessage(ex)));
                skipped++;
            }
        }
        audit.record("ORDER_CSV_IMPORTED", "ORDER", null, "Импортирован CSV с заказами",
                Map.of("created", created, "updated", updated, "skipped", skipped, "mode", effectiveMode.name()));
        return new ImportResult(parsed.totalRows(), created, updated, skipped, List.copyOf(issues));
    }

    /** Постранично экспортирует текущую выборку, не загружая весь набор в память. */
    @Transactional(readOnly = true)
    public void export(String query, OutputStream output) throws IOException {
        // BOM нужен, чтобы Excel корректно открыл UTF-8 с кириллицей.
        output.write("﻿".getBytes(StandardCharsets.UTF_8));
        var spec = OrderSpecifications.from(parser.parse(query));
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
            int page = 0;
            while (true) {
                var result = orders.findAll(spec, PageRequest.of(page, 500, Sort.by("createdAt").descending().and(Sort.by("id"))));
                for (DeliveryOrder order : result.getContent()) {
                    OrderResponse dto = mapper.toResponse(order);
                    printer.printRecord(
                            safeCell(dto.orderNumber()), safeCell(dto.clientPhone()), safeCell(dto.pickupAddress()),
                            safeCell(dto.deliveryAddress()), safeCell(dto.description()),
                            dto.weight(), dto.price(), dto.fragile(), dto.status(),
                            safeCell(String.join("|", dto.tags())));
                }
                if (result.isLast()) break;
                page++;
            }
            printer.flush();
        }
        audit.record("ORDER_CSV_EXPORTED", "ORDER", null, "Экспортирована выборка заказов",
                Map.of("query", query == null ? "" : query));
    }

    private ParsedFile parse(MultipartFile file, ImportMode mode) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Выберите непустой CSV-файл");
        List<ParsedRow> validRows = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        Set<String> numbersInFile = new LinkedHashSet<>();
        int total = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csv = CSVParser.parse(reader, format)) {
            validateHeaders(csv.getHeaderMap());
            for (CSVRecord record : csv) {
                total++;
                long rowNumber = record.getRecordNumber() + 1;
                if (total > properties.importLimits().maxRows()) {
                    throw new BadRequestException("CSV содержит больше " + properties.importLimits().maxRows() + " строк");
                }
                try {
                    ParsedRow row = toRow(record, rowNumber);
                    Set<ConstraintViolation<OrderRequest>> violations = validator.validate(row.request());
                    if (!violations.isEmpty()) {
                        for (ConstraintViolation<OrderRequest> v : violations) {
                            issues.add(new RowIssue(rowNumber, v.getPropertyPath().toString(), v.getMessage()));
                        }
                        continue;
                    }
                    String key = row.request().orderNumber().toUpperCase(Locale.ROOT);
                    if (!numbersInFile.add(key)) {
                        issues.add(new RowIssue(rowNumber, "orderNumber", "Повтор номера заказа внутри файла"));
                        continue;
                    }
                    if (mode == ImportMode.CREATE_ONLY && orders.existsByOrderNumberIgnoreCase(key)) {
                        issues.add(new RowIssue(rowNumber, "orderNumber", "Заказ с таким номером уже существует"));
                        continue;
                    }
                    validRows.add(row);
                } catch (RuntimeException ex) {
                    issues.add(new RowIssue(rowNumber, "row", safeMessage(ex)));
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Не удалось прочитать CSV в кодировке UTF-8");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new BadRequestException("Некорректная структура CSV");
        }
        return new ParsedFile(total, List.copyOf(validRows), List.copyOf(issues));
    }

    private ParsedRow toRow(CSVRecord record, long rowNumber) {
        String phone = required(record, "clientPhone");
        Client client = clients.findByPhone(phone)
                .orElseThrow(() -> new BadRequestException("Клиент с телефоном " + phone + " не найден"));
        if (!client.isActive()) throw new BadRequestException("Клиент отключён: " + phone);

        OrderRequest request = new OrderRequest(
                OrderService.normalizeNumber(required(record, "orderNumber")),
                client.getId(),
                required(record, "pickupAddress"),
                required(record, "deliveryAddress"),
                nullable(record, "description"),
                parseDecimal(required(record, "weight"), "weight"),
                parseDecimal(required(record, "price"), "price"),
                parseBoolean(nullable(record, "fragile")),
                SmartFilterParser.status(required(record, "status")),
                parseTags(nullable(record, "tags")),
                null);
        return new ParsedRow(rowNumber, request);
    }

    private static void validateHeaders(Map<String, Integer> actual) {
        for (String header : HEADERS) {
            if (!actual.containsKey(header)) throw new BadRequestException("Отсутствует колонка: " + header);
        }
    }

    private static String required(CSVRecord record, String field) {
        String value = nullable(record, field);
        if (value == null) throw new BadRequestException("Не заполнено поле " + field);
        return value;
    }

    private static String nullable(CSVRecord record, String field) {
        String value = record.isMapped(field) && record.isSet(field) ? record.get(field) : null;
        if (value == null) return null;
        value = value.replace("﻿", "").trim();
        // Снимаем защитный апостроф, добавленный нашим же экспортом.
        if (value.length() > 1 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0) {
            value = value.substring(1);
        }
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal parseDecimal(String value, String field) {
        try { return new BigDecimal(value.replace(',', '.')); }
        catch (NumberFormatException ex) { throw new BadRequestException("Некорректное число в поле " + field); }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "да" -> true;
            case "false", "0", "no", "нет" -> false;
            default -> throw new BadRequestException("fragile принимает true/false или да/нет");
        };
    }

    private static Set<String> parseTags(String value) {
        if (value == null) return Set.of();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.toLowerCase(Locale.ROOT)).forEach(tags::add);
        return tags;
    }

    static String safeCell(Object value) {
        if (value == null) return "";
        String cell = String.valueOf(value);
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) return "'" + cell;
        return cell;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "Некорректная строка" : message;
    }

    private record ParsedRow(long rowNumber, OrderRequest request) { }
    private record ParsedFile(int totalRows, List<ParsedRow> rows, List<RowIssue> issues) { }
}
