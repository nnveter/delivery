package org.example.delivery.service;

import org.example.delivery.config.AppProperties;
import org.example.delivery.domain.Client;
import org.example.delivery.domain.DeliveryOrder;
import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.UserAccount;
import org.example.delivery.dto.OrderDtos.BulkAction;
import org.example.delivery.dto.OrderDtos.BulkFailure;
import org.example.delivery.dto.OrderDtos.BulkRequest;
import org.example.delivery.dto.OrderDtos.BulkResult;
import org.example.delivery.dto.OrderDtos.OrderRequest;
import org.example.delivery.dto.OrderDtos.OrderResponse;
import org.example.delivery.error.BadRequestException;
import org.example.delivery.error.ConflictException;
import org.example.delivery.error.NotFoundException;
import org.example.delivery.filter.OrderSpecifications;
import org.example.delivery.filter.SmartFilterParser;
import org.example.delivery.repository.ClientRepository;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.example.delivery.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Основной прикладной сервис заказов доставки. */
@Service
public class OrderService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeliveryOrderRepository orders;
    private final ClientRepository clients;
    private final CurrentUserService currentUser;
    private final SmartFilterParser parser;
    private final OrderMapper mapper;
    private final PriorityCalculator priorityCalculator;
    private final AuditService audit;
    private final AppProperties properties;

    public OrderService(DeliveryOrderRepository orders, ClientRepository clients,
                        CurrentUserService currentUser, SmartFilterParser parser, OrderMapper mapper,
                        PriorityCalculator priorityCalculator, AuditService audit, AppProperties properties) {
        this.orders = orders;
        this.clients = clients;
        this.currentUser = currentUser;
        this.parser = parser;
        this.mapper = mapper;
        this.priorityCalculator = priorityCalculator;
        this.audit = audit;
        this.properties = properties;
    }

    /** Выполняет безопасный умный поиск с пагинацией. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> search(String query, Pageable pageable) {
        return orders.findAll(OrderSpecifications.from(parser.parse(query)), pageable).map(mapper::toResponse);
    }

    /** Возвращает одну карточку независимо от архивного признака. */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    /** Создаёт заказ; номер генерируется, если не передан, приоритет рассчитывается сервером. */
    @Transactional
    public OrderResponse create(OrderRequest request) {
        UserAccount actor = currentUser.requireEntity();
        DeliveryOrder order = newOrder(request, actor);
        orders.save(order);
        audit.record("ORDER_CREATED", "ORDER", order.getId(),
                "Создан заказ " + order.getOrderNumber(), Map.of());
        orders.flush();
        return mapper.toResponse(order);
    }

    /** Изменяет заказ только при совпадении optimistic version. */
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        DeliveryOrder order = require(id);
        verifyVersion(request.version(), order.getVersion());
        String number = request.orderNumber() == null || request.orderNumber().isBlank()
                ? order.getOrderNumber() : normalizeNumber(request.orderNumber());
        validateNumberUnique(number, id);
        order.setOrderNumber(number);
        apply(order, request, currentUser.requireEntity());
        audit.record("ORDER_UPDATED", "ORDER", id,
                "Изменён заказ " + order.getOrderNumber(), Map.of("version", order.getVersion()));
        orders.flush();
        return mapper.toResponse(order);
    }

    /** Архивирует заказ вместо физического удаления. */
    @Transactional
    public OrderResponse archive(UUID id) {
        DeliveryOrder order = require(id);
        if (order.isArchived()) throw new BadRequestException("Заказ уже находится в архиве");
        archiveInternal(order, currentUser.requireEntity());
        audit.record("ORDER_ARCHIVED", "ORDER", id, "Заказ " + order.getOrderNumber() + " перемещён в архив", Map.of());
        orders.flush();
        return mapper.toResponse(order);
    }

    /** Возвращает заказ из архива. */
    @Transactional
    public OrderResponse restore(UUID id) {
        DeliveryOrder order = require(id);
        if (!order.isArchived()) throw new BadRequestException("Заказ не находится в архиве");
        restoreInternal(order, currentUser.requireEntity());
        audit.record("ORDER_RESTORED", "ORDER", id, "Заказ " + order.getOrderNumber() + " восстановлен из архива", Map.of());
        orders.flush();
        return mapper.toResponse(order);
    }

    /** Безвозвратно удаляет заказ (только ADMIN, при включённом флаге и явном подтверждении). */
    @Transactional
    public void permanentDelete(UUID id, String confirmation) {
        if (!properties.permanentDeleteEnabled()) {
            throw new BadRequestException("Безвозвратное удаление отключено конфигурацией сервера");
        }
        if (!"PERMANENTLY_DELETE".equals(confirmation)) {
            throw new BadRequestException("Для удаления требуется заголовок X-Confirm-Delete: PERMANENTLY_DELETE");
        }
        DeliveryOrder order = require(id);
        String number = order.getOrderNumber();
        orders.delete(order);
        audit.record("ORDER_PERMANENTLY_DELETED", "ORDER", id, "Безвозвратно удалён заказ " + number, Map.of());
    }

    /** Выполняет массовую операцию и сообщает ошибки по каждому UUID отдельно. */
    @Transactional
    public BulkResult bulk(BulkRequest request) {
        validateBulkRequest(request);
        UserAccount actor = currentUser.requireEntity();
        int succeeded = 0;
        var failures = new ArrayList<BulkFailure>();
        for (UUID id : request.ids()) {
            DeliveryOrder order = orders.findById(id).orElse(null);
            if (order == null) {
                failures.add(new BulkFailure(id, "Заказ не найден"));
                continue;
            }
            try {
                applyBulk(order, request, actor);
                succeeded++;
            } catch (BadRequestException ex) {
                failures.add(new BulkFailure(id, order.getOrderNumber() + ": " + ex.getMessage()));
            }
        }
        audit.record("ORDER_BULK_" + request.action().name(), "ORDER", null,
                "Массовая операция " + request.action().name(),
                Map.of("requested", request.ids().size(), "succeeded", succeeded));
        return new BulkResult(request.ids().size(), succeeded, failures);
    }

    /** Используется CSV-импортом внутри его транзакции, чтобы не дублировать правила. */
    DeliveryOrder upsertFromImport(DeliveryOrder existing, OrderRequest request, UserAccount actor) {
        if (existing == null) {
            return orders.save(newOrder(request, actor));
        }
        apply(existing, request, actor);
        return existing;
    }

    private DeliveryOrder newOrder(OrderRequest request, UserAccount actor) {
        String number = request.orderNumber() == null || request.orderNumber().isBlank()
                ? generateNumber() : normalizeNumber(request.orderNumber());
        validateNumberUnique(number, null);
        DeliveryOrder order = new DeliveryOrder(UUID.randomUUID(), number, actor);
        order.setStatus(request.status());
        apply(order, request, actor);
        return order;
    }

    private void apply(DeliveryOrder order, OrderRequest request, UserAccount actor) {
        changeStatus(order, request.status());
        order.setClient(resolveClient(request.clientId(), order.getClient()));
        order.setPickupAddress(normalizeRequired(request.pickupAddress()));
        order.setDeliveryAddress(normalizeRequired(request.deliveryAddress()));
        // Бизнес-правило КР №1: адреса отправления и доставки не могут совпадать.
        if (order.getPickupAddress().equalsIgnoreCase(order.getDeliveryAddress())) {
            throw new BadRequestException("Адреса отправления и доставки не могут совпадать");
        }
        order.setDescription(normalizeNullable(request.description()));
        order.setWeight(request.weight());
        order.setPrice(request.price());
        order.setFragile(request.fragile());
        order.setTags(normalizeTags(request.tags()));
        recalculate(order);
        order.setUpdatedBy(actor);
    }

    private void applyBulk(DeliveryOrder order, BulkRequest request, UserAccount actor) {
        switch (request.action()) {
            case ARCHIVE -> {
                if (order.isArchived()) throw new BadRequestException("уже в архиве");
                archiveInternal(order, actor);
            }
            case RESTORE -> {
                if (!order.isArchived()) throw new BadRequestException("не находится в архиве");
                restoreInternal(order, actor);
            }
            case SET_STATUS -> {
                changeStatus(order, request.status());
                recalculate(order);
                order.setUpdatedBy(actor);
            }
            case ADD_TAG -> {
                Set<String> tags = new LinkedHashSet<>(order.getTags());
                tags.add(normalizeTag(request.tag()));
                if (tags.size() > 20) throw new BadRequestException("допустимо не более 20 тегов");
                order.setTags(tags);
                order.setUpdatedBy(actor);
            }
            case REMOVE_TAG -> {
                Set<String> tags = new LinkedHashSet<>(order.getTags());
                if (!tags.remove(normalizeTag(request.tag()))) throw new BadRequestException("тег отсутствует");
                order.setTags(tags);
                order.setUpdatedBy(actor);
            }
        }
    }

    /** Бизнес-правило КР №1: статус меняется только по допустимым переходам. */
    private static void changeStatus(DeliveryOrder order, DeliveryStatus target) {
        if (target == null) throw new BadRequestException("Статус обязателен");
        if (order.getStatus() == target) return;
        if (order.getCreatedAt() != null && !order.getStatus().canTransitionTo(target)) {
            throw new BadRequestException("Недопустимый переход статуса: %s → %s".formatted(order.getStatus(), target));
        }
        order.setStatus(target);
    }

    private void recalculate(DeliveryOrder order) {
        order.setPriority(priorityCalculator.calculate(order.getStatus(), order.getPrice(), order.getWeight(), order.isFragile()));
    }

    private static void validateBulkRequest(BulkRequest request) {
        if (request.action() == BulkAction.SET_STATUS && request.status() == null) {
            throw new BadRequestException("Для SET_STATUS требуется status");
        }
        if ((request.action() == BulkAction.ADD_TAG || request.action() == BulkAction.REMOVE_TAG)
                && (request.tag() == null || request.tag().isBlank())) {
            throw new BadRequestException("Для операции с тегом требуется tag");
        }
    }

    private static void archiveInternal(DeliveryOrder order, UserAccount actor) {
        order.setArchived(true);
        order.setArchivedAt(Instant.now());
        order.setArchivedBy(actor);
        order.setUpdatedBy(actor);
    }

    private static void restoreInternal(DeliveryOrder order, UserAccount actor) {
        order.setArchived(false);
        order.setArchivedAt(null);
        order.setArchivedBy(null);
        order.setUpdatedBy(actor);
    }

    private DeliveryOrder require(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    private Client resolveClient(UUID id, Client current) {
        if (id == null) throw new BadRequestException("Клиент обязателен");
        if (current != null && current.getId().equals(id)) return current;
        Client client = clients.findById(id).orElseThrow(() -> new BadRequestException("Клиент не найден"));
        if (!client.isActive()) throw new BadRequestException("Нельзя назначить заказ отключённому клиенту");
        return client;
    }

    private void validateNumberUnique(String number, UUID currentId) {
        boolean exists = currentId == null
                ? orders.existsByOrderNumberIgnoreCase(number)
                : orders.existsByOrderNumberIgnoreCaseAndIdNot(number, currentId);
        if (exists) throw new ConflictException("Номер заказа уже используется");
    }

    private String generateNumber() {
        byte[] bytes = new byte[4];
        String number;
        do {
            RANDOM.nextBytes(bytes);
            number = "DLV-" + HexFormat.of().withUpperCase().formatHex(bytes);
        } while (orders.existsByOrderNumberIgnoreCase(number));
        return number;
    }

    private static void verifyVersion(Long requested, long actual) {
        if (requested == null) throw new BadRequestException("Для изменения требуется поле version");
        if (requested != actual) throw new ConflictException("Заказ уже изменён другим пользователем. Обновите данные и повторите");
    }

    static String normalizeNumber(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) result.add(normalizeTag(tag));
        if (result.size() > 20) throw new BadRequestException("Допустимо не более 20 тегов");
        return result;
    }

    private static String normalizeTag(String value) {
        String normalized = normalizeRequired(value).toLowerCase(Locale.ROOT);
        if (normalized.length() > 50) throw new BadRequestException("Тег длиннее 50 символов");
        return normalized;
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) throw new BadRequestException("Обязательное текстовое поле не заполнено");
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().replaceAll("\\s+", " ");
    }
}
