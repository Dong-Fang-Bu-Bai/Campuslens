package com.campuslens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.campuslens.service.AlgorithmSearchClient;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResponse;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmSearchResult;
import com.campuslens.service.AlgorithmSearchException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerTest {
  @Autowired
  private MockMvc mockMvc;

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
  void uploadReturnsAlgorithmTopFive() throws Exception {
    when(algorithmSearchClient.search(any())).thenReturn(new AlgorithmSearchResponse(
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

    mockMvc.perform(multipart("/api/search/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("算法服务检索成功"))
        .andExpect(jsonPath("$.results.length()").value(5))
        .andExpect(jsonPath("$.results[0].landmarkCode").value("L03"))
        .andExpect(jsonPath("$.results[0].confidenceLevel").value("high"))
        .andExpect(jsonPath("$.results[0].mahalanobisDistance").value(3.12));
  }

  @Test
  void uploadFallsBackWhenAlgorithmUnavailable() throws Exception {
    when(algorithmSearchClient.search(any())).thenThrow(new AlgorithmSearchException("连接失败"));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});

    mockMvc.perform(multipart("/api/search/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lowConfidence").value(true))
        .andExpect(jsonPath("$.message").value("算法服务暂不可用，未生成候选地标。原因：连接失败"))
        .andExpect(jsonPath("$.results.length()").value(0));
  }

  @Test
  void feedbackReturnsPending() throws Exception {
    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": 1,
                  "predictedLandmarkId": 1,
                  "confirmedLandmarkId": 1,
                  "feedbackType": "correct",
                  "comment": "识别正确"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.feedbackId").isNumber());
  }

  @Test
  void feedbackRejectsMissingType() throws Exception {
    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": 1,
                  "predictedLandmarkId": 1,
                  "feedbackType": ""
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void feedbackRejectsInvalidType() throws Exception {
    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": 1,
                  "predictedLandmarkId": 1,
                  "feedbackType": "invalid_type"
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void feedbackWrongRejectsWithoutConfirmed() throws Exception {
    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": 1,
                  "predictedLandmarkId": 1,
                  "feedbackType": "wrong"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("识别错误反馈需要提供 confirmedLandmarkId"));
  }

  @Test
  void feedbackUncertainSucceeds() throws Exception {
    mockMvc.perform(post("/api/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "searchRecordId": 2,
                  "predictedLandmarkId": 1,
                  "feedbackType": "uncertain",
                  "comment": "不太确定"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.feedbackId").isNumber());
  }

  @Test
  void adminFeedbackReturnsList() throws Exception {
    mockMvc.perform(get("/api/admin/feedback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void searchFeedbackReturnsList() throws Exception {
    mockMvc.perform(get("/api/search/1/feedback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
