package com.campuslens.controller;

import com.campuslens.model.AdminFeedbackDetail;
import com.campuslens.model.AdminFeedbackRecord;
import com.campuslens.model.AdminLoginRequest;
import com.campuslens.model.AdminLoginResponse;
import com.campuslens.model.AdminSearchRecord;
import com.campuslens.model.AuthLoginRequest;
import com.campuslens.model.AuthRegisterRequest;
import com.campuslens.model.AuthResponse;
import com.campuslens.model.CheckInRecord;
import com.campuslens.model.CheckInReply;
import com.campuslens.model.CheckInReplyRequest;
import com.campuslens.model.CheckInRequest;
import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import com.campuslens.model.FeedbackStatusRequest;
import com.campuslens.model.HealthResponse;
import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.LandmarkImage;
import com.campuslens.model.LandmarkSummary;
import com.campuslens.model.LandmarkUpsertRequest;
import com.campuslens.model.LikeResponse;
import com.campuslens.model.SearchResponse;
import com.campuslens.model.SearchJobStatus;
import com.campuslens.model.SearchJobSubmission;
import com.campuslens.model.SessionUser;
import com.campuslens.model.UserSearchRecord;
import com.campuslens.service.AdminService;
import com.campuslens.service.AuthService;
import com.campuslens.service.CheckInService;
import com.campuslens.service.FeedbackService;
import com.campuslens.service.LandmarkService;
import com.campuslens.service.SearchRecordService;
import com.campuslens.service.SearchService;
import com.campuslens.service.SearchJobService;
import com.campuslens.service.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final SearchJobService searchJobService;
  private final FeedbackService feedbackService;
  private final SearchRecordService searchRecordService;
  private final AdminService adminService;
  private final AuthService authService;
  private final SessionService sessionService;
  private final CheckInService checkInService;

  public ApiController(
      LandmarkService landmarkService,
      SearchService searchService,
      SearchJobService searchJobService,
      FeedbackService feedbackService,
      SearchRecordService searchRecordService,
      AdminService adminService,
      AuthService authService,
      SessionService sessionService,
      CheckInService checkInService) {
    this.landmarkService = landmarkService;
    this.searchService = searchService;
    this.searchJobService = searchJobService;
    this.feedbackService = feedbackService;
    this.searchRecordService = searchRecordService;
    this.adminService = adminService;
    this.authService = authService;
    this.sessionService = sessionService;
    this.checkInService = checkInService;
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
  public ResponseEntity<SearchJobSubmission> upload(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "guestId", required = false) String guestId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return ResponseEntity.accepted().body(searchJobService.submit(file, user, guestId, idempotencyKey));
  }

  @GetMapping("/search/jobs/{jobId}")
  public SearchJobStatus searchJob(
      @PathVariable String jobId,
      @RequestHeader(value = "X-Search-Job-Token", required = false) String jobToken,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return searchJobService.status(jobId, user, jobToken);
  }

  @PostMapping("/feedback")
  public FeedbackResponse feedback(
      @Valid @RequestBody FeedbackRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return feedbackService.submit(request, user);
  }

  @PostMapping("/auth/register")
  public AuthResponse register(@Valid @RequestBody AuthRegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/auth/login")
  public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
    return authService.login(request);
  }

  @GetMapping("/me/search-records")
  public List<UserSearchRecord> mySearchRecords(
      @RequestParam(value = "limit", defaultValue = "20") int limit,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.requireUser(authorization);
    return searchRecordService.listForUser(user.userId(), limit);
  }

  @GetMapping("/check-ins")
  public List<CheckInRecord> checkIns(
      @RequestParam(value = "landmarkId", required = false) Long landmarkId,
      @RequestParam(value = "limit", defaultValue = "50") int limit,
      @RequestParam(value = "guestId", required = false) String guestId,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return checkInService.list(landmarkId, limit, user, guestId);
  }

  @PostMapping("/check-ins")
  public CheckInRecord createCheckIn(
      @Valid @RequestBody CheckInRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return checkInService.create(request, user);
  }

  @PostMapping("/check-ins/{id}/like")
  public LikeResponse toggleCheckInLike(
      @PathVariable Long id,
      @RequestParam(value = "guestId", required = false) String guestId,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return checkInService.toggleLike(id, user, guestId);
  }

  @PostMapping("/check-ins/{id}/replies")
  public CheckInReply addCheckInReply(
      @PathVariable Long id,
      @Valid @RequestBody CheckInReplyRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    SessionUser user = sessionService.find(authorization).orElse(null);
    return checkInService.addReply(id, request, user);
  }

  @PostMapping("/admin/auth/login")
  public AdminLoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
    return adminService.login(request);
  }

  @GetMapping("/admin/search-records")
  public List<AdminSearchRecord> adminSearchRecords(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return searchRecordService.listRecent();
  }

  @GetMapping("/admin/feedback")
  public List<AdminFeedbackRecord> adminFeedback(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return feedbackService.listRecent();
  }

  @GetMapping("/admin/feedback/{id}")
  public AdminFeedbackDetail adminFeedbackDetail(
      @PathVariable Long id,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return feedbackService.detail(id);
  }

  @PostMapping("/admin/feedback/{id}/status")
  public FeedbackResponse updateFeedbackStatus(
      @PathVariable Long id,
      @Valid @RequestBody FeedbackStatusRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return feedbackService.updateStatus(id, request);
  }

  @PostMapping("/admin/landmarks")
  public LandmarkDetail createLandmark(
      @Valid @RequestBody LandmarkUpsertRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return landmarkService.create(request);
  }

  @PutMapping("/admin/landmarks/{id}")
  public LandmarkDetail updateLandmark(
      @PathVariable Long id,
      @Valid @RequestBody LandmarkUpsertRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return landmarkService.update(id, request);
  }

  @PostMapping("/admin/landmarks/{id}/images")
  public LandmarkImage addLandmarkImage(
      @PathVariable Long id,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "angle", required = false) String angle,
      @RequestPart(value = "lightCondition", required = false) String lightCondition,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    sessionService.requireAdmin(authorization);
    return landmarkService.addImage(id, file, angle, lightCondition);
  }
}
