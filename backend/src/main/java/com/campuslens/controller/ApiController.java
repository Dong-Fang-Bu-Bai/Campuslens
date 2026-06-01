package com.campuslens.controller;

import com.campuslens.model.AdminFeedbackRecord;
import com.campuslens.model.AdminLoginRequest;
import com.campuslens.model.AdminLoginResponse;
import com.campuslens.model.AdminSearchRecord;
import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import com.campuslens.model.FeedbackStatusRequest;
import com.campuslens.model.HealthResponse;
import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.LandmarkSummary;
import com.campuslens.model.SearchResponse;
import com.campuslens.service.AdminService;
import com.campuslens.service.FeedbackService;
import com.campuslens.service.LandmarkService;
import com.campuslens.service.SearchRecordService;
import com.campuslens.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final LandmarkService landmarkService;
  private final SearchService searchService;
  private final FeedbackService feedbackService;
  private final SearchRecordService searchRecordService;
  private final AdminService adminService;

  public ApiController(
      LandmarkService landmarkService,
      SearchService searchService,
      FeedbackService feedbackService,
      SearchRecordService searchRecordService,
      AdminService adminService) {
    this.landmarkService = landmarkService;
    this.searchService = searchService;
    this.feedbackService = feedbackService;
    this.searchRecordService = searchRecordService;
    this.adminService = adminService;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse("ok", "CampusLens backend is running");
  }

  @GetMapping("/landmarks")
  public List<LandmarkSummary> landmarks() {
    return landmarkService.list();
  }

  @GetMapping("/landmarks/{id}")
  public ResponseEntity<LandmarkDetail> landmark(@PathVariable Long id) {
    return landmarkService.findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/search/upload")
  public SearchResponse upload(@RequestPart("file") MultipartFile file) {
    return searchService.search(file);
  }

  @PostMapping("/feedback")
  public FeedbackResponse feedback(@Valid @RequestBody FeedbackRequest request) {
    return feedbackService.submit(request);
  }

  @PostMapping("/admin/auth/login")
  public AdminLoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
    return adminService.login(request);
  }

  @GetMapping("/admin/search-records")
  public List<AdminSearchRecord> adminSearchRecords() {
    return searchRecordService.listRecent();
  }

  @GetMapping("/admin/feedback")
  public List<AdminFeedbackRecord> adminFeedback() {
    return feedbackService.listRecent();
  }

  @PostMapping("/admin/feedback/{id}/status")
  public FeedbackResponse updateFeedbackStatus(
      @PathVariable Long id,
      @Valid @RequestBody FeedbackStatusRequest request) {
    return feedbackService.updateStatus(id, request);
  }
}
