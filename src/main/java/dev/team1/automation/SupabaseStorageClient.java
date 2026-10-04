package dev.team1.automation;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SupabaseStorageClient {
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final String url;
  private final String bucket;
  private final String apiKey;

  public SupabaseStorageClient(
      @Value("${cloud.supabase.url:}") String url,
      @Value("${cloud.supabase.bucket:}") String bucket,
      @Value("${cloud.supabase.api-key:}") String apiKey) {
    this.url = url;
    this.bucket = bucket;
    this.apiKey = apiKey;
  }

  public boolean isConfigured() {
    return !url.isBlank() && !bucket.isBlank() && !apiKey.isBlank();
  }

  public void upload(String path, byte[] content) throws Exception {
    if (!isConfigured()) {
      throw new IllegalStateException("Supabase Storage no está configurado.");
    }
    String encodedPath = java.util.Arrays.stream(path.split("/"))
        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
        .collect(java.util.stream.Collectors.joining("/"));
    URI endpoint = URI.create(url.replaceAll("/$", "") + "/storage/v1/object/"
        + URLEncoder.encode(bucket, StandardCharsets.UTF_8) + "/" + encodedPath);
    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .header("apikey", apiKey)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/pdf")
        .header("x-upsert", "true")
        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
        .build();
    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("Supabase Storage respondió HTTP " + response.statusCode());
    }
  }
}
