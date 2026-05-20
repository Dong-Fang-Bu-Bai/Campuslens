package com.campuslens.service;

import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.SearchResponse;
import com.campuslens.model.SearchResult;
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
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SearchService {
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
  private final LandmarkService landmarkService;
  private final AtomicLong searchRecordId = new AtomicLong(1);

  public SearchService(LandmarkService landmarkService) {
    this.landmarkService = landmarkService;
  }

  public SearchResponse search(MultipartFile file) {
    validate(file);
    String uploadUrl = save(file);
    List<SearchResult> results = buildDemoResults();
    return new SearchResponse(
        searchRecordId.getAndIncrement(),
        uploadUrl,
        false,
        "当前为初始阶段演示结果，第二周接入 DINOv2 + FAISS 算法服务。",
        results);
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

  private List<SearchResult> buildDemoResults() {
    List<LandmarkDetail> candidates = landmarkService.topCandidates();
    double[] scores = {0.92, 0.87, 0.81, 0.76, 0.71};
    return java.util.stream.IntStream.range(0, candidates.size())
        .mapToObj(i -> {
          LandmarkDetail item = candidates.get(i);
          return new SearchResult(
              i + 1,
              item.id(),
              item.code(),
              item.name(),
              item.englishName(),
              scores[i],
              item.coverImageUrl(),
              item.summary(),
              item.locationText(),
              item.mapX(),
              item.mapY());
        })
        .toList();
  }
}
