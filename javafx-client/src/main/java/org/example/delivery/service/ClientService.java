package org.example.delivery.service;

import org.example.delivery.exception.BusinessException;
import org.example.delivery.exception.EntityNotFoundException;
import org.example.delivery.model.Client;
import org.example.delivery.repository.ClientRepository;

import java.util.List;

public final class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client create(String fullName, String phone, String email, String address) {
        return clientRepository.save(new Client(fullName, phone, email, address));
    }

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    public Client findById(long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с ID " + id + " не найден."));
    }

    public Client update(long id, String fullName, String phone, String email, String address) {
        findById(id);
        return clientRepository.update(new Client(id, fullName, phone, email, address));
    }

    public void delete(long id) {
        findById(id);
        clientRepository.deleteById(id);
    }

    public List<Client> search(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException("Строка поиска не может быть пустой.");
        }
        return clientRepository.search(query);
    }
}
