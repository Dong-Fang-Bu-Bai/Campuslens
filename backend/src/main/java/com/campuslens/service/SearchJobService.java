package com.campuslens.service;

import com.campuslens.model.SearchJobStatus;
import com.campuslens.model.SearchJobSubmission;
import com.campuslens.model.SessionUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SearchJobService {
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
  private final SearchJobRepository repository;
  private final SearchQueue queue;
  private final AuthService authService;
  private final IndexRebuildService indexRebuildService;
  private final GuestIdentityService guestIdentityService;
  private final String tokenSecret;

  public SearchJobService(
      SearchJobRepository repository,
      SearchQueue queue,
      AuthService authService,
      IndexRebuildService indexRebuildService,
      GuestIdentityService guestIdentityService,
      @Value("${campuslens.search.job-token-secret:campuslens-local-job-secret}") String tokenSecret) {
    this.repository = repository;
    this.queue = queue;
    this.authService = authService;
    this.indexRebuildService = indexRebuildService;
    this.guestIdentityService = guestIdentityService;
    this.tokenSecret = tokenSecret;
  }

  public SearchJobSubmission submit(
      MultipartFile file,
      SessionUser user,
      String guestId,
      String idempotencyKey,
      boolean sarMode) {
    if (indexRebuildService.isMaintenance()) {
      throw new SearchMaintenanceException("索引正在切换，请稍后重试");
    }
    validate(file);
    Long userId = user == null ? null : activeUserId(user.userId());
    String key = normalizeIdempotencyKey(idempotencyKey);
    String requestedGuestId = userId == null
        ? guestIdentityService.requireExisting(guestId)
        : "user-" + userId;
    String scopedKey = sha256((userId == null ? "guest:" + requestedGuestId : "user:" + userId)
        + ":" + key + ":sar=" + sarMode);
    String fileSha = sha256(file);
    var existing = repository.findByIdempotencyKey(scopedKey);
    if (existing.isPresent()) {
      if (!existing.get().fileSha256().equals(fileSha)) {
        throw new SearchJobConflictException("同一 Idempotency-Key 不能用于不同图片");
      }
      return ensureAdmitted(existing.get());
    }

    String jobId = UUID.randomUUID().toString();
    String token = tokenFor(jobId);
    Path saved = save(file);
    SearchJobRepository.JobRow row;
    try {
      row = repository.create(
          jobId, sha256(token), scopedKey, fileSha, toUrl(saved), requestedGuestId, userId, sarMode);
    } catch (DuplicateKeyException ex) {
      deleteQuietly(saved);
      SearchJobRepository.JobRow concurrent = repository.findByIdempotencyKey(scopedKey)
          .orElseThrow(() -> ex);
      if (!concurrent.fileSha256().equals(fileSha)) {
        throw new SearchJobConflictException("同一 Idempotency-Key 不能用于不同图片");
      }
      return ensureAdmitted(concurrent);
    }
    try {
      queue.enqueueNew(jobId);
      repository.markAdmitted(row.id());
      return submission(row, token);
    } catch (RuntimeException ex) {
      if (repository.deleteUnadmitted(row.id())) {
        deleteQuietly(saved);
      } else {
        SearchJobRepository.JobRow admitted = repository.findByIdempotencyKey(scopedKey).orElse(null);
        if (admitted != null && admitted.admitted()) return submission(admitted, tokenFor(admitted.jobId()));
      }
      throw ex;
    }
  }

  private SearchJobSubmission ensureAdmitted(SearchJobRepository.JobRow row) {
    if (!row.admitted()) {
      queue.enqueueNew(row.jobId());
      repository.markAdmitted(row.id());
    }
    return submission(row, tokenFor(row.jobId()));
  }

  public SearchJobStatus status(String jobId, SessionUser user, String jobToken) {
    SearchJobRepository.JobOwnership ownership = repository.findOwnership(jobId)
        .orElseThrow(() -> new SearchJobNotFoundException("检索任务不存在"));
    if (ownership.userId() != null) {
      if (user == null || !ownership.userId().equals(user.userId())) {
        throw new AuthRequiredException("无权查看该检索任务");
      }
    } else if (jobToken == null || !constantTimeEquals(ownership.tokenHash(), sha256(jobToken))) {
      throw new AuthRequiredException("游客任务令牌无效");
    }
    return repository.findStatus(jobId)
        .orElseThrow(() -> new SearchJobNotFoundException("检索任务不存在"));
  }

  private SearchJobSubmission submission(SearchJobRepository.JobRow row, String token) {
    return new SearchJobSubmission(row.jobId(), row.id(), row.status(), token, 1000);
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

  private Path save(MultipartFile file) {
    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    Path directory = Path.of("uploads", date);
    Path target = directory.resolve(UUID.randomUUID() + extension(file));
    try {
      Files.createDirectories(directory);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return target;
    } catch (IOException ex) {
      throw new IllegalArgumentException("上传图片保存失败");
    }
  }

  private String extension(MultipartFile file) {
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    if (name.endsWith(".png")) return ".png";
    if (name.endsWith(".webp")) return ".webp";
    return ".jpg";
  }

  private String toUrl(Path path) {
    return "/" + path.toString().replace('\\', '/');
  }

  private String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("必须提供 Idempotency-Key");
    }
    String key = value.trim();
    if (key.length() > 128) {
      throw new IllegalArgumentException("Idempotency-Key 不能超过 128 个字符");
    }
    return key;
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
    }
  }

  private String tokenFor(String jobId) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        digest((tokenSecret + ":" + jobId).getBytes(StandardCharsets.UTF_8)));
  }

  private String sha256(MultipartFile file) {
    try {
      return HexFormat.of().formatHex(digest(file.getBytes()));
    } catch (IOException ex) {
      throw new IllegalArgumentException("读取上传图片失败");
    }
  }

  private String sha256(String value) {
    return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private byte[] digest(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private boolean constantTimeEquals(String left, String right) {
    return left != null && MessageDigest.isEqual(
        left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
  }

  private Long activeUserId(Long userId) {
    if (userId == null || !authService.isActiveUser(userId)) {
      throw new IllegalArgumentException("用户不存在或已停用");
    }
    return userId;
  }
}
