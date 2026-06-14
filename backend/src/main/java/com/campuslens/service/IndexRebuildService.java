package com.campuslens.service;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IndexRebuildService {
  private final AlgorithmSearchClient algorithm;
  private final CorrectionSampleService correctionSamples;
  private final JdbcTemplate jdbcTemplate;

  public IndexRebuildService(
      AlgorithmSearchClient algorithm,
      CorrectionSampleService correctionSamples,
      JdbcTemplate jdbcTemplate) {
    this.algorithm = algorithm;
    this.correctionSamples = correctionSamples;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<String, Object> start() {
    Map<String, Object> result = algorithm.startIndexRebuild();
    String id = String.valueOf(result.get("rebuildJobId"));
    jdbcTemplate.update("""
        INSERT INTO index_rebuild_job (rebuild_job_id, status) VALUES (?, ?)
        """, id, String.valueOf(result.getOrDefault("status", "building")));
    return result;
  }

  public Map<String, Object> status(String jobId) {
    Map<String, Object> result = algorithm.indexRebuildStatus(jobId);
    String status = String.valueOf(result.get("status"));
    String version = result.get("indexVersion") == null ? null : String.valueOf(result.get("indexVersion"));
    String error = result.get("error") == null ? null : String.valueOf(result.get("error"));
    jdbcTemplate.update("""
        UPDATE index_rebuild_job SET status = ?, index_version = ?, error_message = ?
        WHERE rebuild_job_id = ?
        """, status, version, error, jobId);
    if ("completed".equals(status) && version != null) correctionSamples.markPublished(version);
    return result;
  }

  public Map<String, Object> runtime() {
    return algorithm.runtimeStatus();
  }

  public boolean isMaintenance() {
    try {
      return "maintenance".equals(String.valueOf(runtime().get("status")));
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}
