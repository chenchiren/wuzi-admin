-- 按钮级权限种子：sys_permission 菜单 + 按钮
-- 幂等：按 perm_code 更新；ADMIN(1) 授予全部；WAREHOUSE/PURCHASER/EMPLOYEE/FINANCE 按岗位预授

DELETE FROM sys_role_permission WHERE role_id IN (1,2,3,4,5,6);
DELETE FROM sys_permission WHERE perm_code LIKE '%';

-- ========== 系统管理 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(100, 0, 'sys', '系统管理', 'MENU', NULL, 900),
(110, 100, 'sys:user', '用户管理', 'MENU', '/users', 10),
(111, 110, 'sys:user:create', '新增用户', 'BUTTON', NULL, 1),
(112, 110, 'sys:user:update', '编辑用户', 'BUTTON', NULL, 2),
(113, 110, 'sys:user:delete', '删除用户', 'BUTTON', NULL, 3),
(114, 110, 'sys:user:reset-pwd', '重置密码', 'BUTTON', NULL, 4),
(115, 110, 'sys:user:assign-role', '分配角色', 'BUTTON', NULL, 5),
(120, 100, 'sys:role', '角色权限', 'MENU', '/roles', 20),
(121, 120, 'sys:role:create', '新增角色', 'BUTTON', NULL, 1),
(122, 120, 'sys:role:update', '编辑角色', 'BUTTON', NULL, 2),
(123, 120, 'sys:role:delete', '删除角色', 'BUTTON', NULL, 3),
(124, 120, 'sys:role:assign-perm', '分配权限', 'BUTTON', NULL, 4),
(130, 100, 'sys:dept', '部门管理', 'MENU', '/depts', 30),
(131, 130, 'sys:dept:create', '新增部门', 'BUTTON', NULL, 1),
(132, 130, 'sys:dept:update', '编辑部门', 'BUTTON', NULL, 2),
(133, 130, 'sys:dept:delete', '删除部门', 'BUTTON', NULL, 3),
(140, 100, 'sys:import', 'Excel导入', 'MENU', '/excel-import', 40),
(141, 140, 'sys:import:create', '执行导入', 'BUTTON', NULL, 1),
(150, 100, 'sys:log', '操作日志', 'MENU', '/logs', 50),
(160, 100, 'sys:dict', '字典管理', 'MENU', '/dicts', 60),
(161, 160, 'sys:dict:update', '编辑字典', 'BUTTON', NULL, 1),
(162, 160, 'sys:dict:create', '新增字典', 'BUTTON', NULL, 2),
(163, 160, 'sys:dict:delete', '删除字典', 'BUTTON', NULL, 3),
(170, 100, 'flow:config', '审批流配置', 'MENU', '/flow-configs', 70);

-- ========== 基础数据 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(200, 0, 'base', '基础数据', 'MENU', NULL, 100),
(210, 200, 'base:material', '物资档案', 'MENU', '/materials', 10),
(211, 210, 'base:material:create', '新增物资', 'BUTTON', NULL, 1),
(212, 210, 'base:material:update', '编辑物资', 'BUTTON', NULL, 2),
(213, 210, 'base:material:delete', '删除物资', 'BUTTON', NULL, 3),
(220, 200, 'base:category', '物资分类', 'MENU', '/categories', 20),
(221, 220, 'base:category:create', '新增分类', 'BUTTON', NULL, 1),
(222, 220, 'base:category:update', '编辑分类', 'BUTTON', NULL, 2),
(223, 220, 'base:category:delete', '删除分类', 'BUTTON', NULL, 3),
(230, 200, 'base:supplier', '供应商', 'MENU', '/suppliers', 30),
(231, 230, 'base:supplier:create', '新增供应商', 'BUTTON', NULL, 1),
(232, 230, 'base:supplier:update', '编辑供应商', 'BUTTON', NULL, 2),
(233, 230, 'base:supplier:delete', '删除供应商', 'BUTTON', NULL, 3),
(234, 230, 'base:supplier-product:create', '新增供应商产品', 'BUTTON', NULL, 4),
(235, 230, 'base:supplier-product:update', '编辑供应商产品', 'BUTTON', NULL, 5),
(236, 230, 'base:supplier-product:delete', '删除供应商产品', 'BUTTON', NULL, 6),
(240, 200, 'base:tax-rate', '税率字典', 'MENU', '/tax-rates', 40),
(241, 240, 'base:tax-rate:create', '新增税率', 'BUTTON', NULL, 1),
(242, 240, 'base:tax-rate:update', '编辑税率', 'BUTTON', NULL, 2),
(243, 240, 'base:tax-rate:delete', '删除税率', 'BUTTON', NULL, 3),
(244, 240, 'base:tax-rate:toggle', '启停税率', 'BUTTON', NULL, 4),
(250, 200, 'base:price-trend', '价格趋势', 'MENU', '/price-trends', 50);

