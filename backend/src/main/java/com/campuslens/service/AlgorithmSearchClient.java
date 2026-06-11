package com.campuslens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlgorithmSearchClient {
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final List<String> baseUrls;
  private final AtomicInteger nextUrl = new AtomicInteger();
  private final Map<String, Long> unhealthyUntil = new ConcurrentHashMap<>();
  private final long unhealthyCooldownMs;

  public AlgorithmSearchClient(
      ObjectMapper objectMapper,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${campuslens.algorithm.base-urls:${campuslens.algorithm.base-url:http://localhost:8000}}") String baseUrls,
      @Value("${campuslens.algorithm.connect-timeout-ms:3000}") long connectTimeoutMs,
      @Value("${campuslens.algorithm.read-timeout-ms:20000}") long readTimeoutMs,
      @Value("${campuslens.algorithm.unhealthy-cooldown-ms:10000}") long unhealthyCooldownMs) {
    this.objectMapper = objectMapper;
    this.baseUrls = Arrays.stream(baseUrls.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.replaceAll("/+$", ""))
        .toList();
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
        .setReadTimeout(Duration.ofMillis(readTimeoutMs))
        .build();
    this.unhealthyCooldownMs = unhealthyCooldownMs;
  }

  public AlgorithmSearchResponse search(MultipartFile file) {
    HttpEntity<MultiValueMap<String, Object>> request;
    try {
      request = multipartRequest(file);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("读取上传图片失败", ex);
    }

    String baseUrl = nextBaseUrl();
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/v1/search",
          request,
          String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new AlgorithmSearchException(
            "算法服务返回 HTTP " + response.getStatusCode().value() + ": " + response.getBody());
      }
      return objectMapper.readValue(response.getBody(), AlgorithmSearchResponse.class);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("解析算法服务响应失败", ex);
    } catch (HttpStatusCodeException ex) {
      markUnhealthyOnServerError(baseUrl, ex);
      throw new AlgorithmSearchException(
          "算法服务返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
      markUnhealthy(baseUrl);
      throw new AlgorithmSearchException("调用算法服务失败：" + ex.getMessage(), ex);
    }
  }

  public List<AlgorithmBatchItem> searchBatch(List<Path> paths) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    try {
      for (Path path : paths) {
        byte[] bytes = Files.readAllBytes(path);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return path.getFileName().toString();
          }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        body.add("files", new HttpEntity<>(resource, fileHeaders));
      }
    } catch (IOException ex) {
      throw new AlgorithmSearchException("读取排队图片失败", ex);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    String baseUrl = nextBaseUrl();
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/v1/search/batch",
          new HttpEntity<>(body, headers),
          String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new AlgorithmSearchException("算法批量接口返回 HTTP " + response.getStatusCode().value());
      }
      AlgorithmBatchResponse batch = objectMapper.readValue(response.getBody(), AlgorithmBatchResponse.class);
      if (batch.items() == null || batch.items().size() != paths.size()) {
        throw new AlgorithmSearchException("算法批量响应数量与请求不一致");
      }
      return batch.items();
    } catch (IOException ex) {
      throw new AlgorithmSearchException("解析算法批量响应失败", ex);
    } catch (HttpStatusCodeException ex) {
      markUnhealthyOnServerError(baseUrl, ex);
      throw new AlgorithmSearchException(
          "算法批量接口返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
      markUnhealthy(baseUrl);
      throw new AlgorithmSearchException("调用算法批量接口失败：" + ex.getMessage(), ex);
    }
  }

  public AdaptationResponse submitCorrectionSample(AdaptationRequest payload) {
    String baseUrl = nextBaseUrl();
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/v1/adaptation/correction-samples",
          payload,
          String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new AlgorithmSearchException(
            "算法服务返回 HTTP " + response.getStatusCode().value() + ": " + response.getBody());
      }
      return objectMapper.readValue(response.getBody(), AdaptationResponse.class);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("解析算法校正响应失败", ex);
    } catch (HttpStatusCodeException ex) {
      markUnhealthyOnServerError(baseUrl, ex);
      throw new AlgorithmSearchException(
          "算法服务返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
      markUnhealthy(baseUrl);
      throw new AlgorithmSearchException("调用算法校正接口失败：" + ex.getMessage(), ex);
    }
  }

  private String nextBaseUrl() {
    if (baseUrls.isEmpty()) {
      throw new AlgorithmSearchException("未配置算法服务地址");
    }
    int start = Math.floorMod(nextUrl.getAndIncrement(), baseUrls.size());
    long now = System.currentTimeMillis();
    for (int offset = 0; offset < baseUrls.size(); offset++) {
      String candidate = baseUrls.get((start + offset) % baseUrls.size());
      if (unhealthyUntil.getOrDefault(candidate, 0L) <= now) {
        return candidate;
      }
    }
    return baseUrls.get(start);
  }

  private void markUnhealthyOnServerError(String baseUrl, HttpStatusCodeException ex) {
    if (ex.getStatusCode().is5xxServerError()) {
      markUnhealthy(baseUrl);
    }
  }

  private void markUnhealthy(String baseUrl) {
    unhealthyUntil.put(baseUrl, System.currentTimeMillis() + unhealthyCooldownMs);
  }

  private HttpEntity<MultiValueMap<String, Object>> multipartRequest(MultipartFile file) throws IOException {
    String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
    String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();

    ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
      @Override
      public String getFilename() {
        return filename;
      }

      @Override
      public long contentLength() {
        return file.getSize();
      }
    };

    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.parseMediaType(contentType));
    HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(body, headers);
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

  public record AlgorithmBatchResponse(List<AlgorithmBatchItem> items) {}

  public record AlgorithmBatchItem(
      boolean success,
      AlgorithmSearchResponse response,
      String errorCode,
      String message,
      boolean retryable) {}

  public record AdaptationRequest(
      Long sampleId,
      Long feedbackId,
      Long searchRecordId,
      String imageUrl,
      String confirmedLandmarkCode,
      String predictedLandmarkCode,
      String feedbackType,
      String comment,
      List<AdaptationTopResult> topResults) {
  }

  public record AdaptationTopResult(
      int rank,
      String landmarkCode,
      double score,
      Double mahalanobisDistance) {
  }

  public record AdaptationResponse(
      boolean suggestAccept,
      double reviewScore,
      String reason,
      boolean sarEligible,
      String nextAction) {
  }
}
