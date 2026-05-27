package com.campuslens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlgorithmSearchClient {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String baseUrl;

  public AlgorithmSearchClient(
      ObjectMapper objectMapper,
      @Value("${campuslens.algorithm.base-url:http://localhost:8000}") String baseUrl) {
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
  }

  public AlgorithmSearchResponse search(MultipartFile file) {
    String boundary = "CampusLensBoundary" + UUID.randomUUID();
    HttpRequest request;
    try {
      request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/v1/search"))
          .timeout(Duration.ofSeconds(20))
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofByteArrays(multipartBody(file, boundary)))
          .build();
    } catch (IOException ex) {
      throw new AlgorithmSearchException("读取上传图片失败", ex);
    }

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new AlgorithmSearchException("算法服务返回 HTTP " + response.statusCode());
      }
      return objectMapper.readValue(response.body(), AlgorithmSearchResponse.class);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("解析算法服务响应失败", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AlgorithmSearchException("调用算法服务被中断", ex);
    }
  }

  private List<byte[]> multipartBody(MultipartFile file, String boundary) throws IOException {
    String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    String header = "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename.replace("\"", "") + "\"\r\n"
        + "Content-Type: " + contentType + "\r\n\r\n";
    String footer = "\r\n--" + boundary + "--\r\n";
    return List.of(
        header.getBytes(StandardCharsets.UTF_8),
        file.getBytes(),
        footer.getBytes(StandardCharsets.UTF_8));
  }

  public record AlgorithmSearchResponse(
      List<AlgorithmSearchResult> results,
      boolean lowConfidence,
      String message) {
  }

  public record AlgorithmSearchResult(
      int rank,
      String landmarkCode,
      String landmarkName,
      double score,
      String confidenceLevel,
      Double mahalanobisDistance) {
  }
}
