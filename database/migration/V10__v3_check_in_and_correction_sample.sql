CREATE TABLE IF NOT EXISTS check_in (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  landmark_id BIGINT NOT NULL,
  user_id BIGINT,
  guest_id VARCHAR(100),
  display_name VARCHAR(100) NOT NULL,
  message VARCHAR(500) NOT NULL,
  like_count INT NOT NULL DEFAULT 0,
  reply_count INT NOT NULL DEFAULT 0,
  status VARCHAR(50) NOT NULL DEFAULT 'visible',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmark(id),
  CONSTRAINT fk_check_in_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE IF NOT EXISTS check_in_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_in_id BIGINT NOT NULL,
  user_id BIGINT,
  guest_id VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_like_check_in
    FOREIGN KEY (check_in_id) REFERENCES check_in(id)
);

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

CREATE INDEX idx_check_in_landmark_created ON check_in (landmark_id, created_at);
CREATE INDEX idx_check_in_like_target ON check_in_like (check_in_id);
CREATE INDEX idx_check_in_reply_target ON check_in_reply (check_in_id, created_at);
CREATE INDEX idx_correction_sample_feedback ON correction_sample (feedback_id);
