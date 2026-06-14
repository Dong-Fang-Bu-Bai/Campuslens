-- M5 审核日志表
-- 记录每次反馈状态变更，便于追溯谁在什么时候做了什么操作
CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feedback_id BIGINT NOT NULL,
  action VARCHAR(50) NOT NULL COMMENT 'status_change / comment',
  old_status VARCHAR(50),
  new_status VARCHAR(50),
  operator_id BIGINT,
  comment VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_log_feedback
    FOREIGN KEY (feedback_id) REFERENCES feedback(id),
  CONSTRAINT fk_audit_log_operator
    FOREIGN KEY (operator_id) REFERENCES admin_user(id)
);
