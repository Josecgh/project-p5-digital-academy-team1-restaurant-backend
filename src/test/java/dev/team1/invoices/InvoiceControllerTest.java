package dev.team1.invoices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.team1.contracts.IInvoiceService;
import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.invoices.dtos.InvoiceDTORequest;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
import dev.team1.invoices.dtos.SalesSummaryDTOResponse;
import dev.team1.invoices.exceptions.InvoiceExceptionNotFound;
import dev.team1.invoices.exceptions.InvoiceException;
import dev.team1.security.JwtFilter;
import dev.team1.security.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;


@WebMvcTest(controllers = InvoiceController.class)
@Import(SecurityConfiguration.class)
class InvoiceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private IInvoiceService invoiceService;

  @MockitoBean
  private JwtFilter jwtFilter;

  @BeforeEach
  void bypassJwtFilter() throws Exception {
    doAnswer(invocation -> {
      ServletRequest request = invocation.getArgument(0);
      ServletResponse response = invocation.getArgument(1);
      FilterChain chain = invocation.getArgument(2);
      chain.doFilter(request, response);
      return null;
    }).when(jwtFilter).doFilter(any(), any(), any());
  }

  @Test
  @WithMockUser (roles = "ADMIN")
  void findAll_shouldReturnInvoicesPage() throws Exception {
    InvoiceDTOResponse invoice = InvoiceDTOResponse.builder()
        .id(1L)
        .orderId(12L)
        .invoiceNumber(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        .amount(new BigDecimal("24.50"))
        .paidAt(Instant.parse("2026-10-01T12:00:00Z"))
        .build();
    Page<InvoiceDTOResponse> invoices = new PageImpl<>(List.of(invoice));
    when(invoiceService.findAll(any(Pageable.class))).thenReturn(invoices);

    mockMvc.perform(get("/api/v1/invoices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.content[0].orderId").value(12))
        .andExpect(jsonPath("$.content[0].amount").value(24.50))
        .andExpect(jsonPath("$.totalElements").value(1));

    verify(invoiceService).findAll(any(Pageable.class));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void findById_shouldReturnNotFoundWhenInvoiceDoesNotExist() throws Exception {
    when(invoiceService.findById(404L))
        .thenThrow(new InvoiceExceptionNotFound("Invoice 404 not found."));

    mockMvc.perform(get("/api/v1/invoices/{id}", 404L))
        .andExpect(status().isNotFound())
        .andExpect(content().string("Invoice 404 not found."));

    verify(invoiceService).findById(404L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void findPaidById_shouldReturnNotFoundWhenInvoiceDoesNotExist() throws Exception {
    when(invoiceService.findPaidById(404L))
        .thenThrow(new InvoiceExceptionNotFound("Paid invoice 404 not found."));

    mockMvc.perform(get("/api/v1/invoices/paid/{id}", 404L))
        .andExpect(status().isNotFound())
        .andExpect(content().string("Paid invoice 404 not found."));

    verify(invoiceService).findPaidById(404L);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void create_shouldRejectInvalidInvoiceData() throws Exception {
    mockMvc.perform(post("/api/v1/invoices")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"invoiceNumber":"33333333-3333-3333-3333-333333333333","amount":0,"paidAt":"2026-10-01T14:00:00Z"}
                """))
        .andExpect(status().isBadRequest());

    verify(invoiceService, never()).create(any(InvoiceDTORequest.class));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void create_shouldRejectAmountWithMoreThanTwoDecimals() throws Exception {
    mockMvc.perform(post("/api/v1/invoices")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"invoiceNumber":"33333333-3333-3333-3333-333333333333","amount":12.345,"paidAt":"2026-10-01T14:00:00Z"}
                """))
        .andExpect(status().isBadRequest());

    verify(invoiceService, never()).create(any(InvoiceDTORequest.class));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void create_shouldRejectFuturePaidAt() throws Exception {
    mockMvc.perform(post("/api/v1/invoices")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"invoiceNumber":"33333333-3333-3333-3333-333333333333","amount":12.34,"paidAt":"2099-10-01T14:00:00Z"}
                """))
        .andExpect(status().isBadRequest());

    verify(invoiceService, never()).create(any(InvoiceDTORequest.class));
  }

  @Test
  @WithMockUser(authorities = "ROLE_ADMIN")
  void findPaid_shouldReturnPaidInvoicesPage() throws Exception {
    PaidInvoiceDTOResponse invoice = new PaidInvoiceDTOResponse(
        2L,
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Ana Perez",
        4,
        OrderChannel.SALA,
        new BigDecimal("18.00"),
        OrderStatus.PAID,
        PaymentMethod.CARD_ONSITE,
        Instant.parse("2026-10-01T13:00:00Z"));
    Page<PaidInvoiceDTOResponse> invoices = new PageImpl<>(List.of(invoice));
    when(invoiceService.findPaid(any(Pageable.class))).thenReturn(invoices);

    mockMvc.perform(get("/api/v1/invoices/paid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(2))
        .andExpect(jsonPath("$.content[0].customerName").value("Ana Perez"))
        .andExpect(jsonPath("$.content[0].tableNumber").value(4))
        .andExpect(jsonPath("$.content[0].status").value("PAID"))
        .andExpect(jsonPath("$.totalElements").value(1));

    verify(invoiceService).findPaid(any(Pageable.class));
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void findAll_shouldRejectNonAdmin() throws Exception {
    mockMvc.perform(get("/api/v1/invoices"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_CUSTOMER")
  void facturation_shouldRejectNonAdmin() throws Exception {
    mockMvc.perform(get("/api/v1/facturation"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void salesKpis_shouldRejectNonAdmin() throws Exception {
    mockMvc.perform(get("/api/v1/kpi/sales"))
        .andExpect(status().isForbidden());

    verify(invoiceService, never()).salesKpis();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void salesReport_shouldGeneratePdfForDayWeekAndMonth() throws Exception {
    for (String period : List.of("dia", "semana", "mes")) {
      when(invoiceService.salesSummary(period)).thenReturn(new SalesSummaryDTOResponse(
          period,
          LocalDate.of(2026, 10, 1),
          LocalDate.of(2026, 10, 4),
          3,
          new BigDecimal("90.00"),
          new BigDecimal("60.00"),
          new BigDecimal("30.00")));

      mockMvc.perform(get("/api/v1/reportes/ventas.pdf").param("periodo", period))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_PDF))
          .andExpect(header().string("Content-Disposition",
              "attachment; filename=\"resumen-ventas-2026-10-04.pdf\""));

      verify(invoiceService).salesSummary(period);
    }
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void salesReport_shouldRejectUnsupportedPeriod() throws Exception {
    when(invoiceService.salesSummary("year"))
        .thenThrow(new InvoiceException("Periodo debe ser dia, semana o mes."));

    mockMvc.perform(get("/api/v1/reportes/ventas.pdf").param("periodo", "year"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Periodo debe ser dia, semana o mes."));

    verify(invoiceService).salesSummary("year");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void salesReport_shouldReturnNotFoundWhenSummaryResourceDoesNotExist() throws Exception {
    when(invoiceService.salesSummary("mes"))
        .thenThrow(new InvoiceExceptionNotFound("Sales summary not found."));

    mockMvc.perform(get("/api/v1/reportes/ventas.pdf").param("periodo", "mes"))
        .andExpect(status().isNotFound())
        .andExpect(content().string("Sales summary not found."));

    verify(invoiceService).salesSummary("mes");
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void salesReport_shouldRejectInsufficientPermissionsOnBothRoutes() throws Exception {
    mockMvc.perform(get("/api/v1/reportes/ventas.pdf"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/kpi/sales/report"))
        .andExpect(status().isForbidden());

    verify(invoiceService, never()).salesSummary(any());
  }
}
