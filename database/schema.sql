-- CampusLens initial schema draft.
-- Field names use snake_case in database and camelCase in JSON APIs.

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
  cover_image_url VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS landmark_image (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  landmark_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
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
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_search_record_best_landmark
    FOREIGN KEY (best_landmark_id) REFERENCES landmark(id)
);

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

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL DEFAULT 'admin',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
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
