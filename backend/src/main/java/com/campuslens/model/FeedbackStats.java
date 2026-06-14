package com.campuslens.model;

import java.util.List;

/**
 * 反馈统计数据，用于管理后台展示。
 */
public record FeedbackStats(
    long totalCount,
    long correctCount,
    long wrongCount,
    long uncertainCount,
    long pendingCount,
    long acceptedCount,
    long ignoredCount,
    double accuracyRate,
    List<DailyTrend> dailyTrend) {

  public record DailyTrend(
      String date,
      long count) {
  }
}
