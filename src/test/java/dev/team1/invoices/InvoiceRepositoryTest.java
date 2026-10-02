package dev.team1.invoices;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.orders.OrderEntity;
import dev.team1.orders.OrderRepository;
import dev.team1.tables.TableEntity;
import dev.team1.tables.TableRepository;
import dev.team1.users.UserEntity;
import dev.team1.users.UserRepository;

@SpringBootTest
@Transactional
class InvoiceRepositoryTest {

  @Autowired
  private InvoiceRepository invoiceRepository;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private TableRepository tableRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void searchPaidInvoices_shouldMatchInvoiceId() {
    InvoiceEntity invoice = saveInvoice(OrderStatus.PAID, null, null, null,
        Instant.parse("2026-10-01T10:00:00Z"));

    Page<InvoiceEntity> result = invoiceRepository.searchPaidInvoices(
        OrderStatus.PAID,
        invoice.getId(),
        null,
        invoice.getId().toString(),
        PageRequest.of(0, 5));

    assertEquals(1, result.getTotalElements());
    assertEquals(invoice.getId(), result.getContent().get(0).getId());
  }

  @Test
  void searchPaidInvoices_shouldMatchTableNumberAndIgnoreUnpaidOrders() {
    InvoiceEntity paidInvoice = saveInvoice(OrderStatus.PAID, null, null, 42,
        Instant.parse("2026-10-01T10:00:00Z"));
    InvoiceEntity unpaidInvoice = saveInvoice(OrderStatus.PLACED, null, null, null,
        Instant.parse("2026-10-01T11:00:00Z"));
    unpaidInvoice.getOrder().setTable(paidInvoice.getOrder().getTable());
    orderRepository.saveAndFlush(unpaidInvoice.getOrder());

    Page<InvoiceEntity> result = invoiceRepository.searchPaidInvoices(
        OrderStatus.PAID,
        null,
        42,
        "42",
        PageRequest.of(0, 5));

    assertEquals(1, result.getTotalElements());
    assertEquals(paidInvoice.getId(), result.getContent().get(0).getId());
  }

  @Test
  void searchPaidInvoices_shouldMatchCustomerNameIgnoringCase() {
    InvoiceEntity invoice = saveInvoice(OrderStatus.PAID, "Ana", "Perez", null,
        Instant.parse("2026-10-01T10:00:00Z"));

    Page<InvoiceEntity> result = invoiceRepository.searchPaidInvoices(
        OrderStatus.PAID,
        null,
        null,
        "aNA per",
        PageRequest.of(0, 5));

    assertEquals(1, result.getTotalElements());
    assertEquals(invoice.getId(), result.getContent().get(0).getId());
  }

  @Test
  void searchPaidInvoices_shouldPaginateResults() {
    for (int index = 0; index < 6; index++) {
      saveInvoice(OrderStatus.PAID, null, null, null,
          Instant.parse("2026-10-01T10:00:00Z").plusSeconds(index));
    }

    Page<InvoiceEntity> result = invoiceRepository.searchPaidInvoices(
        OrderStatus.PAID,
        null,
        null,
        null,
        PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "paidAt")));

    assertEquals(6, result.getTotalElements());
    assertEquals(2, result.getTotalPages());
    assertEquals(1, result.getNumberOfElements());
  }

  private InvoiceEntity saveInvoice(
      OrderStatus status,
      String firstName,
      String lastName,
      Integer tableNumber,
      Instant paidAt) {
    OrderEntity order = new OrderEntity();
    order.setSubtotal(new BigDecimal("10.00"));
    order.setDiscountAmount(BigDecimal.ZERO);
    order.setVatRate(10);
    order.setVatAmount(BigDecimal.ONE);
    order.setTotal(new BigDecimal("11.00"));
    order.setStatus(status);
    order.setChannel(OrderChannel.ONSITE);
    order.setPaymentMethod(PaymentMethod.CASH_ONSITE);

    if (firstName != null) {
      UserEntity user = new UserEntity();
      user.setFirstName(firstName);
      user.setLastName(lastName);
      user.setEmail(UUID.randomUUID() + "@example.test");
      user.setPassword("test-password");
      user.setAddress("Test address");
      user.setPostalCode("00000");
      user.setCity("Test city");
      order.setUser(userRepository.saveAndFlush(user));
    }

    if (tableNumber != null) {
      TableEntity table = new TableEntity();
      table.setTableNumber(tableNumber);
      table.setDeviceIdentifier("test-device-" + UUID.randomUUID());
      order.setTable(tableRepository.saveAndFlush(table));
    }

    OrderEntity savedOrder = orderRepository.saveAndFlush(order);
    InvoiceEntity invoice = InvoiceEntity.builder()
        .amount(savedOrder.getTotal())
        .paidAt(paidAt)
        .build();
    invoice.setOrder(savedOrder);
    return invoiceRepository.saveAndFlush(invoice);
  }
}
