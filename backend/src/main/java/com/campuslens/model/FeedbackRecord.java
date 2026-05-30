package com.campuslens.model;

import java.time.LocalDateTime;

/**
 * 反馈记录实体，对应 feedback 表。
 * 用于 GET 接口返回完整反馈数据，与 POST 返回的 FeedbackResponse（简短确认）互补。
 */
public record FeedbackRecord(
    Long id,
    Long searchRecordId,
    Long predictedLandmarkId,
    Long confirmedLandmarkId,
    String feedbackType,
    String comment,
    String status,
    LocalDateTime createdAt) {
}
