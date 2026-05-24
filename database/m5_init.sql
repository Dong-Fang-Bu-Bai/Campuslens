-- M5 用户反馈纠错模块初始化脚本
-- 负责人：庄子杰
-- 日期：2026-05-24
-- 命名规范：数据库使用 snake_case，JSON 接口使用 camelCase

-- 检索记录表
CREATE TABLE IF NOT EXISTS search_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  image_path VARCHAR(500) NOT NULL,
  landmark_id BIGINT,
  status VARCHAR(50) NOT NULL DEFAULT 'success',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_search_records_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmark(id)
);

-- 纠错反馈表
CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  search_record_id BIGINT NOT NULL,
  image_path VARCHAR(500),
  predicted_landmark_id BIGINT,
  confirmed_landmark_id BIGINT,
  feedback_type VARCHAR(50) NOT NULL COMMENT 'correct / wrong / uncertain',
  comment VARCHAR(500),
  status VARCHAR(50) NOT NULL DEFAULT 'pending' COMMENT 'pending / reviewed / adopted',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_feedback_search_record
    FOREIGN KEY (search_record_id) REFERENCES search_records(id),
  CONSTRAINT fk_user_feedback_predicted_landmark
    FOREIGN KEY (predicted_landmark_id) REFERENCES landmark(id),
  CONSTRAINT fk_user_feedback_confirmed_landmark
    FOREIGN KEY (confirmed_landmark_id) REFERENCES landmark(id)
);