-- ========== 采购 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(300, 0, 'po', '采购管理', 'MENU', NULL, 200),
(310, 300, 'po:apply', '采购申请', 'MENU', '/purchase-applies', 10),
(311, 310, 'po:apply:create', '新建申请', 'BUTTON', NULL, 1),
(312, 310, 'po:apply:submit', '提交申请', 'BUTTON', NULL, 2),
(313, 310, 'po:apply:cancel', '作废申请', 'BUTTON', NULL, 3),
(314, 310, 'po:apply:delete', '删除申请', 'BUTTON', NULL, 4),
(320, 300, 'po:inquiry', '询价', 'MENU', '/inquiries', 20),
(321, 320, 'po:inquiry:create', '生成询价', 'BUTTON', NULL, 1),
(322, 320, 'po:inquiry:quote', '录入报价', 'BUTTON', NULL, 2),
(323, 320, 'po:inquiry:select', '选定供应商', 'BUTTON', NULL, 3),
(330, 300, 'po:order', '采购订单', 'MENU', '/purchase-orders', 30),
(331, 330, 'po:order:create', '新建订单', 'BUTTON', NULL, 1),
(332, 330, 'po:order:submit', '提交订单', 'BUTTON', NULL, 2),
(333, 330, 'po:order:cancel', '作废订单', 'BUTTON', NULL, 3),
(340, 300, 'po:return', '采购退货', 'MENU', '/purchase-returns', 40),
(341, 340, 'po:return:create', '新建退货', 'BUTTON', NULL, 1),
(342, 340, 'po:return:submit', '提交退货', 'BUTTON', NULL, 2),
(343, 340, 'po:return:confirm', '确认退货', 'BUTTON', NULL, 3),
(344, 340, 'po:return:cancel', '取消退货', 'BUTTON', NULL, 4),
(345, 340, 'po:return:delete', '删除退货', 'BUTTON', NULL, 5),
(350, 300, 'po:arrival', '到货验收', 'MENU', '/arrivals', 50),
(351, 350, 'po:arrival:create', '生成到货', 'BUTTON', NULL, 1),
(352, 350, 'po:arrival:submit', '提交到货', 'BUTTON', NULL, 2),
(353, 350, 'po:arrival:complete', '确认到货', 'BUTTON', NULL, 3),
(354, 350, 'po:arrival:cancel', '作废到货', 'BUTTON', NULL, 4),
(360, 300, 'approval', '审批待办', 'MENU', '/approvals', 60),
(361, 360, 'approval:approve', '审批通过', 'BUTTON', NULL, 1),
(362, 360, 'approval:reject', '审批驳回', 'BUTTON', NULL, 2),
(363, 360, 'approval:transfer', '审批转交', 'BUTTON', NULL, 3),
(364, 360, 'approval:add-sign', '审批加签', 'BUTTON', NULL, 4);

