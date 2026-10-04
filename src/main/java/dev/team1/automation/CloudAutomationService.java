package dev.team1.automation;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dev.team1.contracts.IInvoiceService;
import dev.team1.io.PDFExporter;

@Service
public class CloudAutomationService {
  private static final Logger logger = LoggerFactory.getLogger(CloudAutomationService.class);
  private final IInvoiceService invoiceService;
  private final SupabaseStorageClient storageClient;
  private final int retryAttempts;
  private final long retryDelayMs;
  private final ZoneId zone;

  private volatile Instant lastSyncAt;
  private volatile Instant lastAttemptAt;
  private volatile String lastError;

  public CloudAutomationService(IInvoiceService invoiceService, SupabaseStorageClient storageClient,
      @Value("${sales-report.retry-attempts:3}") int retryAttempts,
      @Value("${sales-report.retry-delay-ms:5000}") long retryDelayMs,
      @Value("${sales-report.zone:Europe/Madrid}") String zone) {
    this.invoiceService = invoiceService;
    this.storageClient = storageClient;
    this.retryAttempts = Math.max(1, retryAttempts);
    this.retryDelayMs = Math.max(0, retryDelayMs);
    this.zone = ZoneId.of(zone);
  }

  @Scheduled(cron = "${sales-report.cron:0 0 0 * * *}", zone = "${sales-report.zone:Europe/Madrid}")
  public void generateDailyReport() {
    generateDailyReport(LocalDate.now(zone).minusDays(1));
  }

  public void generateDailyReport(LocalDate date) {
    lastAttemptAt = Instant.now();
    String path = "resumenes-ventas/resumen-ventas-" + date + ".pdf";
    try {
      byte[] pdf = PDFExporter.exportSalesSummary(invoiceService.salesSummary(date));
      Exception failure = null;
      for (int attempt = 1; attempt <= retryAttempts; attempt++) {
        try {
          storageClient.upload(path, pdf);
          lastSyncAt = Instant.now();
          lastError = null;
          logger.info("Resumen diario de ventas {} sincronizado en Supabase Storage", date);
          return;
        } catch (Exception exception) {
          failure = exception;
          logger.warn("Error al subir el resumen de ventas {} (intento {}/{}): {}",
              date, attempt, retryAttempts, exception.getMessage());
          if (attempt < retryAttempts && retryDelayMs > 0) {
            try {
              Thread.sleep(retryDelayMs);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              failure = interrupted;
              break;
            }
          }
        }
      }
      lastError = failure == null ? "No se pudo completar la sincronización." : failure.getMessage();
      logger.error("Falló la sincronización del resumen {} tras {} intentos. Notificar al administrador.",
          date, retryAttempts, failure);
    } catch (IOException exception) {
      lastError = exception.getMessage();
      logger.error("No se pudo generar el PDF del resumen de ventas {}", date, exception);
    } catch (RuntimeException exception) {
      lastError = exception.getMessage();
      logger.error("No se pudo obtener el resumen de ventas {}", date, exception);
    }
  }

  public CloudAutomationStatus status() {
    return new CloudAutomationStatus(
        storageClient.isConfigured() && lastError == null, lastSyncAt, lastAttemptAt, lastError);
  }
}
