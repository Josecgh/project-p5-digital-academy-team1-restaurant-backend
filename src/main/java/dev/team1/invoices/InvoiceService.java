package dev.team1.invoices;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import dev.team1.contracts.IInvoiceService;
import dev.team1.enums.OrderStatus;
import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.exceptions.InvoiceException;
import dev.team1.invoices.exceptions.InvoiceExceptionNotFound;
import dev.team1.mappers.InvoiceMapper;
import dev.team1.orders.OrderEntity;

@Service
public class InvoiceService implements IInvoiceService {
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
