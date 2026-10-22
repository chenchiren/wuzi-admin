-- =============================================================
-- patch_ai_assistant.sql  智能助手 P0（可重复执行，幂等）
-- 3 张表 + 权限种子（菜单 ai + 按钮 ai:chat，全角色预授）
-- 编码：UTF-8 无 BOM；导入必须 --default-character-set=utf8mb4
-- =============================================================

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL,
  title       VARCHAR(100) NOT NULL DEFAULT '新对话',
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_session_user (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT      NOT NULL,
  role        VARCHAR(16) NOT NULL COMMENT 'user/assistant/tool',
  content     MEDIUMTEXT  NULL,
  tool_name   VARCHAR(64) NULL,
  tool_args   JSON        NULL,
  tokens      INT         NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_msg_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_tool_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  session_id    BIGINT      NULL,
  tool_name     VARCHAR(64) NOT NULL,
  args_json     JSON        NULL,
  result_digest VARCHAR(500) NULL,
  cost_ms       INT         NULL,
  success       TINYINT     NOT NULL DEFAULT 1,
  error_msg     VARCHAR(500) NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_tool_user (user_id, created_at),
  KEY idx_ai_tool_name (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 权限种子：不硬编码 id，存在性判断保证幂等
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, path, sort_no)
SELECT 0, 'ai', '智能助手', 'MENU', NULL, 950
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'ai');

SET @ai_menu := (SELECT id FROM sys_permission WHERE perm_code = 'ai');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, sort_no)
SELECT @ai_menu, 'ai:chat', '智能对话', 'BUTTON', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'ai:chat');

-- 全角色预授（新增角色后重跑本补丁即可补授）
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.is_deleted = 0
  AND p.perm_code IN ('ai', 'ai:chat');
