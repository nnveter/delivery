package org.example.delivery.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.delivery.domain.Role;
import org.example.delivery.domain.DeliveryOrder;
import org.example.delivery.domain.OrderNote;
import org.example.delivery.domain.UserAccount;
import org.example.delivery.dto.NoteDtos.NoteRequest;
import org.example.delivery.dto.NoteDtos.NoteResponse;
import org.example.delivery.error.ConflictException;
import org.example.delivery.error.NotFoundException;
import org.example.delivery.repository.OrderNoteRepository;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.example.delivery.security.CurrentUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Управляет комментариями к заказам и авторскими правами на их изменение. */
@Service
public class NoteService {
    private final OrderNoteRepository notes;
    private final DeliveryOrderRepository orders;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public NoteService(OrderNoteRepository notes, DeliveryOrderRepository orders,
                       CurrentUserService currentUser, AuditService audit) {
        this.notes = notes;
        this.orders = orders;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID orderId) {
        requireOrder(orderId);
        return notes.findByOrderIdOrderByPinnedDescCreatedAtDesc(orderId).stream().map(NoteService::toResponse).toList();
    }

    @Transactional
    public NoteResponse create(UUID orderId, NoteRequest request) {
        DeliveryOrder order = requireOrder(orderId);
        UserAccount actor = currentUser.requireEntity();
        OrderNote note = new OrderNote(UUID.randomUUID(), order, actor, request.body().trim(), request.pinned());
        notes.save(note);
        audit.record("NOTE_CREATED", "ORDER_NOTE", note.getId(), "Добавлена заметка к заказу", Map.of("orderId", orderId));
        notes.flush();
        return toResponse(note);
    }

    @Transactional
    public NoteResponse update(UUID orderId, UUID noteId, NoteRequest request) {
        OrderNote note = requireNote(orderId, noteId);
        UserAccount actor = currentUser.requireEntity();
        assertOwnerOrAdmin(note, actor);
        if (request.version() == null || request.version() != note.getVersion())
            throw new ConflictException("Заметка уже изменена. Обновите данные");
        note.setBody(request.body().trim());
        note.setPinned(request.pinned());
        audit.record("NOTE_UPDATED", "ORDER_NOTE", noteId, "Изменена заметка", Map.of("orderId", orderId));
        notes.flush();
        return toResponse(note);
    }

    @Transactional
    public void delete(UUID orderId, UUID noteId) {
        OrderNote note = requireNote(orderId, noteId);
        UserAccount actor = currentUser.requireEntity();
        assertOwnerOrAdmin(note, actor);
        notes.delete(note);
        audit.record("NOTE_DELETED", "ORDER_NOTE", noteId, "Удалена заметка", Map.of("orderId", orderId));
    }

    private DeliveryOrder requireOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    private OrderNote requireNote(UUID orderId, UUID noteId) {
        OrderNote note = notes.findById(noteId).orElseThrow(() -> new NotFoundException("Заметка не найдена"));
        if (!note.getOrder().getId().equals(orderId)) throw new NotFoundException("Заметка не найдена");
        return note;
    }

    private static void assertOwnerOrAdmin(OrderNote note, UserAccount actor) {
        if (!note.getAuthor().getId().equals(actor.getId()) && !actor.getRoles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Изменять комментарий может только автор или администратор");
        }
    }

    private static NoteResponse toResponse(OrderNote note) {
        return new NoteResponse(note.getId(), note.getOrder().getId(), note.getAuthor().getId(),
                note.getAuthor().getDisplayName(), note.getBody(), note.isPinned(),
                note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }
}
