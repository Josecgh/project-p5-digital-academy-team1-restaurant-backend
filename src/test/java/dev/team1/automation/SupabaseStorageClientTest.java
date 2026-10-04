package dev.team1.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SupabaseStorageClientTest {

  @Test
  void upload_whenStorageReturnsNotFoundThrowsAnError() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/storage/v1/object/reports/", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();
    try {
      SupabaseStorageClient client = new SupabaseStorageClient(
          "http://localhost:" + server.getAddress().getPort(), "reports", "test-key");

      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> client.upload("missing/report.pdf", "pdf".getBytes(StandardCharsets.UTF_8)));

      assertTrue(exception.getMessage().contains("HTTP 404"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void upload_whenStorageIsNotConfiguredFailsBeforeMakingRequest() {
    SupabaseStorageClient client = new SupabaseStorageClient("", "", "");

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> client.upload("report.pdf", new byte[0]));

    assertEquals("Supabase Storage no está configurado.", exception.getMessage());
  }
}
