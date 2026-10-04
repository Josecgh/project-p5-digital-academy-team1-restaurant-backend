package dev.team1.invoices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import dev.team1.invoices.dtos.SalesKpiDTOResponse;
import dev.team1.invoices.exceptions.InvoiceException;
import dev.team1.invoices.exceptions.InvoiceExceptionNotFound;
import dev.team1.orders.OrderEntity;
import dev.team1.tables.TableEntity;
import dev.team1.users.UserEntity;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Madrid");

	@Mock
	private InvoiceRepository invoiceRepository;

	@InjectMocks
	private InvoiceService invoiceService;

	@Test
	void salesSummary_shouldRejectUnsupportedPeriodBeforeQueryingInvoices() {
		InvoiceException exception = assertThrows(InvoiceException.class,
				() -> invoiceService.salesSummary("year"));

		assertEquals("Periodo debe ser dia, semana o mes.", exception.getMessage());
		verifyNoInteractions(invoiceRepository);
	}

	@Test
	void salesSummary_dayShouldAggregatePaidRevenueByChannel() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID), any(Instant.class), any(Instant.class)))
				.thenReturn(List.of(
						paidSale(new BigDecimal("12.35"), today, OrderChannel.SALA),
						paidSale(new BigDecimal("7.65"), today, OrderChannel.DOMICILIO)));

		var result = invoiceService.salesSummary("dia");

		assertEquals("dia", result.period());
		assertEquals(today, result.startDate());
		assertEquals(today, result.endDate());
		assertEquals(2, result.invoiceCount());
		assertEquals(new BigDecimal("20.00"), result.totalRevenue());
		assertEquals(new BigDecimal("12.35"), result.onsiteRevenue());
		assertEquals(new BigDecimal("7.65"), result.deliveryRevenue());
		verify(invoiceRepository).findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID),
				eq(today.atStartOfDay(BUSINESS_ZONE).toInstant()),
				eq(today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()));
	}

	@Test
	void salesSummary_weekShouldCoverMondayThroughSunday() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
			eq(OrderStatus.PAID), any(Instant.class), any(Instant.class)))
				.thenReturn(List.of(paidSale(new BigDecimal("18.00"), today, OrderChannel.SALA)));

		var result = invoiceService.salesSummary("semana");

		assertEquals("semana", result.period());
		assertEquals(monday, result.startDate());
		assertEquals(monday.plusDays(6), result.endDate());
		assertEquals(new BigDecimal("18.00"), result.totalRevenue());
		verify(invoiceRepository).findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID),
				eq(monday.atStartOfDay(BUSINESS_ZONE).toInstant()),
				eq(monday.plusDays(7).atStartOfDay(BUSINESS_ZONE).toInstant()));
	}

	@Test
	void salesSummary_monthShouldCoverFullMonthAndHandleNoSales() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate firstDay = today.withDayOfMonth(1);
		LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
			eq(OrderStatus.PAID), any(Instant.class), any(Instant.class))).thenReturn(List.of());

		var result = invoiceService.salesSummary("mes");

		assertEquals("mes", result.period());
		assertEquals(firstDay, result.startDate());
		assertEquals(lastDay, result.endDate());
		assertEquals(0, result.invoiceCount());
		assertEquals(new BigDecimal("0.00"), result.totalRevenue());
		assertEquals(new BigDecimal("0.00"), result.onsiteRevenue());
		assertEquals(new BigDecimal("0.00"), result.deliveryRevenue());
		verify(invoiceRepository).findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID),
				eq(firstDay.atStartOfDay(BUSINESS_ZONE).toInstant()),
				eq(lastDay.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()));
	}

	@Test
	void createForPaidOrder() {
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
	void skipDuplicate() {
		OrderEntity order = new OrderEntity();
		ReflectionTestUtils.setField(order, "id", 77L);
		when(invoiceRepository.existsByOrder_Id(77L)).thenReturn(true);

		invoiceService.createForPaidOrder(order);

		verify(invoiceRepository).existsByOrder_Id(77L);
		verify(invoiceRepository, never()).save(any(InvoiceEntity.class));
	}

	@Test
	void create() {
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
	void duplicateNumber() {
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
	void findById() {
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
	void missingInvoice() {
		when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findById(404L));

		assertEquals("Invoice 404 not found.", exception.getMessage());
	}

	@Test
	void findAll() {
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
	void emptyList() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.findAll(pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findAll(pageable));

		assertEquals("Not invoices found.", exception.getMessage());
	}

	@Test
	void findPaid() {
		Pageable pageable = PageRequest.of(0, 5);
		UserEntity user = new UserEntity();
		user.setFirstName("Ana");
		user.setLastName("Perez");
		TableEntity table = new TableEntity();
		table.setTableNumber(4);
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setChannel(OrderChannel.SALA);
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
		assertEquals(OrderChannel.SALA, paidInvoice.channel());
		assertEquals(OrderStatus.PAID, paidInvoice.status());
		assertEquals(PaymentMethod.CARD_ONSITE, paidInvoice.paymentMethod());
		verify(invoiceRepository).findByOrder_Status(OrderStatus.PAID, pageable);
	}

	@Test
	void emptyPaidList() {
		Pageable pageable = PageRequest.of(0, 5);
		when(invoiceRepository.findByOrder_Status(OrderStatus.PAID, pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findPaid(pageable));

		assertEquals("Not paid invoices found.", exception.getMessage());
	}

	@Test
	void searchByIdOrTable() {
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
	void searchByCustomer() {
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
	void blankSearch() {
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
	void noSearchMatches() {
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
	void findPaidById() {
		UserEntity user = new UserEntity();
		user.setFirstName("Ana");
		user.setLastName("Perez");
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setChannel(OrderChannel.SALA);
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
		assertEquals(OrderChannel.SALA, response.channel());
		assertEquals(OrderStatus.PAID, response.status());
		assertEquals(PaymentMethod.CARD_ONSITE, response.paymentMethod());
		verify(invoiceRepository).findByIdAndOrder_Status(9L, OrderStatus.PAID);
	}

	@Test
	void missingPaidInvoice() {
		when(invoiceRepository.findByIdAndOrder_Status(404L, OrderStatus.PAID))
				.thenReturn(Optional.empty());

		InvoiceExceptionNotFound exception = assertThrows(
				InvoiceExceptionNotFound.class,
				() -> invoiceService.findPaidById(404L));

		assertEquals("Paid invoice 404 not found.", exception.getMessage());
	}

	@Test
	void salesKpis() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate monthStart = today.withDayOfMonth(1);
		LocalDate quarterStart = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
		List<InvoiceEntity> invoices = List.of(
				paidSale(new BigDecimal("10.00"), today, OrderChannel.SALA),
				paidSale(new BigDecimal("20.00"), today, OrderChannel.DOMICILIO),
				paidSale(new BigDecimal("5.00"), today.minusDays(1), OrderChannel.SALA),
				paidSale(new BigDecimal("7.00"), monthStart.minusMonths(1).plusDays(1), OrderChannel.DOMICILIO),
				paidSale(new BigDecimal("11.00"), quarterStart.minusMonths(3).plusDays(1), OrderChannel.SALA),
				paidSale(new BigDecimal("13.00"), today.withDayOfYear(1).minusYears(1).plusDays(1), OrderChannel.DOMICILIO));
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID), any(Instant.class), any(Instant.class))).thenReturn(invoices);

		SalesKpiDTOResponse result = invoiceService.salesKpis();

		assertEquals(new BigDecimal("30.00"), result.today().amount());
		assertEquals(new BigDecimal("500.00"), result.today().variationPercent());
		assertEquals(new BigDecimal("35.00"), result.month().amount());
		assertEquals(new BigDecimal("400.00"), result.month().variationPercent());
		assertEquals(new BigDecimal("35.00"), result.quarter().amount());
		assertEquals(new BigDecimal("218.18"), result.quarter().variationPercent());
		assertEquals(new BigDecimal("53.00"), result.fiscalYear().amount());
		assertEquals(new BigDecimal("307.69"), result.fiscalYear().variationPercent());
		assertEquals(new BigDecimal("15.00"), result.channelSales().get(0).amount());
		assertEquals(OrderChannel.SALA, result.channelSales().get(0).channel());
		assertEquals(new BigDecimal("42.86"), result.channelSales().get(0).percentage());
		assertEquals(new BigDecimal("20.00"), result.channelSales().get(1).amount());
		assertEquals(OrderChannel.DOMICILIO, result.channelSales().get(1).channel());
		assertEquals(new BigDecimal("57.14"), result.channelSales().get(1).percentage());
		assertEquals(today, result.busiestDay());
		var todaySales = result.weeklySales().stream()
				.filter(day -> day.date().equals(today))
				.findFirst()
				.orElseThrow();
		assertEquals(new BigDecimal("10.00"), todaySales.onsite());
		assertEquals(new BigDecimal("20.00"), todaySales.online());
		assertEquals(new BigDecimal("30.00"), todaySales.total());
	}

	@Test
	void weeklySales() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
		List<InvoiceEntity> invoices = List.of(
				paidSale(new BigDecimal("15.00"), today, OrderChannel.SALA),
				paidSale(new BigDecimal("25.00"), today.minusDays(1), OrderChannel.DOMICILIO));
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID), any(Instant.class), any(Instant.class))).thenReturn(invoices);

		var result = invoiceService.weeklySales();

		assertEquals(weekStart, result.weekStart());
		assertEquals(weekStart.plusDays(6), result.weekEnd());
		assertEquals(7, result.days().size());
		assertEquals(weekStart, result.days().get(0).date());
		assertEquals(weekStart.plusDays(6), result.days().get(6).date());
		assertEquals(today.minusDays(1), result.peakDay());
		var peak = result.days().get((int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, result.peakDay()));
		assertEquals(BigDecimal.ZERO.setScale(2), peak.onsite());
		assertEquals(new BigDecimal("25.00"), peak.online());
		assertEquals(new BigDecimal("25.00"), peak.total());
		var todaySales = result.days().get((int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, today));
		assertEquals(new BigDecimal("15.00"), todaySales.onsite());
		assertEquals(BigDecimal.ZERO.setScale(2), todaySales.online());
		assertEquals(new BigDecimal("15.00"), todaySales.total());
	}

	@Test
	void channelSales() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		List<InvoiceEntity> invoices = List.of(
				paidSale(new BigDecimal("25.00"), today, OrderChannel.SALA),
				paidSale(new BigDecimal("75.00"), today, OrderChannel.DOMICILIO),
				paidSale(new BigDecimal("200.00"), today.minusMonths(1), OrderChannel.DOMICILIO));
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID), any(Instant.class), any(Instant.class))).thenReturn(invoices);

		var result = invoiceService.salesChannelDistribution();

		assertEquals(today.withDayOfMonth(1), result.from());
		assertEquals(today, result.to());
		assertEquals(new BigDecimal("100.00"), result.total());
		assertEquals(OrderChannel.SALA, result.channels().get(0).channel());
		assertEquals(new BigDecimal("25.00"), result.channels().get(0).amount());
		assertEquals(new BigDecimal("25.00"), result.channels().get(0).percentage());
		assertEquals(OrderChannel.DOMICILIO, result.channels().get(1).channel());
		assertEquals(new BigDecimal("75.00"), result.channels().get(1).amount());
		assertEquals(new BigDecimal("75.00"), result.channels().get(1).percentage());
	}

	@Test
	void channelPeriodBounds() {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate monthStart = today.withDayOfMonth(1);
		Instant start = monthStart.atStartOfDay(BUSINESS_ZONE).toInstant();
		Instant end = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
		List<InvoiceEntity> invoices = List.of(
				paidSale(new BigDecimal("10.00"), start, OrderChannel.SALA),
				paidSale(new BigDecimal("20.00"), end.minusNanos(1), OrderChannel.DOMICILIO),
				paidSale(new BigDecimal("999.00"), start.minusNanos(1), OrderChannel.SALA),
				paidSale(new BigDecimal("888.00"), end, OrderChannel.DOMICILIO));
		when(invoiceRepository.findPaidByStatusAndPaidAtRange(
				eq(OrderStatus.PAID), any(Instant.class), any(Instant.class))).thenReturn(invoices);

		var result = invoiceService.salesChannelDistribution();

		assertEquals(new BigDecimal("30.00"), result.total());
		assertEquals(new BigDecimal("10.00"), result.channels().get(0).amount());
		assertEquals(new BigDecimal("33.33"), result.channels().get(0).percentage());
		assertEquals(new BigDecimal("20.00"), result.channels().get(1).amount());
		assertEquals(new BigDecimal("66.67"), result.channels().get(1).percentage());
	}

	private InvoiceEntity paidSale(BigDecimal amount, LocalDate date, OrderChannel channel) {
		return paidSale(amount, date.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant(), channel);
	}

	private InvoiceEntity paidSale(BigDecimal amount, Instant paidAt, OrderChannel channel) {
		InvoiceEntity invoice = invoice(UUID.randomUUID(), amount, paidAt);
		OrderEntity order = new OrderEntity();
		order.setStatus(OrderStatus.PAID);
		order.setChannel(channel);
		invoice.setOrder(order);
		return invoice;
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
