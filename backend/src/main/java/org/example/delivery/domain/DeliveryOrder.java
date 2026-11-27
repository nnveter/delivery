package org.example.delivery.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OptimisticLock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Заказ доставки — основная сущность системы (таблица {@code delivery_orders} из КР №1).
 *
 * <p>Обычное удаление реализовано через архивирование, поэтому заметки, теги и история
 * изменений сохраняются и запись можно восстановить.</p>
 */
@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder extends AuditedVersionedEntity {
    @Id
    private UUID id;

    /** Человекочитаемый номер заказа (внешний идентификатор), например DLV-000123. */
    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "pickup_address", nullable = false, columnDefinition = "TEXT")
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Вес посылки, кг. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal weight;

    /** Стоимость доставки, руб. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Хрупкий груз — требует бережной перевозки. */
    @Column(nullable = false)
    private boolean fragile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PriorityLevel priority = PriorityLevel.NORMAL;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by")
    private UserAccount archivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserAccount updatedBy;

    /*
     * Коллекция исключена из автоматического optimistic lock: Hibernate иначе повышает version
     * при первой записи тегов уже после формирования ответа. Вместо этого setTags() явно
     * вызывает touch(), и version растёт ровно на 1 при реальном изменении тегов.
     */
    @OptimisticLock(excluded = true)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_tag", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags = new LinkedHashSet<>();

    protected DeliveryOrder() {
    }

    public DeliveryOrder(UUID id, String orderNumber, UserAccount creator) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.createdBy = creator;
        this.updatedBy = creator;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public boolean isFragile() { return fragile; }
    public void setFragile(boolean fragile) { this.fragile = fragile; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = priority; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public UserAccount getArchivedBy() { return archivedBy; }
    public void setArchivedBy(UserAccount archivedBy) { this.archivedBy = archivedBy; }
    public UserAccount getCreatedBy() { return createdBy; }
    public UserAccount getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UserAccount updatedBy) { this.updatedBy = updatedBy; }
    public Set<String> getTags() { return tags; }
    /** Меняет набор тегов на месте, чтобы Hibernate обновлял только изменившиеся строки order_tag. */
    public void setTags(Set<String> tags) {
        boolean changed = this.tags.retainAll(tags);
        changed |= this.tags.addAll(tags);
        if (changed) touch();
    }
}
