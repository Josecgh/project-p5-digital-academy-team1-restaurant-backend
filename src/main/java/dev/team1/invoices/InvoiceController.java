package dev.team1.invoices;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.team1.contracts.IInvoiceService;
import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.dtos.SalesKpiDTOResponse;
import dev.team1.invoices.dtos.SalesChannelDistributionDTOResponse;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping(path = "${api-endpoint}")
public class InvoiceController {
  private final IInvoiceService invoiceService;

  public InvoiceController(IInvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @PostMapping("/invoices")
  public ResponseEntity<InvoiceDTOResponse> create(@Valid @RequestBody InvoiceDTORequest dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(dto));
  }

  @GetMapping("/invoices/{id}")
  public ResponseEntity<InvoiceDTOResponse> findById(@PathVariable Long id) {
    return ResponseEntity.ok(invoiceService.findById(id));
  }

  @GetMapping("/invoices/paid/{id}")
  public ResponseEntity<PaidInvoiceDTOResponse> findPaidById(@PathVariable Long id) {
    return ResponseEntity.ok(invoiceService.findPaidById(id));
  }

  @GetMapping("/invoices")
  public ResponseEntity<Page<InvoiceDTOResponse>> findAll(
    @PageableDefault(size = 5, sort = "paidAt", direction = Sort.Direction.DESC)
    Pageable pageable
  ) {
    return ResponseEntity.ok(invoiceService.findAll(pageable));
  }

  @GetMapping("/invoices/paid")
  public ResponseEntity<Page<PaidInvoiceDTOResponse>> findPaid(
    @PageableDefault(size = 5, sort = "paidAt", direction = Sort.Direction.DESC)
    Pageable pageable
  ) {
      return ResponseEntity.ok(invoiceService.findPaid(pageable));
  }

  @GetMapping("/facturation")
  public ResponseEntity<Page<PaidInvoiceDTOResponse>> facturation(
    @RequestParam(required = false) String search,
    @PageableDefault(size = 5,  sort = "paidAt", direction = Sort.Direction.DESC)
    Pageable pageable
  ) {
    return ResponseEntity.ok(invoiceService.findPaid(search, pageable));
  }

  @GetMapping("/kpi/sales")
  public ResponseEntity<SalesKpiDTOResponse> salesKpis() {
    return ResponseEntity.ok(invoiceService.salesKpis());
  }

  @GetMapping("/kpi/sales/channels")
  public ResponseEntity<SalesChannelDistributionDTOResponse> salesChannelDistribution() {
    return ResponseEntity.ok(invoiceService.salesChannelDistribution());
  }
  
}
