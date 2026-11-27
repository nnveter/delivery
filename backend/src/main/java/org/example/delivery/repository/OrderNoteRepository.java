package org.example.delivery.repository;

import org.example.delivery.domain.OrderNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Доступ к комментариям заказов. */
public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {
    @EntityGraph(attributePaths = {"author"})
    List<OrderNote> findByOrderIdOrderByPinnedDescCreatedAtDesc(UUID orderId);
}
