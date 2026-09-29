# 代码格式与注释

Java、Vue、TypeScript、CSS 和 YAML 源码统一使用 4 个空格缩进。根目录的 `.editorconfig` 为 IDEA 等编辑器提供默认规则，`.prettierrc.json` 为批量格式化提供统一配置。

在 IDEA 的 Editor → Code Style 中开启 EditorConfig 支持。自动格式化的最终检查以以下命令为准：

```powershell
cd frontend
npm run format
npm run format:check
```

格式化工具作为前端开发依赖安装，其中 prettier-plugin-java 用于解析 Java；不是后端运行依赖。上述命令同时处理前后端源码，不修改依赖锁文件、构建产物和历史评测快照。XML、SQL 与 PowerShell 文件继续保持原有格式。

注释重点解释类的职责、业务边界和并发处理原因，例如工具鉴权、用户确认、幂等请求、任务超时和会话切换。简单赋值、常规 getter 和显而易见的循环不逐行加注释。修改逻辑时同步检查注释是否仍然准确。
