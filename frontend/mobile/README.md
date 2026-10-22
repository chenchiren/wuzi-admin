# 手机端 H5（frontend/mobile）

独立 Vite + Vue 3 项目（纯 CSS，无 Vant）。对齐《手机端设计说明》一期：

登录/免登、首页待办、全局扫码、扫码入库/出库、到货验收、领用申请、待我审批、库存查询、溯源、我的（离线队列）。

## 启动

```bash
cd frontend/mobile
npm install
npm run dev
# 默认 http://localhost:5174 ，/api 代理到 http://localhost:8081
```

生产构建：`npm run build` → `dist/`。

## 页面与路由（hash）

| 路由 | 页面 | 说明 |
|---|---|---|
| `#/login` | Login.vue | 账密登录 + 企微/钉钉免登（预留扫码） |
| `#/` | Home.vue | 待办计数、作业宫格、库存预警、最近扫码 |
| `#/scan` | Scan.vue | 扫码/手动输码 → `POST /api/m/scan/resolve` |
| `#/inbound` | Inbound.vue | 草稿入库单列表、扫码累加、确认入库 |
| `#/outbound` | Outbound.vue | 草稿出库单列表、扫码核验、确认出库 |
| `#/arrival` | Arrival.vue | 到货验收数量、保存草稿、提交验收 |
| `#/use-apply` | UseApply.vue | 我的领用 + 新建领用申请 |
| `#/approvals` | Approvals.vue | 待我审批（通过/驳回/详情） |
| `#/stocks` | Stocks.vue | 库存查询 + 盘点草稿暂存 |
| `#/trace` | Trace.vue | 对内/对外溯源（token） |
| `#/me` | Me.vue | 账号、离线队列同步、退出 |

底部 Tab：首页 | 扫码 | 库存 | 我的。设计 Token 见 `src/styles.css`（主色 `#2F6BFF` 等）。

## 前端调用的 API

### 已有移动端 `/api/m/**`

| 方法 | 路径 | 页面 |
|---|---|---|
| POST | `/api/m/auth/login` | Login |
| GET | `/api/m/me` | Login/Home/Me |
| GET | `/api/m/home` | Home |
| GET | `/api/m/todos` | Home |
| POST | `/api/m/scan/resolve` | Scan |
| GET | `/api/m/scan/recent` | Scan |
| GET | `/api/m/inbounds/{id}` | Inbound |
| POST | `/api/m/inbounds/{id}/scan` | Inbound |
| POST | `/api/m/inbounds/{id}/confirm` | Inbound |
| GET | `/api/m/outbounds/{id}` | Outbound |
| POST | `/api/m/outbounds/{id}/scan` | Outbound |
| POST | `/api/m/outbounds/{id}/confirm` | Outbound |
| GET | `/api/m/arrivals/{id}` | Arrival |
| POST | `/api/m/arrivals/{id}/submit` | Arrival |
| POST | `/api/m/material-use` | UseApply |
| POST | `/api/m/approvals/{id}/approve` | Approvals |
| POST | `/api/m/approvals/{id}/reject` | Approvals |
| GET | `/api/m/stocks` | Stocks |
| GET | `/api/m/trace/{traceToken}` | Trace |
| POST | `/api/m/sync/batch` | Me / 离线队列 |

### 本次新增（可插拔适配 + 离线）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/m/auth/wechat-work/login` | 企微 code 换 token |
| POST | `/api/m/auth/wechat-work/status` | 是否 mock / mock code |
| POST | `/api/m/auth/dingtalk/login` | 钉钉 authCode 换 token |
| POST | `/api/m/auth/dingtalk/status` | 是否 mock / mock code |
| POST | `/api/m/sync/batch` | 幂等批量 scan_log + 盘点草稿 |

### 复用 PC 列表接口（列表页无专用 m 接口时）

| 方法 | 路径 | 页面 |
|---|---|---|
| GET | `/api/inbound/orders` | Inbound 列表 |
| GET | `/api/outbound/orders` | Outbound 列表 |
| GET | `/api/purchase/arrivals` | Arrival 列表 |
| GET | `/api/material-use` | UseApply 列表 |
| GET | `/api/approvals/todo` | Approvals 列表 |
| GET | `/api/approvals/{id}` | Approvals 详情 |
| GET | `/api/materials` | UseApply 搜物资 |

响应约定：`{ code, msg, data }`，`code===200` 成功。

## 离线队列

`src/offline.js` + `localStorage`：

| Key | 内容 | 同步 |
|---|---|---|
| `wuzi_m_offline_scans` | 扫码日志 | `POST /api/m/sync/batch` → 失败则逐条 `POST /api/m/scan/resolve` |
| `wuzi_m_offline_drafts` | 盘点/验收数量草稿 | `POST /api/m/sync/batch` |
| `wuzi_m_offline_actions` | 作业动作（入库/出库扫码） | 按原 path/method/body 重试 |

- 每条带 `requestId`（uuid），服务端幂等（INSERTED / DUPLICATE）。
- `online` 事件 + 30s 定时自动冲刷；「我的 → 立即同步」手动冲刷。
- **禁止离线完成**：最终入库/出库确认必须在线。

## 企业微信 / 钉钉配置（可插拔）

未配置密钥时 **默认 mock**：`code=demo` 直接签发本地 JWT（映射 `admin` 用户）。  
两边独立，可只配其一；`wuzi.*.mock=false` 且密钥齐全时走真实 OAuth。

### 企业微信 `backend/src/main/resources/application.yml`

```yaml
wuzi:
  wecom:
    mock: true          # false 且 secret 非空 → 真实模式
    corp-id: ww1234567890abcdef
    agent-id: "1000002"
    secret: YOUR_WECOM_APP_SECRET
```

真实模式调用：

1. `GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=&corpsecret=`
2. `GET https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=&code=`
3. 用返回 `userid` 匹配 `sys_user.username`，命中则发 JWT。

前端：`POST /api/m/auth/wechat-work/login` body `{ "code": "demo" }`（或真实 OAuth code）。

### 钉钉

```yaml
wuzi:
  dingtalk:
    mock: true
    corp-id: ding123456
    agent-id: "1234567"
    app-key: dingxxxx
    app-secret: YOUR_DINGTALK_APP_SECRET
```

真实模式调用：

1. `POST https://api.dingtalk.com/v1.0/oauth2/userAccessToken`（appKey/appSecret/code）
2. `GET https://api.dingtalk.com/v1.0/contact/users/me`（x-acs-dingtalk-access-token）
3. 用 `unionId`/`nick` 匹配 `sys_user.username`。

前端：`POST /api/m/auth/dingtalk/login` body `{ "code": "demo" }`。

### 本地 API 根地址

- 默认 `src/api.js` 直连 `http://localhost:8081`（企微/钉钉 WebView 跨域已由后端 CORS 放开）。
- 可用 `localStorage.m_api_base` 或构建环境变量 `VITE_API_BASE` 覆盖。
- 开发时也可走 Vite 代理（相对路径 `/api`）。

## 一期范围（与设计说明 §13）

1. 企微/钉钉免登 + 独立 H5 账密登录  
2. 首页待办  
3. 全局扫码查询 + 溯源（对内）  
4. 扫码入库 / 扫码出库  
5. 到货验收（数量）  
6. 领用申请 + 待我审批  
7. 库存查询  
8. 离线扫码日志 / 盘点草稿缓冲与同步  
