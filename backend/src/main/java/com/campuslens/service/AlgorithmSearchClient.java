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
    return search(file, false);
  }

  public AlgorithmSearchResponse search(MultipartFile file, boolean sarMode) {
    HttpEntity<MultiValueMap<String, Object>> request;
    try {
      request = multipartRequest(file, sarMode);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("读取上传图片失败", ex);
    }

    AlgorithmSearchException last = null;
    for (String baseUrl : candidateUrls()) {
      try {
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/search", request, String.class);
        return objectMapper.readValue(response.getBody(), AlgorithmSearchResponse.class);
      } catch (IOException ex) {
        throw new AlgorithmSearchException("解析算法服务响应失败", ex);
      } catch (HttpStatusCodeException ex) {
        if (!ex.getStatusCode().is5xxServerError()) {
          throw new AlgorithmSearchException(
              "算法服务返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
        }
        markUnhealthy(baseUrl);
        last = new AlgorithmSearchException("算法实例 " + baseUrl + " 返回 HTTP " + ex.getStatusCode().value(), ex);
      } catch (RestClientException ex) {
        markUnhealthy(baseUrl);
        last = new AlgorithmSearchException("调用算法实例 " + baseUrl + " 失败：" + ex.getMessage(), ex);
      }
    }
    throw last == null ? new AlgorithmSearchException("没有可用的算法实例") : last;
  }

  public List<AlgorithmBatchItem> searchBatch(List<Path> paths) {
    return searchBatch(paths, false);
  }

  public List<AlgorithmBatchItem> searchBatch(List<Path> paths, boolean sarMode) {
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
    body.add("sarMode", String.valueOf(sarMode));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    AlgorithmSearchException last = null;
    for (String baseUrl : candidateUrls()) {
      try {
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/search/batch", new HttpEntity<>(body, headers), String.class);
        AlgorithmBatchResponse batch = objectMapper.readValue(response.getBody(), AlgorithmBatchResponse.class);
        if (batch.items() == null || batch.items().size() != paths.size()) {
          throw new AlgorithmSearchException("算法批量响应数量与请求不一致");
        }
        return batch.items();
      } catch (IOException ex) {
        throw new AlgorithmSearchException("解析算法批量响应失败", ex);
      } catch (HttpStatusCodeException ex) {
        if (!ex.getStatusCode().is5xxServerError()) {
          throw new AlgorithmSearchException(
              "算法批量接口返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
        }
        markUnhealthy(baseUrl);
        last = new AlgorithmSearchException("算法实例 " + baseUrl + " 返回 HTTP " + ex.getStatusCode().value(), ex);
      } catch (RestClientException ex) {
        markUnhealthy(baseUrl);
        last = new AlgorithmSearchException("调用算法实例 " + baseUrl + " 失败：" + ex.getMessage(), ex);
      }
    }
    throw last == null ? new AlgorithmSearchException("没有可用的算法实例") : last;
  }

  public AdaptationResponse submitCorrectionSample(AdaptationRequest payload, Path imagePath) {
    String baseUrl = primaryBaseUrl();
    try {
      byte[] imageBytes = Files.readAllBytes(imagePath);
      ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
        @Override
        public String getFilename() {
          return imagePath.getFileName().toString();
        }
      };
      HttpHeaders imageHeaders = new HttpHeaders();
      imageHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new HttpEntity<>(imageResource, imageHeaders));
      HttpHeaders payloadHeaders = new HttpHeaders();
      payloadHeaders.setContentType(MediaType.TEXT_PLAIN);
      body.add("payload", new HttpEntity<>(objectMapper.writeValueAsString(payload), payloadHeaders));
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/v1/adaptation/correction-samples",
          new HttpEntity<>(body, headers),
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

  private String primaryBaseUrl() {
    if (baseUrls.isEmpty()) {
      throw new AlgorithmSearchException("未配置算法服务地址");
    }
    return baseUrls.get(0);
  }

  private List<String> candidateUrls() {
    if (baseUrls.isEmpty()) {
      throw new AlgorithmSearchException("未配置算法服务地址");
    }
    long now = System.currentTimeMillis();
    List<String> healthy = baseUrls.stream()
        .filter(url -> unhealthyUntil.getOrDefault(url, 0L) <= now)
        .toList();
    if (!healthy.isEmpty()) {
      return healthy;
    }
    return baseUrls;
  }

  private void markUnhealthyOnServerError(String baseUrl, HttpStatusCodeException ex) {
    if (ex.getStatusCode().is5xxServerError()) {
      markUnhealthy(baseUrl);
    }
  }

  private void markUnhealthy(String baseUrl) {
    unhealthyUntil.put(baseUrl, System.currentTimeMillis() + unhealthyCooldownMs);
  }

  public Map<String, Object> runtimeStatus() {
    return getJson("/api/v1/runtime/status");
  }

  public Map<String, Object> startIndexRebuild() {
    String baseUrl = primaryBaseUrl();
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/v1/index/rebuild", HttpEntity.EMPTY, String.class);
      return objectMapper.readValue(response.getBody(), Map.class);
    } catch (IOException | RestClientException ex) {
      throw new AlgorithmSearchException("启动索引重建失败：" + ex.getMessage(), ex);
    }
  }

  public Map<String, Object> indexRebuildStatus(String jobId) {
    return getJson("/api/v1/index/rebuild/" + jobId);
  }

  private Map<String, Object> getJson(String path) {
    String baseUrl = primaryBaseUrl();
    try {
      String body = restTemplate.getForObject(baseUrl + path, String.class);
      return objectMapper.readValue(body, Map.class);
    } catch (IOException | RestClientException ex) {
      throw new AlgorithmSearchException("读取算法运行状态失败：" + ex.getMessage(), ex);
    }
  }

  private HttpEntity<MultiValueMap<String, Object>> multipartRequest(MultipartFile file, boolean sarMode) throws IOException {
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
    body.add("sarMode", String.valueOf(sarMode));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(body, headers);
  }

  public record AlgorithmSearchResponse(
      List<AlgorithmSearchResult> results,
      boolean lowConfidence,
      String message,
      Boolean sarApplied,
      String trustLevel,
      String modelVersion,
      String baseModelVersion,
      String indexVersion,
      String sarStateVersion,
      String instanceId,
      String instanceRole) {
    public AlgorithmSearchResponse(List<AlgorithmSearchResult> results, boolean lowConfidence, String message) {
      this(results, lowConfidence, message, false, null, null, null, null, null, null, null);
    }
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
      String nextAction,
      String modelVersion,
      boolean activated,
      String adaptationError) {
  }
}
