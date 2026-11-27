package org.example.delivery.repository;

import org.example.delivery.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Доступ к справочнику клиентов. */
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByPhone(String phone);
    Optional<Client> findByEmailIgnoreCase(String email);
    List<Client> findAllByOrderByFullNameAsc();
    List<Client> findByActiveTrueOrderByFullNameAsc();
}
