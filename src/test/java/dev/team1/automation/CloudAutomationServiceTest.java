package dev.team1.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.team1.contracts.IInvoiceService;
import dev.team1.invoices.dtos.SalesSummaryDTOResponse;

@ExtendWith(MockitoExtension.class)
class CloudAutomationServiceTest {
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 3);

  @Mock
  private IInvoiceService invoiceService;
  @Mock
  private SupabaseStorageClient storageClient;
  @Mock
  private AdminFailureNotifier adminFailureNotifier;

  private CloudAutomationService automationService;

  @BeforeEach
  void setUp() {
    automationService = new CloudAutomationService(
        invoiceService, storageClient, adminFailureNotifier, 3, 0, "Europe/Madrid");
    lenient().when(invoiceService.salesSummary(REPORT_DATE)).thenReturn(summary(REPORT_DATE));
  }

  @Test
  void generateDailyReport_uploadsPdfAndRecordsSuccess() throws Exception {
    when(storageClient.isConfigured()).thenReturn(true);

    automationService.generateDailyReport(REPORT_DATE);

    verify(invoiceService).salesSummary(REPORT_DATE);
    verify(storageClient).upload(
        org.mockito.ArgumentMatchers.eq("resumenes-ventas/resumen-ventas-" + REPORT_DATE + ".pdf"),
        any(byte[].class));
    verify(adminFailureNotifier, never()).notifyFailure(any(), anyString(), anyString());
    CloudAutomationStatus status = automationService.status();
    assertEquals("SUCCESS", status.lastResult());
    assertEquals(true, status.online());
    assertNotNull(status.lastSyncAt());
    assertNotNull(status.lastAttemptAt());
  }

  @Test
  void generateDailyReport_retriesUploadAndSucceedsOnSecondAttempt() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    doAnswer(invocation -> {
      if (attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("temporary storage error");
      }
      return null;
    }).when(storageClient).upload(anyString(), any(byte[].class));
    when(storageClient.isConfigured()).thenReturn(true);

    automationService.generateDailyReport(REPORT_DATE);

    assertEquals(2, attempts.get());
    assertEquals("SUCCESS", automationService.status().lastResult());
    verify(adminFailureNotifier, never()).notifyFailure(any(), anyString(), anyString());
  }

  @Test
  void generateDailyReport_notifiesAdminAfterAllUploadAttemptsFail() throws Exception {
    doAnswer(invocation -> {
      throw new IllegalStateException("storage unavailable");
    }).when(storageClient).upload(anyString(), any(byte[].class));

    automationService.generateDailyReport(REPORT_DATE);

    verify(storageClient, org.mockito.Mockito.times(3)).upload(anyString(), any(byte[].class));
    verify(adminFailureNotifier).notifyFailure(
        REPORT_DATE, "subir el PDF tras 3 intentos", "storage unavailable");
    assertEquals("FAILED", automationService.status().lastResult());
    assertEquals("No se pudo subir el PDF tras 3 intentos: storage unavailable",
        automationService.status().lastError());
  }

  @Test
  void generateDailyReport_notifiesAdminWhenSummaryGenerationFails() throws Exception {
    when(invoiceService.salesSummary(REPORT_DATE)).thenThrow(new IllegalStateException("database down"));

    automationService.generateDailyReport(REPORT_DATE);

    verify(storageClient, never()).upload(anyString(), any(byte[].class));
    verify(adminFailureNotifier).notifyFailure(
        REPORT_DATE, "obtener el resumen de ventas", "database down");
    assertEquals("FAILED", automationService.status().lastResult());
  }

  @Test
  void generateDailyReport_withNullDateFailsAndNotifiesAdmin() throws Exception {
    automationService.generateDailyReport(null);

    assertEquals("FAILED", automationService.status().lastResult());
    assertNotNull(automationService.status().lastAttemptAt());
    verify(storageClient, never()).upload(anyString(), any(byte[].class));
    verify(adminFailureNotifier).notifyFailure(
        isNull(), org.mockito.ArgumentMatchers.eq("obtener el resumen de ventas"), anyString());
  }

  @Test
  void generateDailyReport_keepsFailureStatusWhenNotificationAlsoFails() throws Exception {
    doAnswer(invocation -> {
      throw new IllegalStateException("storage unavailable");
    }).when(storageClient).upload(anyString(), any(byte[].class));
    doAnswer(invocation -> {
      throw new IllegalStateException("webhook unavailable");
    }).when(adminFailureNotifier).notifyFailure(any(), anyString(), anyString());

    automationService.generateDailyReport(REPORT_DATE);

    assertEquals("FAILED", automationService.status().lastResult());
    assertEquals("No se pudo subir el PDF tras 3 intentos: storage unavailable",
        automationService.status().lastError());
  }

  @Test
  void generateDailyReport_withZeroRetryConfigurationStillAttemptsOnce() throws Exception {
    CloudAutomationService serviceWithInvalidRetryCount = new CloudAutomationService(
        invoiceService, storageClient, adminFailureNotifier, 0, 0, "Europe/Madrid");
    doAnswer(invocation -> {
      throw new IllegalStateException("storage unavailable");
    }).when(storageClient).upload(anyString(), any(byte[].class));

    serviceWithInvalidRetryCount.generateDailyReport(REPORT_DATE);

    verify(storageClient).upload(anyString(), any(byte[].class));
    verify(adminFailureNotifier).notifyFailure(
        REPORT_DATE, "subir el PDF tras 1 intentos", "storage unavailable");
    assertEquals("FAILED", serviceWithInvalidRetryCount.status().lastResult());
  }

  private SalesSummaryDTOResponse summary(LocalDate date) {
    return new SalesSummaryDTOResponse(
        "dia", date, date, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
  }
}
