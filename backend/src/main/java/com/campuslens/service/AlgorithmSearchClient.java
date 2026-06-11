package com.campuslens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
  private final String baseUrl;

  public AlgorithmSearchClient(
      ObjectMapper objectMapper,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${campuslens.algorithm.base-url:http://localhost:8000}") String baseUrl,
      @Value("${campuslens.algorithm.connect-timeout-ms:3000}") long connectTimeoutMs,
      @Value("${campuslens.algorithm.read-timeout-ms:20000}") long readTimeoutMs) {
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
        .setReadTimeout(Duration.ofMillis(readTimeoutMs))
        .build();
  }

  public AlgorithmSearchResponse search(MultipartFile file) {
    HttpEntity<MultiValueMap<String, Object>> request;
    try {
      request = multipartRequest(file);
    } catch (IOException ex) {
      throw new AlgorithmSearchException("读取上传图片失败", ex);
    }

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
      throw new AlgorithmSearchException(
          "算法服务返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
      throw new AlgorithmSearchException("调用算法服务失败：" + ex.getMessage(), ex);
    }
  }

  public AdaptationResponse submitCorrectionSample(AdaptationRequest payload) {
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
      throw new AlgorithmSearchException(
          "算法服务返回 HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
      throw new AlgorithmSearchException("调用算法校正接口失败：" + ex.getMessage(), ex);
    }
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
