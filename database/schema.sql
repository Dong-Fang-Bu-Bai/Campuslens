-- CampusLens V3 current-schema reference.
-- Runtime schema evolution is managed by database/migration/V1-V15.
-- This file is for review and fresh-schema comparison; do not run it together
-- with Flyway migrations against the same database.

CREATE TABLE IF NOT EXISTS landmark (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(16) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  english_name VARCHAR(150),
  type VARCHAR(50),
  summary VARCHAR(500),
  description TEXT,
  location_text VARCHAR(255),
  map_x DECIMAL(10, 4),
  map_y DECIMAL(10, 4),
  cover_image_url LONGTEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS landmark_image (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  landmark_id BIGINT NOT NULL,
  image_url LONGTEXT NOT NULL,
  angle VARCHAR(50),
  light_condition VARCHAR(50),
  is_cover BOOLEAN NOT NULL DEFAULT FALSE,
  collector VARCHAR(50),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_landmark_image_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmark(id)
);

CREATE TABLE IF NOT EXISTS image_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  image_id BIGINT NOT NULL,
  landmark_id BIGINT NOT NULL,
  model_name VARCHAR(100),
  model_version VARCHAR(100),
  dimension INT,
  feature_path VARCHAR(500),
  indexed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_image_feature_image
    FOREIGN KEY (image_id) REFERENCES landmark_image(id),
  CONSTRAINT fk_image_feature_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmark(id)
);

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  role VARCHAR(50) NOT NULL DEFAULT 'user',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL DEFAULT 'admin',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS guest_identity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  client_token_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_guest_identity_client_token UNIQUE (client_token_hash)
);

CREATE TABLE IF NOT EXISTS search_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  upload_image_url VARCHAR(500) NOT NULL,
  top_results_json TEXT,
  best_landmark_id BIGINT,
  best_score DECIMAL(8, 6),
  status VARCHAR(50) NOT NULL DEFAULT 'success',
  low_confidence BOOLEAN NOT NULL DEFAULT FALSE,
  message VARCHAR(500),
  guest_id VARCHAR(100) NOT NULL DEFAULT 'guest',
  user_id BIGINT,
  user_type VARCHAR(50) NOT NULL DEFAULT 'guest',
  job_id VARCHAR(36),
  job_token_hash VARCHAR(64),
  idempotency_key VARCHAR(128),
  file_sha256 VARCHAR(64),
  queued_at DATETIME,
  started_at DATETIME,
  finished_at DATETIME,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  attempt_count INT NOT NULL DEFAULT 0,
  error_code VARCHAR(100),
  lease_until DATETIME,
  worker_id VARCHAR(100),
  next_attempt_at DATETIME,
  sar_mode BOOLEAN NOT NULL DEFAULT FALSE,
  sar_applied BOOLEAN,
  trust_level VARCHAR(32),
  base_model_version VARCHAR(160),
  index_version VARCHAR(160),
  sar_state_version VARCHAR(160),
  algorithm_instance_id VARCHAR(100),
  algorithm_instance_role VARCHAR(32),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_search_record_best_landmark
    FOREIGN KEY (best_landmark_id) REFERENCES landmark(id),
  CONSTRAINT uk_search_record_job_id UNIQUE (job_id),
  CONSTRAINT uk_search_record_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_search_record_job_status
  ON search_record (status, lease_until);
CREATE INDEX idx_search_record_queue_due
  ON search_record (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  search_record_id BIGINT NOT NULL,
  predicted_landmark_id BIGINT,
  confirmed_landmark_id BIGINT,
  user_id BIGINT,
  feedback_type VARCHAR(50) NOT NULL,
  comment VARCHAR(500),
  status VARCHAR(50) NOT NULL DEFAULT 'pending',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_feedback_search_record
    FOREIGN KEY (search_record_id) REFERENCES search_record(id),
  CONSTRAINT fk_feedback_predicted_landmark
    FOREIGN KEY (predicted_landmark_id) REFERENCES landmark(id),
  CONSTRAINT fk_feedback_confirmed_landmark
    FOREIGN KEY (confirmed_landmark_id) REFERENCES landmark(id)
);

CREATE TABLE IF NOT EXISTS check_in (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  landmark_id BIGINT NOT NULL,
  search_record_id BIGINT,
  user_id BIGINT,
  guest_id VARCHAR(100),
  display_name VARCHAR(100) NOT NULL,
  message VARCHAR(500) NOT NULL,
  publish_image BOOLEAN NOT NULL DEFAULT FALSE,
  like_count INT NOT NULL DEFAULT 0,
  reply_count INT NOT NULL DEFAULT 0,
  status VARCHAR(50) NOT NULL DEFAULT 'visible',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmark(id),
  CONSTRAINT fk_check_in_search_record
    FOREIGN KEY (search_record_id) REFERENCES search_record(id),
  CONSTRAINT uk_check_in_search_record UNIQUE (search_record_id),
  CONSTRAINT fk_check_in_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_check_in_landmark_created
  ON check_in (landmark_id, created_at);

CREATE TABLE IF NOT EXISTS check_in_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_in_id BIGINT NOT NULL,
  user_id BIGINT,
  guest_id VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_like_check_in
    FOREIGN KEY (check_in_id) REFERENCES check_in(id)
);

CREATE INDEX idx_check_in_like_target
  ON check_in_like (check_in_id);

CREATE TABLE IF NOT EXISTS check_in_reply (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_in_id BIGINT NOT NULL,
  user_id BIGINT,
  guest_id VARCHAR(100),
  display_name VARCHAR(100) NOT NULL,
  message VARCHAR(500) NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'visible',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_reply_check_in
    FOREIGN KEY (check_in_id) REFERENCES check_in(id),
  CONSTRAINT fk_check_in_reply_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_check_in_reply_target
  ON check_in_reply (check_in_id, created_at);

CREATE TABLE IF NOT EXISTS correction_sample (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feedback_id BIGINT NOT NULL,
  search_record_id BIGINT NOT NULL,
  upload_image_url VARCHAR(500) NOT NULL,
  predicted_landmark_id BIGINT,
  confirmed_landmark_id BIGINT NOT NULL,
  confirmed_landmark_code VARCHAR(16) NOT NULL,
  source_feedback_type VARCHAR(50) NOT NULL,
  top_results_json TEXT,
  sync_status VARCHAR(50) NOT NULL DEFAULT 'sync_pending',
  suggest_accept BOOLEAN,
  review_score DECIMAL(8, 6),
  reason VARCHAR(500),
  sar_eligible BOOLEAN,
  next_action VARCHAR(100),
  algorithm_response_json TEXT,
  dataset_path VARCHAR(700),
  published_index_version VARCHAR(160),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_correction_sample_feedback
    FOREIGN KEY (feedback_id) REFERENCES feedback(id),
  CONSTRAINT fk_correction_sample_search_record
    FOREIGN KEY (search_record_id) REFERENCES search_record(id),
  CONSTRAINT fk_correction_sample_predicted_landmark
    FOREIGN KEY (predicted_landmark_id) REFERENCES landmark(id),
  CONSTRAINT fk_correction_sample_confirmed_landmark
    FOREIGN KEY (confirmed_landmark_id) REFERENCES landmark(id)
);

CREATE INDEX idx_correction_sample_feedback
  ON correction_sample (feedback_id);

CREATE TABLE IF NOT EXISTS index_rebuild_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rebuild_job_id VARCHAR(36) NOT NULL UNIQUE,
  status VARCHAR(50) NOT NULL,
  index_version VARCHAR(160),
  error_message VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