-- ========== 仓储 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(400, 0, 'wh', '仓储作业', 'MENU', NULL, 300),
(410, 400, 'wh:warehouse', '仓库库位', 'MENU', '/warehouses', 10),
(411, 410, 'wh:warehouse:create', '新建仓库', 'BUTTON', NULL, 1),
(412, 410, 'wh:warehouse:update', '编辑仓库', 'BUTTON', NULL, 2),
(413, 410, 'wh:warehouse:delete', '删除仓库', 'BUTTON', NULL, 3),
(414, 410, 'wh:location:create', '新建库位', 'BUTTON', NULL, 4),
(415, 410, 'wh:location:update', '编辑库位', 'BUTTON', NULL, 5),
(416, 410, 'wh:location:delete', '删除库位', 'BUTTON', NULL, 6),
(420, 400, 'wh:inbound', '入库', 'MENU', '/inbounds', 20),
(421, 420, 'wh:inbound:create', '新建入库', 'BUTTON', NULL, 1),
(422, 420, 'wh:inbound:confirm', '确认入库', 'BUTTON', NULL, 2),
(430, 400, 'wh:outbound', '出库', 'MENU', '/outbounds', 30),
(431, 430, 'wh:outbound:create', '新建出库', 'BUTTON', NULL, 1),
(432, 430, 'wh:outbound:submit', '提交出库', 'BUTTON', NULL, 2),
(433, 430, 'wh:outbound:confirm', '确认出库', 'BUTTON', NULL, 3),
(434, 430, 'wh:outbound:red-flush', '出库红冲', 'BUTTON', NULL, 4),
(440, 400, 'stock:view', '库存查询', 'MENU', '/stock', 40),
(450, 400, 'wh:transfer', '调拨', 'MENU', '/transfers', 50),
(451, 450, 'wh:transfer:create', '新建调拨', 'BUTTON', NULL, 1),
(452, 450, 'wh:transfer:submit', '提交调拨', 'BUTTON', NULL, 2),
(453, 450, 'wh:transfer:out', '调出', 'BUTTON', NULL, 3),
(454, 450, 'wh:transfer:in', '调入', 'BUTTON', NULL, 4),
(460, 400, 'wh:inventory', '盘点', 'MENU', '/inventory-checks', 60),
(461, 460, 'wh:inventory:create', '新建盘点', 'BUTTON', NULL, 1),
(462, 460, 'wh:inventory:submit', '提交盘点', 'BUTTON', NULL, 2),
(463, 460, 'wh:inventory:confirm', '确认调账', 'BUTTON', NULL, 3);

-- ========== 领用资产 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(500, 0, 'asset', '领用资产', 'MENU', NULL, 400),
(510, 500, 'asset:use', '领用', 'MENU', '/material-use', 10),
(511, 510, 'asset:use:create', '新建领用', 'BUTTON', NULL, 1),
(512, 510, 'asset:use:submit', '提交领用', 'BUTTON', NULL, 2),
(513, 510, 'asset:use:approve', '审批领用', 'BUTTON', NULL, 3),
(514, 510, 'asset:use:issue', '领用发料', 'BUTTON', NULL, 4),
(520, 500, 'asset:return', '退库', 'MENU', '/return-stores', 20),
(521, 520, 'asset:return:create', '新建退库', 'BUTTON', NULL, 1),
(522, 520, 'asset:return:submit', '提交退库', 'BUTTON', NULL, 2),
(523, 520, 'asset:return:accept', '退库验收', 'BUTTON', NULL, 3),
(524, 520, 'asset:return:confirm', '退库入库', 'BUTTON', NULL, 4),
(530, 500, 'asset:borrow', '借用', 'MENU', '/borrows', 30),
(531, 530, 'asset:borrow:create', '新建借用', 'BUTTON', NULL, 1),
(532, 530, 'asset:borrow:submit', '提交借用', 'BUTTON', NULL, 2),
(533, 530, 'asset:borrow:approve', '审批借用', 'BUTTON', NULL, 3),
(534, 530, 'asset:borrow:lend', '借出', 'BUTTON', NULL, 4),
(535, 530, 'asset:borrow:return', '归还', 'BUTTON', NULL, 5),
(540, 500, 'asset:scrap', '报废', 'MENU', '/scraps', 40),
(541, 540, 'asset:scrap:create', '新建报废', 'BUTTON', NULL, 1),
(542, 540, 'asset:scrap:submit', '提交报废', 'BUTTON', NULL, 2),
(543, 540, 'asset:scrap:confirm', '确认报废', 'BUTTON', NULL, 3),
(544, 540, 'asset:scrap:reject', '驳回报废', 'BUTTON', NULL, 4),
(550, 500, 'alert:view', '库存预警', 'MENU', '/alerts', 50),
(551, 550, 'alert:handle', '处理预警', 'BUTTON', NULL, 1);

