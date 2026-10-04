package dev.team1.automation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sends a failure notification to the administrator's configured webhook. */
@Component
public class AdminFailureNotifier {
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private final String webhook;

  public AdminFailureNotifier(@Value("${sales-report.admin-webhook:}") String webhook) {
    this.webhook = webhook == null ? "" : webhook.trim();
  }

  public void notifyFailure(LocalDate date, String operation, String reason) throws Exception {
    if (webhook.isBlank()) {
      throw new IllegalStateException("No está configurado SALES_REPORT_ADMIN_WEBHOOK.");
    }
    String message = "Falló el informe diario de ventas del " + date + " al " + operation
        + ". Error: " + reason;
    String payload = "{\"text\":\"" + escapeJson(message) + "\"}";
    HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("Webhook de administración respondió HTTP "
          + response.statusCode());
    }
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
  }
}
