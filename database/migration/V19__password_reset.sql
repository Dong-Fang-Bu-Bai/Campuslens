ALTER TABLE app_user
  ADD CONSTRAINT uk_app_user_email UNIQUE (email);

CREATE TABLE password_reset_code (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  used_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_password_reset_code_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_password_reset_user_created
  ON password_reset_code (user_id, created_at);
