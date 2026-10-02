package dev.team1.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.dtos.SalesKpiDTOResponse;
import dev.team1.orders.OrderEntity;

public interface IInvoiceService {
  InvoiceDTOResponse create(InvoiceDTORequest request);
  void createForPaidOrder(OrderEntity order);
  InvoiceDTOResponse findById(Long id);
  PaidInvoiceDTOResponse findPaidById(Long id);
  Page<InvoiceDTOResponse> findAll(Pageable pageable);
  Page<PaidInvoiceDTOResponse> findPaid(Pageable pageable);
  Page<PaidInvoiceDTOResponse> findPaid(String search, Pageable pageable);
  SalesKpiDTOResponse salesKpis();
}
