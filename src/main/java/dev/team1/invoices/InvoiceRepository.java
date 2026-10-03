package dev.team1.invoices;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.team1.enums.OrderStatus;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

  Optional<InvoiceEntity> findByInvoiceNumber(UUID invoiceNumber);

  boolean existsByInvoiceNumber(UUID invoiceNumber);

  boolean existsByOrder_Id(Long orderId);

  Optional<InvoiceEntity> findByIdAndOrder_Status(Long id, OrderStatus status);

  Page<InvoiceEntity> findByOrder_Status(OrderStatus status, Pageable pageable);

  @Query("""
        SELECT i FROM InvoiceEntity i
        JOIN i.order o
        LEFT JOIN o.user u
        LEFT JOIN o.table t
        WHERE o.status = :status
        AND (
          (:invoiceId IS NULL AND :tableNumber IS NULL AND :customerSearch IS NULL)
          OR (:invoiceId IS NOT NULL AND i.id = :invoiceId)
          OR (:tableNumber IS NOT NULL AND t.tableNumber = :tableNumber)
          OR (
            :customerSearch IS NOT NULL
            AND LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, '')))
              LIKE LOWER(CONCAT('%', :customerSearch, '%'))
          )
        )
  """)
  Page<InvoiceEntity> searchPaidInvoices(
      @Param("status") OrderStatus status,
      @Param("invoiceId") Long invoiceId,
      @Param("tableNumber") Integer tableNumber,
      @Param("customerSearch") String customerSearch,
      Pageable pageable);

  @Query("""
      SELECT i FROM InvoiceEntity i
      JOIN i.order o
      WHERE o.status = :status
      AND i.paidAt >= :from
      AND i.paidAt < :to
      """)
  List<InvoiceEntity> findPaidByStatusAndPaidAtRange(
      @Param("status") OrderStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
