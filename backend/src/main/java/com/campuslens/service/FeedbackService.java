package com.campuslens.service;

import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
  private static final Set<String> FEEDBACK_TYPES = Set.of("correct", "wrong", "uncertain");
  private final AtomicLong feedbackId = new AtomicLong(1);

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
    return new FeedbackResponse(feedbackId.getAndIncrement(), "pending", "反馈已记录，后续可进入后台审核");
  }
}
