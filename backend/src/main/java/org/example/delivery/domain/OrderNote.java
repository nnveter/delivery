package org.example.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Комментарий диспетчера к заказу доставки. */
@Entity
@Table(name = "order_note")
public class OrderNote extends AuditedVersionedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private DeliveryOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    protected OrderNote() {
    }

    public OrderNote(UUID id, DeliveryOrder order, UserAccount author, String body, boolean pinned) {
        this.id = id;
        this.order = order;
        this.author = author;
        this.body = body;
        this.pinned = pinned;
    }

    public UUID getId() { return id; }
    public DeliveryOrder getOrder() { return order; }
    public UserAccount getAuthor() { return author; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
