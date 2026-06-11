package com.campuslens.service;

import com.campuslens.model.SearchResponse;
import com.campuslens.model.SearchResult;
import com.campuslens.model.SessionUser;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResponse;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResult;
import com.campuslens.service.SearchRecordService.SearchRecordCreation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SearchService {
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
  private final LandmarkService landmarkService;
  private final AlgorithmSearchClient algorithmSearchClient;
  private final SearchRecordService searchRecordService;
  private final AuthService authService;

  public SearchService(
      LandmarkService landmarkService,
      AlgorithmSearchClient algorithmSearchClient,
      SearchRecordService searchRecordService,
      AuthService authService) {
    this.landmarkService = landmarkService;
    this.algorithmSearchClient = algorithmSearchClient;
    this.searchRecordService = searchRecordService;
    this.authService = authService;
  }

  public SearchResponse search(MultipartFile file, SessionUser user, String guestId) {
    validate(file);
    Long activeUserId = user == null ? null : activeUserId(user.userId());
    String uploadUrl = save(file);
    try {
      AlgorithmSearchResponse algorithmResponse = algorithmSearchClient.search(file);
      List<SearchResult> results = buildAlgorithmResults(algorithmResponse);
      if (results.isEmpty()) {
        return recordedResponse(
            uploadUrl,
            true,
            "算法服务未返回可匹配的 L01-L10 地标，请检查样本索引和 landmarkCode 映射。",
            "empty_result",
            results,
            activeUserId,
            guestId);
      }
      boolean lowConfidence = algorithmResponse.lowConfidence();
      String message = normalizeMessage(algorithmResponse.message());
      SearchRecordCreation record = searchRecordService.create(
          uploadUrl,
          results,
          lowConfidence,
          message,
          lowConfidence ? "low_confidence" : "success",
          activeUserId,
          guestId);
      return new SearchResponse(
          record.id(),
          uploadUrl,
          record.guestId(),
          lowConfidence,
          message,
          results);
    } catch (AlgorithmSearchException ex) {
      return recordedResponse(
          uploadUrl,
          true,
          "算法服务暂不可用，未生成候选地标。原因：" + ex.getMessage(),
          "algorithm_unavailable",
          List.of(),
          activeUserId,
          guestId);
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请上传图片文件");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("图片大小不能超过 8MB");
    }
    if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片");
    }
  }

  private String save(MultipartFile file) {
    String extension = extension(file.getOriginalFilename(), file.getContentType());
    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String filename = UUID.randomUUID() + extension;
    Path uploadDir = Path.of("uploads", date);
    Path target = uploadDir.resolve(filename);
    try {
      Files.createDirectories(uploadDir);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return "/" + target.toString().replace('\\', '/');
    } catch (IOException ex) {
      throw new IllegalArgumentException("上传图片保存失败");
    }
  }

  private String extension(String filename, String contentType) {
    if (filename != null) {
      String lower = filename.toLowerCase();
      if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
        return ".jpg";
      }
      if (lower.endsWith(".png")) {
        return ".png";
      }
      if (lower.endsWith(".webp")) {
        return ".webp";
      }
    }
    return "image/png".equals(contentType) ? ".png" : "image/webp".equals(contentType) ? ".webp" : ".jpg";
  }

  private List<SearchResult> buildAlgorithmResults(AlgorithmSearchResponse response) {
    if (response.results() == null) {
      return List.of();
    }
    return response.results().stream()
        .map(this::toSearchResult)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  private java.util.Optional<SearchResult> toSearchResult(AlgorithmSearchResult result) {
    return landmarkService.findByCode(result.landmarkCode())
        .map(item -> new SearchResult(
            result.rank(),
            item.id(),
            item.code(),
            item.name(),
            item.englishName(),
            clampScore(result.score()),
            normalizeConfidence(result.confidenceLevel()),
            result.mahalanobisDistance(),
            item.coverImageUrl(),
            item.summary(),
            item.locationText(),
            item.mapX(),
            item.mapY()));
  }

  private SearchResponse recordedResponse(
      String uploadUrl,
      boolean lowConfidence,
      String message,
      String status,
      List<SearchResult> results,
      Long userId,
      String guestId) {
    SearchRecordCreation record = searchRecordService.create(uploadUrl, results, lowConfidence, message, status, userId, guestId);
    return new SearchResponse(
        record.id(),
        uploadUrl,
        record.guestId(),
        lowConfidence,
        message,
        results);
  }

  private double clampScore(double score) {
    if (score < 0) {
      return 0;
    }
    if (score > 1) {
      return 1;
    }
    return score;
  }

  private String normalizeConfidence(String confidenceLevel) {
    return switch (confidenceLevel == null ? "" : confidenceLevel.toLowerCase()) {
      case "high", "medium", "low" -> confidenceLevel.toLowerCase();
      default -> "low";
    };
  }

  private String normalizeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "算法服务检索成功";
    }
    if ("Search successful".equalsIgnoreCase(message)) {
      return "算法服务检索成功";
    }
    if ("Low match score, manual verification recommended".equalsIgnoreCase(message)) {
      return "算法匹配等级较低，建议人工核验";
    }
    if ("Low confidence, manual verification recommended".equalsIgnoreCase(message)) {
      return "算法匹配等级较低，建议人工核验";
    }
    return message;
  }

  private Long activeUserId(Long userId) {
    if (userId == null) {
      return null;
    }
    if (!authService.isActiveUser(userId)) {
      throw new IllegalArgumentException("用户不存在或已停用");
    }
    return userId;
  }
}
