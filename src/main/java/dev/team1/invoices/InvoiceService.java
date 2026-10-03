package dev.team1.invoices;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.team1.contracts.IInvoiceService;
import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.dtos.SalesKpiDTOResponse;
import dev.team1.invoices.dtos.SalesChannelDistributionDTOResponse;
import dev.team1.invoices.exceptions.InvoiceException;
import dev.team1.invoices.exceptions.InvoiceExceptionNotFound;
import dev.team1.mappers.InvoiceMapper;
import dev.team1.orders.OrderEntity;

@Service
public class InvoiceService implements IInvoiceService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asturias/Oviedo");
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
  private final InvoiceRepository invoiceRepository;

  public InvoiceService(InvoiceRepository invoiceRepository) {
    this.invoiceRepository = invoiceRepository;
  }

  @Override
  @Transactional
  public InvoiceDTOResponse create(InvoiceDTORequest request) {
    if (invoiceRepository.existsByInvoiceNumber(request.invoiceNumber())) {
      throw new InvoiceException(
          "Invoice number " + request.invoiceNumber() + " already exists.");
    }

    InvoiceEntity invoice = InvoiceMapper.toEntity(request);
    invoice.setInvoiceNumber(request.invoiceNumber());

    InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
    return InvoiceMapper.toDTO(savedInvoice);
  }

  @Override
  @Transactional
  public void createForPaidOrder(OrderEntity order) {
    if (invoiceRepository.existsByOrder_Id(order.getId())) {
      return;
    }

    InvoiceEntity invoice = InvoiceEntity.builder()
        .amount(order.getTotal())
        .paidAt(Instant.now())
        .build();
    invoice.setOrder(order);
    invoiceRepository.save(invoice);
  }

  @Override
  @Transactional(readOnly = true)
  public InvoiceDTOResponse findById(Long id) {
    InvoiceEntity invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new InvoiceExceptionNotFound(
            "Invoice " + id + " not found."));

    return InvoiceMapper.toDTO(invoice);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<InvoiceDTOResponse> findAll(Pageable pageable) {
    Page<InvoiceEntity> invoices = invoiceRepository.findAll(pageable);
    if (invoices.getTotalElements() == 0) {
      throw new InvoiceExceptionNotFound("Not invoices found.");
    }

    return invoices.map(InvoiceMapper::toDTO);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PaidInvoiceDTOResponse> findPaid(Pageable pageable) {
    Page<InvoiceEntity> paidInvoices = invoiceRepository
      .findByOrder_Status(OrderStatus.PAID, pageable);
    if (paidInvoices.getTotalElements() == 0) {
      throw new InvoiceExceptionNotFound("Not paid invoices found.");
    }

    return paidInvoices.map(InvoiceMapper::toPaidDTO);
  }

  @Override
  @Transactional(readOnly = true)
  public PaidInvoiceDTOResponse findPaidById(Long id) {
    InvoiceEntity invoice = invoiceRepository
      .findByIdAndOrder_Status(id, OrderStatus.PAID)
      .orElseThrow(() -> new InvoiceExceptionNotFound(
        "Paid invoice " + id + " not found."));

    return InvoiceMapper.toPaidDTO(invoice);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PaidInvoiceDTOResponse> findPaid(String search, Pageable pageable) {
    String customerSearch = search == null ? null : search.trim();
    if (customerSearch != null && customerSearch.isEmpty()) {
      customerSearch = null;
    }

    Long invoiceId = parseLongOrNull(customerSearch);
    Integer tableNumber = parseIntegerOrNull(customerSearch);

    Page<InvoiceEntity> paidInvoices = invoiceRepository.searchPaidInvoices(
        OrderStatus.PAID,
        invoiceId,
        tableNumber,
        customerSearch,
        pageable);

    if (paidInvoices.getTotalElements() == 0) {
      throw new InvoiceExceptionNotFound("Not paid invoices found.");
    }

    return paidInvoices.map(InvoiceMapper::toPaidDTO);
  }

  @Override
  @Transactional(readOnly = true)
  public SalesKpiDTOResponse salesKpis() {
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    LocalDate yearStart = today.withDayOfYear(1);
    LocalDate previousYearStart = yearStart.minusYears(1);
    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
    Instant end = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    List<InvoiceEntity> invoices = invoiceRepository
        .findByOrder_StatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            OrderStatus.PAID, previousYearStart.atStartOfDay(BUSINESS_ZONE).toInstant(), end);

    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate quarterStart = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
    long dayCount = ChronoUnit.DAYS.between(yearStart, today) + 1;

    var todayMetric = metric(invoices, today, today.plusDays(1), today.minusDays(1), today);
    var monthMetric = metric(invoices, monthStart, today.plusDays(1), monthStart.minusMonths(1),
        monthStart.minusMonths(1).plusDays(ChronoUnit.DAYS.between(monthStart, today) + 1));
    var quarterMetric = metric(invoices, quarterStart, today.plusDays(1), quarterStart.minusMonths(3),
        quarterStart.minusMonths(3).plusDays(ChronoUnit.DAYS.between(quarterStart, today) + 1));
    var yearMetric = metric(invoices, yearStart, today.plusDays(1), previousYearStart,
        previousYearStart.plusDays(dayCount));

    List<SalesKpiDTOResponse.ChannelSales> channelSales = channelSales(
        invoices, monthStart, today.plusDays(1));
    List<SalesKpiDTOResponse.DailySales> weeklySales = new ArrayList<>();
    LocalDate busiestDay = null;
    BigDecimal busiestAmount = BigDecimal.valueOf(-1);
    for (int i = 0; i < 7; i++) {
      LocalDate date = weekStart.plusDays(i);
      BigDecimal onsite = sum(invoices, date, date.plusDays(1), OrderChannel.ONSITE);
      BigDecimal online = sum(invoices, date, date.plusDays(1), OrderChannel.ONLINE);
      BigDecimal total = onsite.add(online);
      weeklySales.add(new SalesKpiDTOResponse.DailySales(date, onsite, online, total));
      if (total.compareTo(busiestAmount) > 0) {
        busiestAmount = total;
        busiestDay = date;
      }
    }

    return new SalesKpiDTOResponse(todayMetric, monthMetric, quarterMetric, yearMetric,
        channelSales, weeklySales, busiestAmount.signum() == 0 ? null : busiestDay);
  }

  @Override
  @Transactional(readOnly = true)
  public SalesChannelDistributionDTOResponse salesChannelDistribution() {
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    LocalDate from = today.withDayOfMonth(1);
    LocalDate to = today.plusDays(1);
    List<InvoiceEntity> invoices = invoiceRepository
        .findByOrder_StatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            OrderStatus.PAID,
            from.atStartOfDay(BUSINESS_ZONE).toInstant(),
            to.atStartOfDay(BUSINESS_ZONE).toInstant());
    List<SalesKpiDTOResponse.ChannelSales> channels = channelSales(invoices, from, to);
    BigDecimal total = channels.stream().map(SalesKpiDTOResponse.ChannelSales::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    return new SalesChannelDistributionDTOResponse(from, today, total, channels);
  }

  private SalesKpiDTOResponse.Metric metric(List<InvoiceEntity> invoices,
      LocalDate currentStart, LocalDate currentEnd, LocalDate previousStart, LocalDate previousEnd) {
    BigDecimal current = sum(invoices, currentStart, currentEnd, channel -> true);
    BigDecimal previous = sum(invoices, previousStart, previousEnd, channel -> true);
    BigDecimal variation = previous.signum() == 0
        ? (current.signum() == 0 ? BigDecimal.ZERO : null)
        : current.subtract(previous).multiply(BigDecimal.valueOf(100))
            .divide(previous, 2, RoundingMode.HALF_UP);
    return new SalesKpiDTOResponse.Metric(current, variation);
  }

  private List<SalesKpiDTOResponse.ChannelSales> channelSales(List<InvoiceEntity> invoices,
      LocalDate from, LocalDate to) {
    Map<OrderChannel, BigDecimal> amounts = invoices.stream()
        .filter(invoice -> inRange(invoice, from, to))
        .collect(Collectors.groupingBy(invoice -> invoice.getOrder().getChannel(),
            Collectors.mapping(InvoiceEntity::getAmount,
                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return List.of(OrderChannel.ONSITE, OrderChannel.ONLINE).stream()
        .map(channel -> {
          BigDecimal amount = amounts.getOrDefault(channel, ZERO).setScale(2, RoundingMode.HALF_UP);
          BigDecimal percentage = total.signum() == 0 ? BigDecimal.ZERO
              : amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
          return new SalesKpiDTOResponse.ChannelSales(channel, amount, percentage);
        }).toList();
  }

  private BigDecimal sum(List<InvoiceEntity> invoices, LocalDate from, LocalDate to,
      Predicate<OrderChannel> channelFilter) {
    return invoices.stream().filter(invoice -> inRange(invoice, from, to))
        .filter(invoice -> channelFilter.test(invoice.getOrder().getChannel()))
        .map(InvoiceEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal sum(List<InvoiceEntity> invoices, LocalDate from, LocalDate to, OrderChannel channel) {
    return sum(invoices, from, to, candidate -> candidate == channel);
  }

  private boolean inRange(InvoiceEntity invoice, LocalDate from, LocalDate to) {
    Instant paidAt = invoice.getPaidAt();
    return !paidAt.isBefore(from.atStartOfDay(BUSINESS_ZONE).toInstant())
        && paidAt.isBefore(to.atStartOfDay(BUSINESS_ZONE).toInstant());
  }

  private Long parseLongOrNull(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Integer parseIntegerOrNull(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
