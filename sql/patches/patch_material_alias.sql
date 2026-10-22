-- =============================================================
-- patch_material_alias.sql  物资别名词典（智能助手查询增强，幂等）
-- 解决同义词/俗名无法 LIKE 命中的问题：用户问"复印纸"→档案"A4打印纸"
-- 解析优先级：AiLookupService 先查本词典（等值），未中再走 LIKE 降级
-- 维护方式：直接 INSERT（P1 提供物资档案页内管理入口）
-- =============================================================

CREATE TABLE IF NOT EXISTS material_alias (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  material_id BIGINT       NOT NULL,
  alias       VARCHAR(100) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_alias (alias),
  KEY idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 种子：按档案名关联（档案不存在时该行自然不插入），幂等
INSERT INTO material_alias (material_id, alias)
SELECT m.id, t.alias
FROM material m
JOIN (
  SELECT 'A4打印纸' AS mname, '复印纸' AS alias UNION ALL
  SELECT 'A4打印纸', '打印纸' UNION ALL
  SELECT 'A4打印纸', 'A4纸' UNION ALL
  SELECT 'A4打印纸', 'A4' UNION ALL
  SELECT '中性签字笔', '签字笔' UNION ALL
  SELECT '中性签字笔', '水笔' UNION ALL
  SELECT '中性签字笔', '笔' UNION ALL
  SELECT '激光打印机硒鼓', '硒鼓' UNION ALL
  SELECT '激光打印机硒鼓', '粉盒' UNION ALL
  SELECT 'ABS安全帽', '安全帽' UNION ALL
  SELECT 'ABS安全帽', '头盔' UNION ALL
  SELECT '丁腈防护手套', '手套' UNION ALL
  SELECT '丁腈防护手套', '防护手套' UNION ALL
  SELECT '丁腈防护手套', '丁腈手套' UNION ALL
  SELECT '防砸劳保鞋', '劳保鞋' UNION ALL
  SELECT '工业润滑油', '润滑油' UNION ALL
  SELECT '工业润滑油', '机油' UNION ALL
  SELECT '五层瓦楞纸箱', '纸箱' UNION ALL
  SELECT '五层瓦楞纸箱', '包装箱' UNION ALL
  SELECT '单反相机套机', '相机' UNION ALL
  SELECT '单反相机套机', '单反' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '笔记本' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '电脑' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '笔记本电脑'
) t ON m.material_name = t.mname
WHERE NOT EXISTS (SELECT 1 FROM material_alias x WHERE x.alias = t.alias);
