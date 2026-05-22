package com.campuslens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {
  @Autowired
  private MockMvc mockMvc;

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
  void uploadReturnsDemoTopFive() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "sample.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});

    mockMvc.perform(multipart("/api/search/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(5));
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
        .andExpect(jsonPath("$.status").value("pending"));
  }
}
