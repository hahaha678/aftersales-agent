# 售后政策知识库（RAG）

本版本完成政策草稿管理、向量化发布、适用范围与日期过滤、原文检索、Agent 工具及来源追溯。政策与向量保存在 MySQL 8.0，Java 在内存中计算余弦相似度，适合个人项目的小型资料库；并非 MySQL 原生向量索引。

## 安装和启用

1. 从 [Ollama 官方下载页](https://ollama.com/download/windows) 安装 Windows 版本。安装完成后重新打开 PowerShell。默认服务地址为 `http://127.0.0.1:11434`，见 [Windows 官方说明](https://docs.ollama.com/windows)。
2. 下载本地 Embedding 模型。当前推荐的 [bge-m3 模型](https://ollama.com/library/bge-m3) 支持多语言，下载约 1.2 GB：

```powershell
ollama pull bge-m3
ollama list
```

若托盘应用没有启动服务，可在单独终端运行 `ollama serve`；服务已启动时不要重复运行。

3. 在 IDEA 的 Spring Boot 运行配置 → Environment variables 中增加以下变量，保留原有数据库、Redis、DeepSeek 配置：

```text
EMBEDDING_PROVIDER=ollama
EMBEDDING_BASE_URL=http://127.0.0.1:11434
EMBEDDING_MODEL=bge-m3
KNOWLEDGE_MIN_SCORE=0.65
```

`.env.example` 只是示例，Spring Boot 不会自动读取它。Embedding 不使用 DeepSeek 密钥。

4. 首次启动较慢时，先在 PowerShell 预热模型并验证接口：

```powershell
$body = @{ model='bge-m3'; input=@('鼠标按键失灵需要哪些材料？'); truncate=$false } | ConvertTo-Json
$result = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:11434/api/embed' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$result.embeddings[0].Count
```

5. 使用 `local` 配置重启后端，Flyway 自动应用 V5，建立政策、分块、发布锁及回答来源表。前端正常执行 `npm run dev`，导航栏进入“政策知识库”。

完整文档和向量保存在本机，向量化请求发送给本地 Ollama。**Agent 回答仍使用云端 DeepSeek，检索命中的原文片段会随上下文发送给它**，并非整个问答链路完全离线。

## 录入演示资料

用客服账号登录后，“政策知识库”页面下方可录入草稿。范围目前在界面提供 `MOUSE`（鼠标）和 `GLOBAL`（通用）；新商品范围需要同步维护界面和 Agent 的工具说明。

已有 5 份资料：[demo-policies.json](knowledge/demo-policies.json)，涵盖退货条件、故障材料、保修咨询、物流异常、退回准备。它们明确标注“演示”，不是真实商家政策。

可以手动复制其中一份内容到页面，也可以批量导入草稿。在本机终端设置客服登录接口返回的 accessToken 后运行：

```powershell
# POLICY_STAFF_TOKEN 仅设置在当前终端，不保存到脚本、Git 或聊天。
./scripts/Import-DemoPolicies.ps1
```

脚本只保存草稿，不调用向量模型或自动发布。登录后核对草稿，再点击“核对并发布”。未发布草稿现在支持编辑标题、原文和有效期；政策标识、范围及版本号不能原地变更。已发布政策修订时使用相同政策标识和范围、递增版本号新建草稿。

模型下载期间，可以使用客服页面的“导入文本文件”准备资料。支持 UTF-8 的 `.txt` / `.md`，最多 128 KB 且正文不超过 20000 字符；文件只在浏览器读取，保存草稿后才提交正文。已有正文时拒绝直接覆盖，请先保存或清空。Markdown 按纯文本显示，不执行 HTML。

编辑使用服务器返回的 `fingerprint` 检查并发修改；冲突时返回 409 并保留表单输入，需要重新核对再保存。发布期间若有人修改草稿，本次向量化结果不会落库，需重新发布，避免向量与正文错配。页面显示的“已配置”仅表示配置齐全，不表示模型已下载或服务已连通。

## 处理链路

```text
客服保存原文草稿
    → 发布时按 600 个 Unicode 字符切分，相邻重叠 80 字符
    → Ollama /api/embed 批量生成向量
    → 单事务写入分块并发布，下线同政策同范围旧版本

用户提问
    → Agent 调用 searchPolicies(question, scope)
    → 先筛选已发布、当前生效、指定范围或通用范围文档
    → 问题向量化，与原文片段计算余弦相似度
    → 词面 BM25 与阈值以上向量结果分别召回，RRF 融合名次
    → 去重后返回最多 4 段原文和来源信息
    → 模型依据片段回答；前端可查看本次检索来源和完整原文
```

向量接口超时 20 秒，发布失败时草稿保持未发布、旧版本继续可用。发布事务使用同政策范围锁，防止并发版本互相覆盖。下线和过期政策不进入新检索，已发布过的旧版本仍可通过历史引用查看。

政策不能改变 Java 的资格、金额与确认提交规则。检索为空时返回无依据；接口异常返回可识别业务错误，由 Agent 说明暂不可用。来源卡片展示真实工具检索结果，并持久化到对应任务；自然语言引用的完全忠实性仍需真实模型评测，不能仅凭提示词保证。

原文按纯文本显示，不执行 HTML、Markdown 脚本或文档里的指令。未来添加自动导入不可信文档时，仍需补充审核和更完整的提示注入评测。

## REST API

| 请求 | 用途 | 权限 |
|---|---|---|
| GET /api/knowledge/status | 配置状态（不是连通性检查） | 登录 |
| GET /api/staff/policies | 最近 100 个政策版本 | 客服 |
| POST /api/staff/policies | 创建草稿 | 客服 |
| PUT /api/staff/policies/{id} | 编辑草稿，携带 expectedFingerprint、title、content、effectiveFrom、effectiveUntil | 客服 |
| POST /api/staff/policies/{id}/publication | 生成向量并发布 | 客服 |
| POST /api/staff/policies/{id}/archival | 下线 | 客服 |
| GET /api/policies/{id} | 版本原文，未发布过的仅客服可读 | 登录 |
| POST /api/knowledge/searches | question、scope 检索 | 登录 |
| GET /api/agent-runs/{id}/sources | 本人回答的来源快照 | 本人任务 |

所有接口有 Swagger 注解。服务层分别管理业务状态，Mapper 负责 SQL，DTO/Query/PO/VO 按现有业务分包约定组织。

## 验证及限制

```powershell
./scripts/Run-AgentBaseline.ps1 -KnowledgeTest
```

脚本创建临时 MySQL（33310）和 Redis（16382），结束后关闭。测试用可预测向量验证事务、权限和检索过滤；本地 HTTP 模拟验证 Ollama 协议与错误向量校验。不调用付费模型，也不代表真实语义检索已验收。

2026-09-29 验证结果：隔离测试 7 项通过（知识库 HTTP/工具集成 3 项、分块与向量协议 2 项、DeepSeek 协议回归 2 项）；默认后端测试执行 9 项通过、41 项按环境条件跳过；前端 3 项测试、类型检查、生产构建和格式检查通过。V5 已在隔离 MySQL 8.0.43 验证，未对日常数据库执行迁移。

2026-09-29 已安装 Ollama 并完成首轮真实 bge-m3 + DeepSeek 评测，详见 [RAG 基线报告](evaluations/rag-baseline-v1-20260929/REPORT.md)。本轮发现工具选择、同义问法召回和时间表述仍需改进。以下为手动联调示例：

| 问题 | 预期 |
|---|---|
| 鼠标按键失灵需要哪些材料？ | 鼠标材料文档，说明文字描述和演示属性 |
| 鼠标是终身免费换新吗？ | 保修文档；不能作终身承诺 |
| 超过7天还可以申请退货退款吗？ | 通用规则；具体订单需另查资格 |
| 退货地址是什么？ | 明确资料未提供，不编造地址 |
| 耳机保修几年？ | 不套用鼠标条款，澄清范围或说明缺少依据 |
| 下线材料文档后再问材料要求 | 不再引用已下线文档作为新依据 |

阈值 0.65 是向量通路待校准的初值，不是准确率。混合检索默认开启（KNOWLEDGE_HYBRID_SEARCH=true），设置 false 可回退纯向量检索，无需重新发布文档。中文按相邻双字、英文按完整词计算 BM25，去除部分通用词后至少匹配两个不同词面才进入词面通路；两路各取前 20 个候选，用 RRF（k=60）融合，最终取 4 段。它是小规模内存实现，不是 Elasticsearch 或语义重排序模型。词面匹配不理解否定，结论必须依照召回的完整原文。

来源 score 始终表示原始向量相似度，词面召回的片段可能低于 0.65；不是融合排名分或正确率。两路都在已发布、生效日期和商品范围过滤后的候选中执行。向量服务故障仍按原逻辑报错，不隐式降级为关键词查询。

Agent 政策工具在每轮第一次选择非 GLOBAL 范围时固定该范围，后续可查询通用 GLOBAL 规则，但不能切换到另一商品获取依据；新一轮重置，跨商品比较需分轮咨询。这是单轮一致性保护，不保证模型第一次选择的范围正确，也不等同于用户权限校验。商品归属和订单权限仍由业务服务校验。

每次最多 4 段、文档 2 万字符；超过 5000 个待检索片段时明确拒绝，应迁移到专门的检索存储。尚未实现 PDF/Word 解析、语义重排序或后台异步入库。

模型身份由服务类型、地址和模型名生成指纹。修改这些配置需发布新版本重建索引；同名模型被覆盖更新无法自动识别，需人工重建。历史评测文件保持不变，新增 RAG 需要另建评测版本。
