package dev.team1.invoices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.team1.contracts.IInvoiceService;
import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.invoices.dtos.InvoiceDTOResponse;
import dev.team1.invoices.dtos.PaidInvoiceDTOResponse;
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
  @WithMockUser(authorities = "ROLE_ADMIN")
  void findPaid_shouldReturnPaidInvoicesPage() throws Exception {
    PaidInvoiceDTOResponse invoice = new PaidInvoiceDTOResponse(
        2L,
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Ana Perez",
        4,
        OrderChannel.ONSITE,
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
}