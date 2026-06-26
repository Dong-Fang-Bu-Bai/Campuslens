ALTER TABLE check_in_reply
  ADD COLUMN parent_reply_id BIGINT NULL;

ALTER TABLE check_in_reply
  ADD COLUMN like_count INT NOT NULL DEFAULT 0;

ALTER TABLE check_in_reply
  ADD COLUMN reply_count INT NOT NULL DEFAULT 0;

ALTER TABLE check_in_reply
  ADD CONSTRAINT fk_check_in_reply_parent
    FOREIGN KEY (parent_reply_id) REFERENCES check_in_reply(id);

CREATE INDEX idx_check_in_reply_parent
  ON check_in_reply (parent_reply_id, created_at);

CREATE TABLE check_in_reply_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reply_id BIGINT NOT NULL,
  user_id BIGINT,
  guest_id VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_check_in_reply_like_reply
    FOREIGN KEY (reply_id) REFERENCES check_in_reply(id),
  CONSTRAINT fk_check_in_reply_like_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_check_in_reply_like_target
  ON check_in_reply_like (reply_id);
