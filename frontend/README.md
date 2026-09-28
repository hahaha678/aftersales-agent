# 售后助手前端

Vue 3 + TypeScript + Vite + Vue Router，原生 fetch 调用 REST API。

## 启动

在 IDEA 启动后端 local 配置，确认 MySQL、Redis（包含 REDIS_PASSWORD）可连接，然后在项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开 http://127.0.0.1:5173 。首次安装依赖才需要 npm ci；前后端独立启动，启动 Spring Boot 不会自动启动 Vite。5173 被占用时不会自动换端口。

后端默认 8080。如果改端口，复制 .env.example 为 .env.local，修改 BACKEND_URL 后重启 Vite。Vue 文件修改会热更新。

登录需要数据库已有用户。仅在开发库中，按 [数据库说明](../docs/DATABASE_DESIGN.md) 手动运行完整的 [演示数据脚本](../deploy/mysql/02-seed-demo-data.sql)，公开演示账号 demo_customer，密码 DemoPass123!。脚本已包含选择 aftersales_agent 库和导入开关，无需拼接 SQL；首次导入要求业务表为空，已导入则跳过，不覆盖已有数据。401 可能表示账号不存在、密码不符或账号停用；503 应检查后端及数据库、Redis连接；501 表示功能尚未启用（例如 scaffold 配置）。

## 页面与接口

| 页面 | 功能 | API |
| --- | --- | --- |
| / | 服务首页、业务入口 | — |
| /login | 登录、错误提示、登录后返回目标订单页 | POST /api/sessions |
| 公共布局 | 恢复用户信息、退出会话 | GET /api/users/me、DELETE /api/sessions/current |
| /orders | 订单号精确查询、状态筛选、分页、空状态、失败重试 | GET /api/orders |
| /orders/:id | 商品清单、实付金额、订单时间、物流轨迹 | GET /api/orders/{id}、GET /api/orders/{id}/shipments |
| /assistant、/aftersales | 标明暂未开放的服务说明 | 尚未接入 |

列表筛选和页码保存在 URL，详情返回列表时保留。ID、金额采用字符串，避免大整数和小数精度损失。日期按浏览器本地时区展示。商品金额显示整行实付，当前不将 availableAftersalesQuantity 当作真实售后资格。

物流独立显示加载和错误状态，失败时不隐藏已取得的商品信息。请求默认 10 秒超时；切换页面取消未完成请求，忽略过期响应。错误展示后端 message 和 requestId，方便定位日志。

## 登录状态

- 使用后端已有的随机 Bearer token，不是 JWT。
- sessionStorage 仅保存当前标签页的 token 和过期时间，不保存密码。页面刷新后通过 /users/me 重新读取身份。
- 访问订单页需要登录；接口返回 401 时清理失效会话并跳转登录页。路由保护不能代替后端订单归属校验。
- 注销成功后清理本地会话；依赖故障导致注销失败时保留会话并提示重试。
- sessionStorage 不是加密存储，也不能防御 XSS；本项目不使用 v-html 输出接口数据。部署仍需 HTTPS 和适当 CSP。

## 代码结构

| 目录/文件 | 职责 |
| --- | --- |
| src/views/ | 首页、登录、订单列表、订单详情、未开放/404 页面 |
| src/components/ErrorNotice.vue | 统一错误及请求编号 |
| src/api/types.ts、orders.ts | 接口类型和订单请求 |
| src/api/http.ts | JSON 请求、Bearer、超时、取消、错误处理 |
| src/services/auth.ts | 登录、恢复身份、注销 |
| src/state/session.ts | Vue 响应式会话和标签页存储 |
| src/router/index.ts | 页面路由和登录守卫 |
| src/utils/format.ts | 状态、日期、金额展示 |
| src/styles.css | 公共样式及响应式布局 |

前端不持有数据库、Redis、模型 API 密钥。浏览器请求同源 /api，经 Vite 开发代理转发后端。VITE_ 前缀变量会进入浏览器产物，不能保存密钥。

## 验证记录（2026-09-28）

- npm run build 通过，包含 vue-tsc 类型检查及 Vite 生产构建。
- 真实 8080 后端：登录接口可达，演示账号返回 401，页面正确提示。当前数据库的成功登录和订单数据联调仍需有效账号，未声称完成。
- 独立 5174/18081 临时 HTTP 测试服务：验证成功登录、刷新恢复用户、分页及末页按钮、状态筛选重置页码、详情返回保留条件、空结果、503 请求编号、重置恢复、商品清单、物流时间线及未发货空状态、注销。测试使用内存固定数据，没有写入用户数据库，也不注入正常 5173 服务。
- 检查了桌面及移动端布局；正常开发入口为 5173，独立测试服务验证后关闭。

后续增加售后/Agent 时，需同时补齐后端业务与前端交互。SSE 应独立封装流式读取，不使用当前只解析 JSON 的请求方法。

## 构建和部署

```powershell
npm run type-check
npm run build
npm run preview
```

dist/ 为构建结果。preview 仅供预览，不是生产服务，也未配置后端代理。生产可通过 Nginx 托管 dist，将 /api/ 转发到 Spring Boot 并保留路径，对 history 路由配置 `try_files $uri $uri/ /index.html`。Vite server.proxy 不会写入 dist。本次未执行生产部署。
