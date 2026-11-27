package org.example.delivery.service;

import org.example.delivery.domain.Client;
import org.example.delivery.dto.ClientDtos.ClientRequest;
import org.example.delivery.dto.ClientDtos.ClientResponse;
import org.example.delivery.error.BadRequestException;
import org.example.delivery.error.ConflictException;
import org.example.delivery.error.NotFoundException;
import org.example.delivery.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Операции над справочником клиентов. Физическое удаление не предусмотрено. */
@Service
public class ClientService {
    private final ClientRepository clients;
    private final AuditService audit;

    public ClientService(ClientRepository clients, AuditService audit) {
        this.clients = clients;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(boolean activeOnly) {
        return (activeOnly ? clients.findByActiveTrueOrderByFullNameAsc() : clients.findAllByOrderByFullNameAsc())
                .stream().map(ClientService::toResponse).toList();
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        String phone = normalizePhone(request.phone());
        String email = normalizeEmail(request.email());
        ensureUnique(phone, email, null);
        Client client = new Client(UUID.randomUUID(), normalizeRequired(request.fullName()), phone, email,
                normalizeRequired(request.address()));
        if (request.active() != null) client.setActive(request.active());
        clients.save(client);
        audit.record("CLIENT_CREATED", "CLIENT", client.getId(), "Создан клиент " + client.getFullName(), Map.of());
        clients.flush();
        return toResponse(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = require(id);
        if (request.version() == null || request.version() != client.getVersion())
            throw new ConflictException("Клиент уже изменён. Обновите список");
        String phone = normalizePhone(request.phone());
        String email = normalizeEmail(request.email());
        ensureUnique(phone, email, id);
        client.setFullName(normalizeRequired(request.fullName()));
        client.setPhone(phone);
        client.setEmail(email);
        client.setAddress(normalizeRequired(request.address()));
        if (request.active() != null) client.setActive(request.active());
        audit.record("CLIENT_UPDATED", "CLIENT", id, "Изменён клиент " + client.getFullName(), Map.of());
        clients.flush();
        return toResponse(client);
    }

    @Transactional
    public ClientResponse setActive(UUID id, boolean active, long version) {
        Client client = require(id);
        if (version != client.getVersion()) throw new ConflictException("Клиент уже изменён. Обновите список");
        client.setActive(active);
        audit.record(active ? "CLIENT_ACTIVATED" : "CLIENT_DEACTIVATED", "CLIENT", id,
                (active ? "Включён" : "Отключён") + " клиент " + client.getFullName(), Map.of());
        clients.flush();
        return toResponse(client);
    }

    private void ensureUnique(String phone, String email, UUID currentId) {
        clients.findByPhone(phone).filter(other -> !other.getId().equals(currentId)).ifPresent(other -> {
            throw new ConflictException("Клиент с таким телефоном уже существует");
        });
        if (email != null) {
            clients.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(currentId)).ifPresent(other -> {
                throw new ConflictException("Клиент с таким email уже существует");
            });
        }
    }

    private Client require(UUID id) {
        return clients.findById(id).orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    static ClientResponse toResponse(Client c) {
        return new ClientResponse(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getAddress(),
                c.isActive(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Телефон обязателен");
        return value.trim();
    }

    private static String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Обязательное поле не заполнено");
        return value.trim().replaceAll("\\s+", " ");
    }
}
