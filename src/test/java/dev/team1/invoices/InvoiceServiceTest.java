package dev.team1.invoices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.exceptions.InvoiceException;
import dev.team1.invoices.exceptions.InvoiceExceptionNotFound;
import dev.team1.orders.OrderEntity;
import dev.team1.tables.TableEntity;
import dev.team1.users.UserEntity;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

	@Mock
	private InvoiceRepository invoiceRepository;

	@InjectMocks
	private InvoiceService invoiceService;

	@Test
	void createForPaidOrder_shouldSaveInvoiceLinkedToOrder() {
		OrderEntity order = new OrderEntity();
		ReflectionTestUtils.setField(order, "id", 77L);
		order.setTotal(new BigDecimal("35.50"));
		when(invoiceRepository.existsByOrder_Id(77L)).thenReturn(false);
		when(invoiceRepository.save(any(InvoiceEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		invoiceService.createForPaidOrder(order);

		ArgumentCaptor<InvoiceEntity> captor = ArgumentCaptor.forClass(InvoiceEntity.class);
		verify(invoiceRepository).save(captor.capture());
		assertSame(order, captor.getValue().getOrder());
		assertEquals(new BigDecimal("35.50"), captor.getValue().getAmount());
		assertNotNull(captor.getValue().getPaidAt());
		verify(invoiceRepository).existsByOrder_Id(77L);
	}

	@Test
	void createForPaidOrder_shouldNotDuplicateInvoice() {
		OrderEntity order = new OrderEntity();
		ReflectionTestUtils.setField(order, "id", 77L);
		when(invoiceRepository.existsByOrder_Id(77L)).thenReturn(true);

		invoiceService.createForPaidOrder(order);

		verify(invoiceRepository).existsByOrder_Id(77L);
		verify(invoiceRepository, never()).save(any(InvoiceEntity.class));
	}

	@Test
	void create_shouldSaveAndReturnInvoice() {
		UUID invoiceNumber = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BigDecimal amount = new BigDecimal("37.50");
		Instant paidAt = Instant.parse("2026-10-01T12:00:00Z");
		InvoiceDTORequest request = new InvoiceDTORequest(invoiceNumber, amount, paidAt);

		when(invoiceRepository.existsByInvoiceNumber(invoiceNumber)).thenReturn(false);
		when(invoiceRepository.save(any(InvoiceEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		InvoiceDTOResponse response = invoiceService.create(request);

		verify(invoiceRepository).existsByInvoiceNumber(invoiceNumber);
		verify(invoiceRepository).save(any(InvoiceEntity.class));
		assertEquals(invoiceNumber, response.invoiceNumber());
		assertEquals(amount, response.amount());
		assertEquals(paidAt, response.paidAt());
	}

	@Test
	void create_shouldThrowConflictWhenInvoiceNumberAlreadyExists() {
		UUID invoiceNumber = UUID.fromString("11111111-1111-1111-1111-111111111111");
		InvoiceDTORequest request = new InvoiceDTORequest(
				invoiceNumber,
				new BigDecimal("37.50"),
				Instant.parse("2026-10-01T12:00:00Z"));
		when(invoiceRepository.existsByInvoiceNumber(invoiceNumber)).thenReturn(true);

		InvoiceException exception = assertThrows(
				InvoiceException.class,
				() -> invoiceService.create(request));

		assertEquals("Invoice number " + invoiceNumber + " already exists.", exception.getMessage());
		verify(invoiceRepository, never()).save(any(InvoiceEntity.class));
	}

	@Test
	void findById_shouldReturnInvoiceWhenFound() {
		UUID invoiceNumber = UUID.fromString("22222222-2222-2222-2222-222222222222");
		InvoiceEntity invoice = invoice(
				invoiceNumber,
				new BigDecimal("22.00"),
				Instant.parse("2026-10-01T12:00:00Z"));
		when(invoiceRepository.findById(8L)).thenReturn(Optional.of(invoice));

		InvoiceDTOResponse response = invoiceService.findById(8L);

		assertEquals(invoiceNumber, response.invoiceNumber());
		assertEquals(new BigDecimal("22.00"), response.amount());
		assertEquals(invoice.getPaidAt(), response.paidAt());
		verify(invoiceRepository).findById(8L);
	}

	@Test
	void findById_shouldThrowNotFoundWhenMissing() {
		when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findById(404L));

		assertEquals("Invoice 404 not found.", exception.getMessage());
	}

	@Test
	void findAll_shouldMapInvoicesToResponsePage() {
		Pageable pageable = PageRequest.of(0, 5);
		InvoiceEntity invoice = invoice(
				UUID.fromString("33333333-3333-3333-3333-333333333333"),
				new BigDecimal("15.00"),
				Instant.parse("2026-10-01T12:00:00Z"));
		when(invoiceRepository.findAll(pageable))
				.thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

		Page<InvoiceDTOResponse> response = invoiceService.findAll(pageable);

		assertEquals(1, response.getTotalElements());
		assertEquals(invoice.getInvoiceNumber(), response.getContent().get(0).invoiceNumber());
		assertEquals(invoice.getAmount(), response.getContent().get(0).amount());
		verify(invoiceRepository).findAll(pageable);
	}

	@Test
	void findAll_shouldThrowNotFoundWhenThereAreNoInvoices() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.findAll(pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findAll(pageable));

		assertEquals("Not invoices found.", exception.getMessage());
	}

	@Test
	void findPaid_shouldReturnPaidInvoicesWithOrderDetails() {
		Pageable pageable = PageRequest.of(0, 5);
		UserEntity user = new UserEntity();
		user.setFirstName("Ana");
		user.setLastName("Perez");
		TableEntity table = new TableEntity();
		table.setTableNumber(4);
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setChannel(OrderChannel.ONSITE);
		order.setPaymentMethod(PaymentMethod.CARD_ONSITE);
		order.setUser(user);
		order.setTable(table);
		InvoiceEntity invoice = invoice(
				UUID.fromString("44444444-4444-4444-4444-444444444444"),
				new BigDecimal("18.00"),
				Instant.parse("2026-10-01T13:00:00Z"));
		invoice.setOrder(order);
		when(invoiceRepository.findByOrder_Status(OrderStatus.PAID, pageable))
				.thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

		Page<PaidInvoiceDTOResponse> response = invoiceService.findPaid(pageable);
		PaidInvoiceDTOResponse paidInvoice = response.getContent().get(0);

		assertEquals(1, response.getTotalElements());
		assertEquals("Ana Perez", paidInvoice.customerName());
		assertEquals(4, paidInvoice.tableNumber());
		assertEquals(OrderChannel.ONSITE, paidInvoice.channel());
		assertEquals(OrderStatus.PAID, paidInvoice.status());
		assertEquals(PaymentMethod.CARD_ONSITE, paidInvoice.paymentMethod());
		verify(invoiceRepository).findByOrder_Status(OrderStatus.PAID, pageable);
	}

	@Test
	void findPaid_shouldThrowNotFoundWhenThereAreNoPaidInvoices() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.findByOrder_Status(OrderStatus.PAID, pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findPaid(pageable));

		assertEquals("Not paid invoices found.", exception.getMessage());
	}

	@Test
	void findPaidWithSearch_shouldSearchByInvoiceIdOrTableNumber() {
		Pageable pageable = PageRequest.of(0, 5);
		InvoiceEntity invoice = invoice(
				UUID.fromString("77777777-7777-7777-7777-777777777777"),
				new BigDecimal("19.00"),
				Instant.parse("2026-10-01T17:00:00Z"));
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		TableEntity table = new TableEntity();
		table.setTableNumber(42);
		order.setTable(table);
		invoice.setOrder(order);
		when(invoiceRepository.searchPaidInvoices(OrderStatus.PAID, 42L, 42, "42", pageable))
				.thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

		Page<PaidInvoiceDTOResponse> response = invoiceService.findPaid(" 42 ", pageable);

		assertEquals(1, response.getTotalElements());
		assertEquals(42, response.getContent().get(0).tableNumber());
		verify(invoiceRepository).searchPaidInvoices(OrderStatus.PAID, 42L, 42, "42", pageable);
	}

	@Test
	void findPaidWithSearch_shouldSearchByCustomerName() {
		Pageable pageable = PageRequest.of(0, 5);
		UserEntity user = new UserEntity();
		user.setFirstName("Ana");
		user.setLastName("Perez");
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setUser(user);
		InvoiceEntity invoice = invoice(
				UUID.fromString("88888888-8888-8888-8888-888888888888"),
				new BigDecimal("21.00"),
				Instant.parse("2026-10-01T18:00:00Z"));
		invoice.setOrder(order);
		when(invoiceRepository.searchPaidInvoices(OrderStatus.PAID, null, null, "Ana", pageable))
				.thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

		Page<PaidInvoiceDTOResponse> response = invoiceService.findPaid(" Ana ", pageable);

		assertEquals("Ana Perez", response.getContent().get(0).customerName());
		verify(invoiceRepository).searchPaidInvoices(OrderStatus.PAID, null, null, "Ana", pageable);
	}

	@Test
	void findPaidWithBlankSearch_shouldSearchWithoutFilters() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.searchPaidInvoices(OrderStatus.PAID, null, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(invoice(
						UUID.fromString("99999999-9999-9999-9999-999999999999"),
						new BigDecimal("12.00"),
						Instant.parse("2026-10-01T19:00:00Z"))), pageable, 1));

		Page<PaidInvoiceDTOResponse> response = invoiceService.findPaid("   ", pageable);

		assertEquals(1, response.getTotalElements());
		verify(invoiceRepository).searchPaidInvoices(OrderStatus.PAID, null, null, null, pageable);
	}

	@Test
	void findPaidWithSearch_shouldThrowNotFoundWhenThereAreNoMatches() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.searchPaidInvoices(OrderStatus.PAID, null, null, "Nobody", pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findPaid("Nobody", pageable));

		assertEquals("Not paid invoices found.", exception.getMessage());
		verify(invoiceRepository).searchPaidInvoices(OrderStatus.PAID, null, null, "Nobody", pageable);
	}

	@Test
	void findPaidById_shouldReturnPaidInvoiceWithOrderDetails() {
		UserEntity user = new UserEntity();
		user.setFirstName("Ana");
		user.setLastName("Perez");
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setChannel(OrderChannel.ONSITE);
		order.setPaymentMethod(PaymentMethod.CARD_ONSITE);
		order.setUser(user);
		InvoiceEntity invoice = invoice(
				UUID.fromString("66666666-6666-6666-6666-666666666666"),
				new BigDecimal("26.00"),
				Instant.parse("2026-10-01T16:00:00Z"));
		invoice.setOrder(order);
		when(invoiceRepository.findByIdAndOrder_Status(9L, OrderStatus.PAID))
				.thenReturn(Optional.of(invoice));

		PaidInvoiceDTOResponse response = invoiceService.findPaidById(9L);

		assertEquals("Ana Perez", response.customerName());
		assertEquals(OrderChannel.ONSITE, response.channel());
		assertEquals(OrderStatus.PAID, response.status());
		assertEquals(PaymentMethod.CARD_ONSITE, response.paymentMethod());
		verify(invoiceRepository).findByIdAndOrder_Status(9L, OrderStatus.PAID);
	}

	@Test
	void findPaidById_shouldThrowNotFoundWhenInvoiceIsMissingOrNotPaid() {
		when(invoiceRepository.findByIdAndOrder_Status(404L, OrderStatus.PAID))
				.thenReturn(Optional.empty());

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findPaidById(404L));

		assertEquals("Paid invoice 404 not found.", exception.getMessage());
	}

	private InvoiceEntity invoice(UUID invoiceNumber, BigDecimal amount, Instant paidAt) {
		InvoiceEntity invoice = InvoiceEntity.builder()
				.amount(amount)
				.paidAt(paidAt)
				.build();
		invoice.setInvoiceNumber(invoiceNumber);
		return invoice;
	}
}
