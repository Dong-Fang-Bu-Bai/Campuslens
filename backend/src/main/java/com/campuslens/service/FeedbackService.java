package com.campuslens.service;

import com.campuslens.model.FeedbackRecord;
import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import com.campuslens.repository.FeedbackRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
  private static final Set<String> FEEDBACK_TYPES = Set.of("correct", "wrong", "uncertain");

  private final FeedbackRepository feedbackRepository;

  public FeedbackService(FeedbackRepository feedbackRepository) {
    this.feedbackRepository = feedbackRepository;
  }

  public FeedbackResponse submit(FeedbackRequest request) {
    if (!FEEDBACK_TYPES.contains(request.feedbackType())) {
      throw new IllegalArgumentException("feedbackType 只能为 correct、wrong 或 uncertain");
    }
    if (("correct".equals(request.feedbackType()) || "wrong".equals(request.feedbackType()))
        && request.predictedLandmarkId() == null) {
      throw new IllegalArgumentException("correct 和 wrong 反馈需要提供 predictedLandmarkId");
    }
    if ("wrong".equals(request.feedbackType()) && request.confirmedLandmarkId() == null) {
      throw new IllegalArgumentException("识别错误反馈需要提供 confirmedLandmarkId");
    }

    FeedbackRecord record = feedbackRepository.save(
        request.searchRecordId(),
        request.predictedLandmarkId(),
        request.confirmedLandmarkId(),
        request.feedbackType(),
        request.comment());

    return new FeedbackResponse(record.id(), record.status(), "反馈已记录，后续可进入后台审核");
  }

  public List<FeedbackRecord> listAll() {
    return feedbackRepository.findAll();
  }

  public List<FeedbackRecord> listBySearchRecordId(Long searchRecordId) {
    return feedbackRepository.findBySearchRecordId(searchRecordId);
  }
}