-- ========== 财务 / 溯源 / 报表 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(600, 0, 'fin', '财务结算', 'MENU', NULL, 500),
(610, 600, 'fin:invoice', '发票登记', 'MENU', '/finance', 10),
(611, 610, 'fin:invoice:create', '登记发票', 'BUTTON', NULL, 1),
(612, 610, 'fin:invoice:match', '三单匹配', 'BUTTON', NULL, 2),
(613, 610, 'fin:settle:create', '生成结算', 'BUTTON', NULL, 3),
(700, 0, 'trace', '溯源标签', 'MENU', NULL, 600),
(710, 700, 'trace:qr', '二维码溯源', 'MENU', '/trace', 10),
(711, 710, 'trace:qr:generate', '生成二维码', 'BUTTON', NULL, 1),
(712, 710, 'trace:qr:activate', '激活二维码', 'BUTTON', NULL, 2),
(720, 700, 'label:print', '标签打印', 'MENU', '/label-print', 20),
(721, 720, 'label:print:generate', '生成标签', 'BUTTON', NULL, 1),
(800, 0, 'report', '分析报表', 'MENU', NULL, 700),
(810, 800, 'report:stats', '统计看板', 'MENU', '/stats', 10),
(820, 800, 'report:advanced', '高级报表', 'MENU', '/reports', 20),
(900, 0, 'msg', '消息中心', 'MENU', '/messages', 800),
(901, 900, 'msg:read', '标记已读', 'BUTTON', NULL, 1),
(902, 900, 'msg:read-all', '全部已读', 'BUTTON', NULL, 2);

-- ========== ADMIN 授予全部 ==========
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE is_deleted = 0;

-- 仓管：仓储+领用执行+预警+盘点+扫码标签
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'wh:%' OR perm_code LIKE 'stock:%' OR perm_code LIKE 'asset:%'
  OR perm_code LIKE 'alert:%' OR perm_code LIKE 'trace:%' OR perm_code LIKE 'label:%'
  OR perm_code LIKE 'msg:%' OR perm_code LIKE 'approval:%'
  OR perm_code IN ('base','base:material','base:category','base:supplier','base:price-trend')
);

-- 采购：采购域+供应商+询价
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'po:%' OR perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code LIKE 'base:%' OR perm_code IN ('base','po','report','report:stats')
);

-- 员工：申请类 + 查询
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 4, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code IN ('po:apply','po:apply:create','po:apply:submit','po:apply:cancel','po:apply:delete')
  OR perm_code IN ('asset:use','asset:use:create','asset:use:submit')
  OR perm_code IN ('asset:return','asset:return:create','asset:return:submit')
  OR perm_code IN ('asset:borrow','asset:borrow:create','asset:borrow:submit','asset:borrow:return')
  OR perm_code LIKE 'msg:%' OR perm_code IN ('stock:view','base:material','base','po','asset')
  OR perm_code IN ('trace:qr','trace:qr:generate','label:print','report:stats')
);

-- 财务：发票结算 + 审批
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 5, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'fin:%' OR perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code IN ('base','base:tax-rate','base:tax-rate:create','base:tax-rate:update','base:tax-rate:delete','base:tax-rate:toggle')
  OR perm_code IN ('po','po:order','po:return','report','report:stats','report:advanced','stock:view')
);

-- 部门负责人（若存在 role_id=6 DEPT_MANAGER）
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 6, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code IN ('po:apply','po:apply:create','po:apply:submit','po:apply:cancel','po:apply:delete')
  OR perm_code IN ('asset:use','asset:use:create','asset:use:submit','asset:use:approve')
  OR perm_code IN ('asset:return','asset:return:create','asset:return:submit')
  OR perm_code IN ('asset:borrow','asset:borrow:create','asset:borrow:submit','asset:borrow:approve')
  OR perm_code IN ('asset:scrap','asset:scrap:create','asset:scrap:submit')
  OR perm_code IN ('stock:view','base','base:material','report:stats')
) AND EXISTS (SELECT 1 FROM sys_role r WHERE r.id = 6 AND r.is_deleted = 0);
