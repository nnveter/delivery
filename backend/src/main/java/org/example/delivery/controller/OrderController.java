package org.example.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.delivery.dto.ImportDtos.ImportMode;
import org.example.delivery.dto.ImportDtos.ImportPreview;
import org.example.delivery.dto.ImportDtos.ImportResult;
import org.example.delivery.dto.OrderDtos.BulkRequest;
import org.example.delivery.dto.OrderDtos.BulkResult;
import org.example.delivery.dto.OrderDtos.OrderRequest;
import org.example.delivery.dto.OrderDtos.OrderResponse;
import org.example.delivery.dto.PageResponse;
import org.example.delivery.service.CsvOrderService;
import org.example.delivery.service.OrderService;
import org.example.delivery.web.PageableFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/** REST API заказов доставки. */
@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Заказы", description = "Основная сущность: заказы доставки")
public class OrderController {
    private final OrderService orders;
    private final CsvOrderService csv;
    private final PageableFactory pageableFactory;

    public OrderController(OrderService orders, CsvOrderService csv, PageableFactory pageableFactory) {
        this.orders = orders;
        this.csv = csv;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','DISPATCHER','ADMIN')")
    @Operation(summary = "Поиск заказов с фильтрами, сортировкой и пагинацией")
    public PageResponse<OrderResponse> search(
            @Parameter(description = "Умный фильтр, например: «Книга клиент:Иванов статус:в_пути хрупкий:да цена>=500 приоритет:высокий тег:срочно архив:все»")
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Поле и направление: created, number, client, status, price, weight, priority, updated; например price,desc")
            @RequestParam(defaultValue = "created,desc") String sort) {
        return PageResponse.from(orders.search(q, pageableFactory.orders(page, size, sort)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER','DISPATCHER','ADMIN')")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(201).body(orders.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        return orders.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    @Operation(summary = "Архивировать заказ (вместо удаления)")
    public OrderResponse archive(@PathVariable UUID id) {
        return orders.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public OrderResponse restore(@PathVariable UUID id) {
        return orders.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Окончательно удалить заказ (только ADMIN, нужен заголовок X-Confirm-Delete: PERMANENTLY_DELETE)")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Confirm-Delete", required = false) String confirmation) {
        orders.permanentDelete(id, confirmation);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    @Operation(summary = "Массовая операция: ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return orders.bulk(request);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public ImportPreview previewImport(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public ImportResult applyImport(@RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.apply(file, mode);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAnyRole('VIEWER','DISPATCHER','ADMIN')")
    @Operation(summary = "Экспорт заказов в CSV с учётом фильтра q")
    public void export(@RequestParam(defaultValue = "") String q, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders-" + LocalDate.now() + ".csv\"");
        csv.export(q, response.getOutputStream());
    }
}
