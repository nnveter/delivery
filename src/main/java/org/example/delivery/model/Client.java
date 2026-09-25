package org.example.delivery.model;

import java.util.Objects;

public final class Client {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String address;

    public Client(Long id, String fullName, String phone, String email, String address) {
        this.id = id;
        this.fullName = requireText(fullName, "ФИО клиента");
        this.phone = requireText(phone, "Телефон клиента");
        this.email = email == null ? null : email.trim();
        this.address = requireText(address, "Адрес клиента");
    }

    public Client(String fullName, String phone, String email, String address) {
        this(null, fullName, phone, email, address);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " не может быть пустым");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "Client{id=%d, fullName='%s', phone='%s', email='%s', address='%s'}"
                .formatted(id, fullName, phone, email, address);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Client client)) {
            return false;
        }
        return Objects.equals(id, client.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
