package org.example.delivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTO-контракты справочника клиентов. */
public final class ClientDtos {
    private ClientDtos() {
    }

    public record ClientRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "телефон: 10–15 цифр, допускается + в начале")
            String phone,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 500) String address,
            Boolean active,
            Long version) {
    }

    public record ClientResponse(
            UUID id,
            String fullName,
            String phone,
            String email,
            String address,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
