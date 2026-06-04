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

ALTER TABLE search_record
  ADD COLUMN user_id BIGINT;

ALTER TABLE search_record
  ADD COLUMN user_type VARCHAR(50) NOT NULL DEFAULT 'guest';

ALTER TABLE feedback
  ADD COLUMN user_id BIGINT;

INSERT INTO app_user (username, password_hash, email, role, enabled)
VALUES ('admin', 'admin', NULL, 'admin', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role = VALUES(role),
  enabled = VALUES(enabled);
