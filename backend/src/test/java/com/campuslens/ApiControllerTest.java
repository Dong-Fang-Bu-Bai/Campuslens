package com.campuslens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.campuslens.service.AlgorithmSearchClient;
import com.campuslens.service.AlgorithmSearchClient.AdaptationRequest;
import com.campuslens.service.AlgorithmSearchClient.AdaptationResponse;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmBatchItem;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResponse;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResult;
import com.campuslens.service.AlgorithmSearchException;
import com.campuslens.service.GuestIdentityService;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GuestIdentityService guestIdentityService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockBean
  private AlgorithmSearchClient algorithmSearchClient;

  @Test
  void healthReturnsOk() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void landmarksReturnsSeedData() throws Exception {
    mockMvc.perform(get("/api/landmarks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("L01"));
  }

  @Test
  void guestIdentityIsIdempotentSequentialAndRejectsUnknownIds() throws Exception {
    String clientToken = UUID.randomUUID().toString();
    String first = allocateGuest(clientToken);
    String repeated = allocateGuest(clientToken);
    String second = allocateGuest(UUID.randomUUID().toString());

    assertThat(repeated).isEqualTo(first);
    assertThat(guestSequence(second)).isEqualTo(guestSequence(first) + 1);

    MockMultipartFile file = new MockMultipartFile(
        "file", "invalid-guest.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    mockMvc.perform(multipart("/api/search/upload").file(file)
            .param("guestId", "guest#999999999")
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentGuestAllocationReturnsOneIdentityForOneBrowserToken() throws Exception {
    String clientToken = UUID.randomUUID().toString();
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(8);
    List<Future<String>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 16; i++) {
        futures.add(executor.submit(() -> {
          start.await();
          return guestIdentityService.allocate(clientToken).guestId();
        }));
      }
      start.countDown();
      Set<String> guestIds = new HashSet<>();
      for (Future<String> future : futures) {
        guestIds.add(future.get());
      }
      assertThat(guestIds).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void uploadReturnsAlgorithmTopFive() throws Exception {
    String guestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(
            new AlgorithmSearchResult(1, "L03", "文雍广场", 0.91, "high", 3.12),
            new AlgorithmSearchResult(2, "L01", "图书馆", 0.82, "medium", 5.34),
            new AlgorithmSearchResult(3, "L04", "博学桥", 0.73, "medium", 7.56),
            new AlgorithmSearchResult(4, "L05", "琴湖及湖心岛", 0.64, "medium", 8.78),
            new AlgorithmSearchResult(5, "L10", "中心酒店", 0.52, "low", 11.23)),
        false,
        "Search successful"));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});

    String submission = mockMvc.perform(multipart("/api/search/upload").file(file)
            .param("guestId", guestId)
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.searchRecordId").isNumber())
        .andExpect(jsonPath("$.status").value("queued"))
        .andReturn().getResponse().getContentAsString();

    String result = waitForJob(submission, null);
    assertThat(result).contains("\"status\":\"success\"");
    assertThat(result).contains("\"landmarkCode\":\"L03\"");
    assertThat(result).contains("\"confidenceLevel\":\"high\"");
    assertThat(result).contains("\"mahalanobisDistance\":3.12");
  }

  @Test
  void uploadAcceptsMultipartTextSarMode() throws Exception {
    String guestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));
    MockMultipartFile file = new MockMultipartFile(
        "file", "sar.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

    mockMvc.perform(multipart("/api/search/upload")
            .file(file)
            .param("guestId", guestId)
            .param("sarMode", "true")
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("queued"));
  }

  @Test
  void uploadFallsBackWhenAlgorithmUnavailable() throws Exception {
    String guestId = allocateGuest();
    when(algorithmSearchClient.searchBatch(any())).thenThrow(new AlgorithmSearchException("连接失败"));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});

    String submission = mockMvc.perform(multipart("/api/search/upload").file(file)
            .param("guestId", guestId)
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    String result = waitForJob(submission, null);
    assertThat(result).contains("\"status\":\"failed\"");
    assertThat(result).contains("\"lowConfidence\":true");
    assertThat(result).contains("\"errorCode\":\"algorithm_unavailable\"");
    assertThat(result).contains("\"results\":[]");
  }

  @Test
  void searchJobRequiresGuestTokenAndUserOwnership() throws Exception {
    String guestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));
    MockMultipartFile guestFile = new MockMultipartFile(
        "file", "guest.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    String guestSubmission = mockMvc.perform(multipart("/api/search/upload").file(guestFile)
            .param("guestId", guestId)
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    String guestJobId = extractString(guestSubmission, "jobId");

    mockMvc.perform(get("/api/search/jobs/" + guestJobId))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/search/jobs/" + guestJobId)
            .header("X-Search-Job-Token", "invalid-token"))
        .andExpect(status().isUnauthorized());

    String ownerToken = register("jobowner01", "password123");
    String otherToken = register("jobowner02", "password123");
    MockMultipartFile userFile = new MockMultipartFile(
        "file", "user.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {4, 5, 6});
    String userSubmission = mockMvc.perform(multipart("/api/search/upload")
            .file(userFile)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();

    mockMvc.perform(get("/api/search/jobs/" + extractString(userSubmission, "jobId"))
            .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isUnauthorized());
    waitForJob(userSubmission, ownerToken);
  }

  @Test
  void uploadIdempotencyReturnsOriginalJobAndRejectsDifferentFile() throws Exception {
    String guestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));
    String key = "same-upload-key";
    MockMultipartFile first = new MockMultipartFile(
        "file", "first.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    String firstSubmission = mockMvc.perform(multipart("/api/search/upload")
            .file(first)
            .param("guestId", guestId)
            .header("Idempotency-Key", key))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    waitForJob(firstSubmission, null);

    MockMultipartFile same = new MockMultipartFile(
        "file", "same.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    String duplicate = mockMvc.perform(multipart("/api/search/upload")
            .file(same)
            .param("guestId", guestId)
            .header("Idempotency-Key", key))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("success"))
        .andReturn().getResponse().getContentAsString();
    assertThat(extractString(duplicate, "jobId")).isEqualTo(extractString(firstSubmission, "jobId"));

    MockMultipartFile different = new MockMultipartFile(
        "file", "different.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {9, 8, 7});
    mockMvc.perform(multipart("/api/search/upload")
            .file(different)
            .param("guestId", guestId)
            .header("Idempotency-Key", key))
        .andExpect(status().isConflict());
  }

  @Test
  void idempotencyKeyIsScopedToTaskOwner() throws Exception {
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));
    String firstToken = register("scopeowner01", "password123");
    String secondToken = register("scopeowner02", "password123");
    String key = "owner-scoped-key";

    MockMultipartFile first = new MockMultipartFile(
        "file", "first.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    String firstSubmission = mockMvc.perform(multipart("/api/search/upload").file(first)
            .header("Idempotency-Key", key)
            .header("Authorization", "Bearer " + firstToken))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();

    MockMultipartFile second = new MockMultipartFile(
        "file", "second.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {9, 8, 7});
    String secondSubmission = mockMvc.perform(multipart("/api/search/upload").file(second)
            .header("Idempotency-Key", key)
            .header("Authorization", "Bearer " + secondToken))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();

    assertThat(extractString(firstSubmission, "jobId"))
        .isNotEqualTo(extractString(secondSubmission, "jobId"));
    waitForJob(firstSubmission, firstToken);
    waitForJob(secondSubmission, secondToken);
  }

  @Test
  void feedbackRequiresTerminalTaskAndOwner() throws Exception {
    AlgorithmSearchResponse algorithmResponse = new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful");
    when(algorithmSearchClient.searchBatch(any())).thenAnswer(invocation -> {
      Thread.sleep(300);
      List<Path> paths = invocation.getArgument(0);
      return paths.stream()
          .map(path -> new AlgorithmBatchItem(true, algorithmResponse, null, null, false))
          .toList();
    });
    String ownerToken = register("feedbackowner01", "password123");
    String otherToken = register("feedbackowner02", "password123");
    MockMultipartFile file = new MockMultipartFile(
        "file", "pending.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {6, 6, 6});
    String submission = mockMvc.perform(multipart("/api/search/upload").file(file)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    long recordId = extractLong(submission, "searchRecordId");

    mockMvc.perform(post("/api/feedback")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(feedbackJson(recordId)))
        .andExpect(status().isBadRequest());

    waitForJob(submission, ownerToken);
    mockMvc.perform(post("/api/feedback")
            .header("Authorization", "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(feedbackJson(recordId)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void feedbackReturnsPending() throws Exception {
    String guestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});

    String searchResponse = mockMvc.perform(multipart("/api/search/upload").file(file)
            .param("guestId", guestId)
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andReturn()
        .getResponse()
        .getContentAsString();
    long searchRecordId = extractLong(searchResponse, "searchRecordId");
    waitForJob(searchResponse, null);

    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": %d,
                  "predictedLandmarkId": 1,
                  "confirmedLandmarkId": 1,
                  "guestId": "%s",
                  "feedbackType": "correct",
                  "comment": "识别正确"
                }
                """.formatted(searchRecordId, guestId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));
  }

  @Test
  void adminLoginAndFeedbackStatusWork() throws Exception {
    String token = login("admin", "admin");

    mockMvc.perform(get("/api/admin/search-records")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void adminEndpointsRejectMissingAndUserTokens() throws Exception {
    mockMvc.perform(get("/api/admin/search-records"))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "student03",
                  "password": "password123"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString());

    String token = login("student03", "password123");
    mockMvc.perform(get("/api/admin/search-records")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAndUpdateLandmark() throws Exception {
    String token = login("admin", "admin");

    String createResponse = mockMvc.perform(post("/api/admin/landmarks")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "code": "L99",
                  "name": "测试地标",
                  "englishName": "Test Landmark",
                  "type": "测试",
                  "summary": "测试摘要",
                  "description": "测试描述",
                  "locationText": "测试位置",
                  "mapX": 10.5,
                  "mapY": 20.5,
                  "coverImageUrl": "/images/landmarks/l99.jpg"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("L99"))
        .andReturn()
        .getResponse()
        .getContentAsString();
    long id = extractLong(createResponse, "id");

    mockMvc.perform(put("/api/admin/landmarks/" + id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "code": "L99",
                  "name": "测试地标更新",
                  "englishName": "Test Landmark",
                  "type": "测试",
                  "summary": "测试摘要",
                  "description": "测试描述",
                  "locationText": "测试位置",
                  "mapX": 11.5,
                  "mapY": 21.5,
                  "coverImageUrl": "/images/landmarks/l99.jpg"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("测试地标更新"));
  }

  @Test
  void userCanOnlyReadOwnSearchHistory() throws Exception {
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));

    String firstToken = register("history01", "password123");
    String secondToken = register("history02", "password123");
    uploadWithToken(firstToken);

    mockMvc.perform(get("/api/me/search-records")
            .header("Authorization", "Bearer " + firstToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].topResults[0].landmarkCode").value("L01"));

    mockMvc.perform(get("/api/me/search-records")
            .header("Authorization", "Bearer " + secondToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc.perform(get("/api/me/search-records"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void checkInBoardSupportsPostLikeAndReply() throws Exception {
    String authorGuestId = allocateGuest();
    String replyGuestId = allocateGuest();
    mockSearch(new AlgorithmSearchResponse(
        List.of(
            new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12),
            new AlgorithmSearchResult(2, "L02", "学术大讲堂", 0.72, "medium", 6.45)),
        false,
        "Search successful"));
    long searchRecordId = uploadWithGuest(authorGuestId);
    String createResponse = mockMvc.perform(post("/api/check-ins")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": %d,
                  "landmarkId": 1,
                  "message": "图书馆门口完成打卡",
                  "publishImage": false,
                  "guestId": "%s"
                }
                """.formatted(searchRecordId, authorGuestId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.searchRecordId").value(searchRecordId))
        .andExpect(jsonPath("$.sourceImageUrl").isEmpty())
        .andExpect(jsonPath("$.landmarkCode").value("L01"))
        .andExpect(jsonPath("$.displayName").value(authorGuestId))
        .andReturn()
        .getResponse()
        .getContentAsString();
    long checkInId = extractLong(createResponse, "id");

    mockMvc.perform(post("/api/check-ins/" + checkInId + "/like")
            .param("guestId", authorGuestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(true))
        .andExpect(jsonPath("$.likeCount").value(1));

    String replyResponse = mockMvc.perform(post("/api/check-ins/" + checkInId + "/replies")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "message": "同一地点也适合夜景拍摄",
                  "guestId": "%s"
                }
                """.formatted(replyGuestId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value(replyGuestId))
        .andExpect(jsonPath("$.parentReplyId").isEmpty())
        .andReturn()
        .getResponse()
        .getContentAsString();
    long replyId = extractLong(replyResponse, "id");

    mockMvc.perform(get("/api/check-ins")
            .param("guestId", authorGuestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].likedByMe").value(true))
        .andExpect(jsonPath("$[0].replyCount").value(1));

    String nestedReplyResponse = mockMvc.perform(post("/api/check-ins/" + checkInId + "/replies")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "message": "同意，夜景很好看",
                  "guestId": "%s",
                  "parentReplyId": %d
                }
                """.formatted(authorGuestId, replyId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentReplyId").value(replyId))
        .andReturn()
        .getResponse()
        .getContentAsString();
    long nestedReplyId = extractLong(nestedReplyResponse, "id");

    mockMvc.perform(post("/api/check-in-replies/" + replyId + "/like")
            .param("guestId", authorGuestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(true))
        .andExpect(jsonPath("$.likeCount").value(1));

    mockMvc.perform(post("/api/check-in-replies/" + nestedReplyId + "/like")
            .param("guestId", authorGuestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replyId").value(nestedReplyId))
        .andExpect(jsonPath("$.liked").value(true))
        .andExpect(jsonPath("$.likeCount").value(1));

    mockMvc.perform(get("/api/check-ins/" + checkInId)
            .param("guestId", authorGuestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replyCount").value(2))
        .andExpect(jsonPath("$.replies[0].id").value(replyId))
        .andExpect(jsonPath("$.replies[0].likedByMe").value(true))
        .andExpect(jsonPath("$.replies[0].likeCount").value(1))
        .andExpect(jsonPath("$.replies[0].replyCount").value(1))
        .andExpect(jsonPath("$.replies[0].replies[0].parentReplyId").value(replyId))
        .andExpect(jsonPath("$.replies[0].replies[0].likedByMe").value(true))
        .andExpect(jsonPath("$.replies[0].replies[0].likeCount").value(1));

    mockMvc.perform(post("/api/check-ins/" + checkInId + "/replies")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "message": "无效父回复",
                  "guestId": "%s",
                  "parentReplyId": 999999
                }
                """.formatted(authorGuestId)))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/check-ins")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(searchRecordId, 1, authorGuestId, true)))
        .andExpect(status().isConflict());

    mockMvc.perform(post("/api/check-ins")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(searchRecordId, 1, replyGuestId, true)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void checkInValidatesSourceAndControlsPhotoVisibility() throws Exception {
    mockSearch(new AlgorithmSearchResponse(
        List.of(
            new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12),
            new AlgorithmSearchResult(2, "L02", "学术大讲堂", 0.72, "medium", 6.45)),
        false,
        "Search successful"));
    String ownerToken = register("checkinowner01", "password123");
    String otherToken = register("checkinowner02", "password123");
    long publicRecordId = uploadWithToken(ownerToken);

    mockMvc.perform(post("/api/check-ins")
            .header("Authorization", "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(publicRecordId, 1, null, true)))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/check-ins")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(publicRecordId, 1, null, true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceImageUrl").isString());

    long invalidCandidateRecordId = uploadWithToken(ownerToken);
    mockMvc.perform(post("/api/check-ins")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(invalidCandidateRecordId, 3, null, false)))
        .andExpect(status().isBadRequest());

    long failedRecordId = uploadWithToken(ownerToken);
    jdbcTemplate.update("UPDATE search_record SET status = 'failed' WHERE id = ?", failedRecordId);
    mockMvc.perform(post("/api/check-ins")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkInJson(failedRecordId, 1, null, false)))
        .andExpect(status().isBadRequest());

    jdbcTemplate.update("""
        INSERT INTO check_in (landmark_id, guest_id, display_name, message)
        VALUES (1, 'legacy-guest', 'legacy-guest', '历史打卡仍可读取')
        """);
    String list = mockMvc.perform(get("/api/check-ins"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(list).contains("\"searchRecordId\":null");
    assertThat(list).contains("\"guestId\":\"legacy-guest\"");
    assertThat(list).contains("\"sourceImageUrl\":\"/uploads/");
  }

  @Test
  void acceptingFeedbackStagesCorrectionSampleForNextIndex() throws Exception {
    mockSearch(new AlgorithmSearchResponse(
        List.of(
            new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12),
            new AlgorithmSearchResult(2, "L02", "学术大讲堂", 0.72, "medium", 6.45)),
        false,
        "Search successful"));
    String userToken = register("feedback01", "password123");
    long searchRecordId = uploadWithToken(userToken);
    long feedbackId = createWrongFeedback(searchRecordId, 1, 2, userToken);
    String adminToken = login("admin", "admin");

    mockMvc.perform(post("/api/admin/feedback/" + feedbackId + "/status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": "accepted"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));

    String detail = waitForFeedbackDetail(adminToken, feedbackId, "pending_index");
    assertThat(detail).contains("\"syncStatus\":\"pending_index\"");
    assertThat(detail).contains("\"suggestAccept\":true");
    assertThat(detail).contains("\"confirmedLandmarkId\":2");
  }

  @Test
  void acceptingFeedbackDoesNotDependOnAlgorithmAdaptationEndpoint() throws Exception {
    mockSearch(new AlgorithmSearchResponse(
        List.of(new AlgorithmSearchResult(1, "L01", "图书馆", 0.91, "high", 3.12)),
        false,
        "Search successful"));
    String userToken = register("feedback02", "password123");
    long searchRecordId = uploadWithToken(userToken);
    long feedbackId = createWrongFeedback(searchRecordId, 1, 1, userToken);
    String adminToken = login("admin", "admin");

    mockMvc.perform(post("/api/admin/feedback/" + feedbackId + "/status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": "accepted"
                }
                """))
        .andExpect(status().isOk());

    String detail = waitForFeedbackDetail(adminToken, feedbackId, "pending_index");
    assertThat(detail).contains("\"syncStatus\":\"pending_index\"");
    assertThat(detail).contains("\"status\":\"accepted\"");
  }

  @Test
  void userRegisterAndLoginWork() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "student01",
                  "password": "password123",
                  "email": "student01@example.com"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").isNumber())
        .andExpect(jsonPath("$.username").value("student01"))
        .andExpect(jsonPath("$.role").value("user"))
        .andExpect(jsonPath("$.admin").value(false))
        .andExpect(jsonPath("$.token").isString());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "student01",
                  "password": "password123"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("student01"))
        .andExpect(jsonPath("$.role").value("user"))
        .andExpect(jsonPath("$.token").isString());
  }

  @Test
  void userRegisterRejectsShortPasswordAndDuplicateUsername() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "shortpass",
                  "password": "1234567"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("密码至少 8 位"));

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "student02",
                  "password": "password123"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "student02",
                  "password": "password456"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("用户名已存在"));
  }

  @Test
  void loggedInUserCanManageAccountProfileAndPassword() throws Exception {
    String token = register("accountuser01", "password123");

    mockMvc.perform(get("/api/me/account")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("accountuser01"))
        .andExpect(jsonPath("$.role").value("user"))
        .andExpect(jsonPath("$.admin").value(false));

    mockMvc.perform(put("/api/me/account/email")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "accountuser01@example.com"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.email").value("accountuser01@example.com"));

    mockMvc.perform(put("/api/me/account/email")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "not-an-email"
                }
                """))
        .andExpect(status().isBadRequest());

    mockMvc.perform(put("/api/me/account/password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "currentPassword": "wrong-password",
                  "newPassword": "newpassword456"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("当前密码不正确"));

    mockMvc.perform(put("/api/me/account/password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "currentPassword": "password123",
                  "newPassword": "newpassword456"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("密码修改成功"));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "accountuser01",
                  "password": "password123"
                }
                """))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "accountuser01",
                  "password": "newpassword456"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("accountuser01@example.com"));

    mockMvc.perform(get("/api/me/account"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loggedInUserCanUploadAndReplaceCroppedAvatar() throws Exception {
    String token = register("avataruser01", "password123");
    MockMultipartFile firstAvatar = avatarFile("first-avatar.png", 64, 0xFF2563EB);

    String firstResponse = mockMvc.perform(multipart("/api/me/account/avatar")
            .file(firstAvatar)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.username").value("avataruser01"))
        .andExpect(jsonPath("$.account.avatarUrl").value(org.hamcrest.Matchers.startsWith("/uploads/avatars/")))
        .andReturn()
        .getResponse()
        .getContentAsString();
    String firstUrl = extractString(firstResponse, "avatarUrl");

    mockMvc.perform(get(firstUrl))
        .andExpect(status().isOk());

    MockMultipartFile secondAvatar = avatarFile("second-avatar.jpg", 96, 0xFFF97316);
    String secondResponse = mockMvc.perform(multipart("/api/me/account/avatar")
            .file(secondAvatar)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    String secondUrl = extractString(secondResponse, "avatarUrl");
    assertThat(secondUrl).isNotEqualTo(firstUrl);

    mockMvc.perform(get("/api/me/account")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value(secondUrl));

    mockMvc.perform(multipart("/api/me/account/avatar")
            .file(new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes()))
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("头像文件不是有效图片"));
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andReturn()
        .getResponse()
        .getContentAsString();
    return response.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
  }

  private String register(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andReturn()
        .getResponse()
        .getContentAsString();
    return response.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
  }

  private MockMultipartFile avatarFile(String filename, int size, int color) throws Exception {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        image.setRGB(x, y, color);
      }
    }
    String format = filename.endsWith(".png") ? "png" : "jpg";
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, format, output);
    String contentType = "png".equals(format) ? "image/png" : "image/jpeg";
    return new MockMultipartFile("file", filename, contentType, output.toByteArray());
  }

  private String allocateGuest() throws Exception {
    return allocateGuest(UUID.randomUUID().toString());
  }

  private String allocateGuest(String clientToken) throws Exception {
    String response = mockMvc.perform(post("/api/guests")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "clientToken": "%s"
                }
                """.formatted(clientToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guestId").isString())
        .andReturn()
        .getResponse()
        .getContentAsString();
    return extractString(response, "guestId");
  }

  private long guestSequence(String guestId) {
    return Long.parseLong(guestId.substring("guest#".length()));
  }

  private long uploadWithToken(String token) throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});
    String response = mockMvc.perform(multipart("/api/search/upload")
            .file(file)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isAccepted())
        .andReturn()
        .getResponse()
        .getContentAsString();
    waitForJob(response, token);
    return extractLong(response, "searchRecordId");
  }

  private long uploadWithGuest(String guestId) throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "check-in.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    String response = mockMvc.perform(multipart("/api/search/upload")
            .file(file)
            .param("guestId", guestId)
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    waitForJob(response, null);
    return extractLong(response, "searchRecordId");
  }

  private String checkInJson(long searchRecordId, long landmarkId, String guestId, boolean publishImage) {
    String guestField = guestId == null ? "" : ",\n  \"guestId\": \"" + guestId + "\"";
    return """
        {
          "searchRecordId": %d,
          "landmarkId": %d,
          "message": "识图记录关联打卡",
          "publishImage": %s%s
        }
        """.formatted(searchRecordId, landmarkId, publishImage, guestField);
  }

  private void mockSearch(AlgorithmSearchResponse response) {
    when(algorithmSearchClient.searchBatch(any())).thenAnswer(invocation -> {
      List<Path> paths = invocation.getArgument(0);
      return paths.stream()
          .map(path -> new AlgorithmBatchItem(true, response, null, null, false))
          .toList();
    });
  }

  private String waitForJob(String submission, String bearerToken) throws Exception {
    String jobId = extractString(submission, "jobId");
    String jobToken = extractString(submission, "jobToken");
    String detail = "";
    for (int i = 0; i < 50; i++) {
      var request = get("/api/search/jobs/" + jobId);
      if (bearerToken == null) {
        request.header("X-Search-Job-Token", jobToken);
      } else {
        request.header("Authorization", "Bearer " + bearerToken);
      }
      detail = mockMvc.perform(request)
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
      if (detail.contains("\"status\":\"success\"")
          || detail.contains("\"status\":\"low_confidence\"")
          || detail.contains("\"status\":\"failed\"")) {
        return detail;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("检索任务未在测试时限内完成: " + detail);
  }

  private long createWrongFeedback(
      long searchRecordId,
      long predictedLandmarkId,
      long confirmedLandmarkId,
      String userToken) throws Exception {
    String response = mockMvc.perform(post("/api/feedback")
            .header("Authorization", "Bearer " + userToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": %d,
                  "predictedLandmarkId": %d,
                  "confirmedLandmarkId": %d,
                  "feedbackType": "wrong",
                  "comment": "应采纳为校正样本"
                }
                """.formatted(searchRecordId, predictedLandmarkId, confirmedLandmarkId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    return extractLong(response, "feedbackId");
  }

  private String feedbackJson(long searchRecordId) {
    return """
        {
          "searchRecordId": %d,
          "predictedLandmarkId": 1,
          "confirmedLandmarkId": 1,
          "feedbackType": "correct",
          "comment": "识别正确"
        }
        """.formatted(searchRecordId);
  }

  private String waitForFeedbackDetail(String adminToken, long feedbackId, String expectedSyncStatus) throws Exception {
    String detail = "";
    for (int i = 0; i < 20; i++) {
      detail = mockMvc.perform(get("/api/admin/feedback/" + feedbackId)
              .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
      if (detail.contains("\"syncStatus\":\"" + expectedSyncStatus + "\"")) {
        return detail;
      }
      Thread.sleep(100);
    }
    return detail;
  }

  private long extractLong(String json, String field) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) {
      throw new IllegalArgumentException("JSON field not found: " + field);
    }
    return Long.parseLong(matcher.group(1));
  }

  private String extractString(String json, String field) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) {
      throw new IllegalArgumentException("JSON field not found: " + field);
    }
    return matcher.group(1);
  }
}
