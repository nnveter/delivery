package org.example.delivery.repository;

import org.example.delivery.domain.DeliveryOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Доступ к заказам доставки: поиск по Specification и агрегаты для аналитики. */
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, UUID>, JpaSpecificationExecutor<DeliveryOrder> {
    boolean existsByOrderNumberIgnoreCase(String orderNumber);
    boolean existsByOrderNumberIgnoreCaseAndIdNot(String orderNumber, UUID id);
    Optional<DeliveryOrder> findByOrderNumberIgnoreCase(String orderNumber);
    boolean existsByClientId(UUID clientId);

    long countByArchivedFalse();
    long countByArchivedTrue();
    long countByArchivedFalseAndFragileTrue();

    @EntityGraph(attributePaths = {"client"})
    @Override
    Optional<DeliveryOrder> findById(UUID id);


    @Query("select avg(o.price) from DeliveryOrder o where o.archived = false")
    Double averagePriceForActive();

    @Query("select avg(o.weight) from DeliveryOrder o where o.archived = false")
    Double averageWeightForActive();

    @Query("select c.fullName, count(o) from DeliveryOrder o join o.client c " +
           "where o.archived = false group by c.fullName order by count(o) desc")
    List<Object[]> countActiveByClient();

    @Query("select o.status, count(o) from DeliveryOrder o where o.archived = false group by o.status order by count(o) desc")
    List<Object[]> countActiveByStatus();

    @Query("select o.priority, count(o) from DeliveryOrder o where o.archived = false group by o.priority order by count(o) desc")
    List<Object[]> countActiveByPriority();
}
