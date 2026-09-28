# 售后助手前端

Vue 3 + TypeScript + Vite + Vue Router。使用原生 fetch，不引入暂未使用的状态管理或 UI 组件库。
TypeScript 主要用于接口数据类型；Vue 页面使用 `<script setup lang="ts">`。

## 独立启动

在 IDEA 中打开项目，先运行后端 AftersalesApplication（默认 8080），再在另一个终端执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开 http://127.0.0.1:5173 ，在首页点击“检测连接”。后端没启动时前端仍可运行，会显示连接错误。
修改 Vue 文件会热更新；修改环境变量或 vite.config.ts 后需重启前端。
如果 5173 被占用会直接提示，不会自动换端口。

## 前后端分离如何工作

```text
浏览器 → 127.0.0.1:5173（Vue / Vite）
       → /api/... → Vite 开发代理 → 127.0.0.1:8080（Spring Boot）
```

浏览器只访问同源的 `/api` 路径，开发期无需在后端开放全域 CORS。
健康检测 `/api/system/health` 被代理为后端 `/actuator/health`；后续业务接口 `/api/orders` 等保留原始路径。
健康状态只证明服务连通，不能证明数据库、模型或业务已接入。
如果后端改端口，将 .env.example 复制为 .env.local，修改 BACKEND_URL 后重启前端。
模型 Key、数据库密码只保存在后端，不能放入前端；`VITE_` 前缀的变量会进入浏览器构建产物。

## 文件职责

| 文件 | 用途 |
| --- | --- |
| src/main.ts | 应用入口 |
| src/App.vue | 公共布局、导航 |
| src/router/index.ts | 页面路由 |
| src/views/ | 总览及业务占位页 |
| src/api/http.ts | JSON 请求、8 秒超时和错误处理 |
| src/api/system.ts | 健康接口及返回数据类型 |
| src/styles.css | 全局样式和响应式布局 |
| vite.config.ts | 本地端口、Vue 插件、后端代理 |

登录、订单、售后和 AI 对话尚未实现。界面中的待开发入口仅为路由占位，没有模拟成功结果。
后续 SSE 流式请求需要独立封装，不能使用当前只读取 JSON 的 getJson。

## 检查与构建

```powershell
npm run type-check
npm run build
npm run preview
```

build 会先检查类型，再生成 dist/；将 package-lock.json 一并提交，其他开发者使用 npm ci 复现依赖。
preview 用于检查构建产物，不是生产服务器，也不配置后端代理。

## 后续部署

前端 dist/ 由 Nginx 托管，后端独立运行 JAR。Nginx 需要：
- 对 Vue Router history 路径配置 `try_files $uri $uri/ /index.html`。
- 将 `/api/` 转发至 Spring Boot，保留业务接口前缀。
- 若保留连通检测页面，将 `/api/system/health` 单独映射到 `/actuator/health`。

Vite 的 server.proxy 仅在开发时生效，不会被写入 dist/。本次不执行生产部署。
