# RAGFlow 对标调研

调研日期为 2026 年 11 月 26 日。产品行为以 RAGFlow v1.0.0 官方文档为准，源码固定在 v1.0.0 发布提交 `db1cd59`。该版本于 2026 年 11 月 6 日发布，服务端改写为 Go。本文通过官方文档与发布版源码交叉核对。 [官方仓库](https://github.com/infiniflow/ragflow) · [v1.0.0 发布](https://github.com/infiniflow/ragflow/releases/tag/v1.0.0) · [官方版本说明](https://ragflow.io/docs/v1.0.0/release_notes)

## 1. 在知识库内直接测试检索

**RAGFlow。** Retrieval Testing 可输入真实问题，查看结果数量、召回片段、相关度和来源文档，并按文件查看结果。测试可单独调整相似度阈值、关键词权重、重排模型、候选池大小和最终结果数，也支持元数据与跨语言检索。测试参数的作用域是当前测试。后端将参数传入共用的检索服务，返回切片和文档聚合结果。 [检索测试指南](https://ragflow.io/docs/v1.0.0/retrieval_testing) · [检索 HTTP API](https://ragflow.io/docs/v1.0.0/http_api_reference#retrieve-chunks) · [服务源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/service/chunk/chunk.go#L408-L455)

**学智汇。** 问答已在 Python 侧调用 `HybridRetriever`，并已有检索回归测试。建议在知识库详情新增「检索测试」，复用 Java 知识库鉴权和问答当前使用的检索与重排路径。首版输入问题与 Top K，输出切片、来源、命中通道、排序分与耗时，方便排查「原文里有，但问答没用上」。用当前项目实际返回的 RRF 排名分标注来源，避免将其显示为置信度百分比。文档筛选若用于限制检索范围，应在召回前对两路施加同一过滤条件。 [问答检索节点](../python-agent/agent/nodes/retrieve_agent.py) · [混合检索器](../python-agent/rag/retriever.py) · [现有回归测试](../python-agent/tests/test_retriever.py)

## 2. 将切片查看扩充为可纠错的解析结果

**RAGFlow。** 文档解析后可查看、搜索、编辑和新增切片，也可启用、停用或删除。支持原文预览的文件可以点击切片定位对应位置。v1.0.0 的切片编辑 HTTP 接口使用 `PATCH`，可更新正文、关键词、关联问题、标签、位置和可用状态。发布版服务在更新前检查数据集、文档和切片归属，写入新文本时重新分词。 [切片管理指南](https://ragflow.io/docs/v1.0.0/chunk_parsing_results_and_knowledge_fragment_management) · [更新切片 API](https://ragflow.io/docs/v1.0.0/http_api_reference#update-chunk) · [更新服务源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/service/chunk/chunk.go#L1099-L1199)

**学智汇。** 已有「向量详情」查看文档切片，接口返回文本列表。建议沿现有入口增加稳定 `chunk_id`、顺序、定位信息与启用状态，再开放停用和正文修订。停用应同时影响 BM25、向量路线和依赖这些结果的缓存。修订正文需重新嵌入并刷新关键词索引。重解析前应让用户知道该文档的手工修订会保留还是被覆盖。首版优先解决可读、可定位、可停用。 [知识库详情](../frontend/src/views/KnowledgeDetailView.vue) · [切片查询入口](../java-backend/src/main/java/com/agent/rag/controller/KnowledgeController.java) · [Milvus 切片存取](../python-agent/storage/milvus_store.py)

## 3. 拆开 PDF 解析质量与引用定位

**RAGFlow。** DeepDoc 在开源 v1.0.0 中使用 CPU 做版面分析、OCR 和表格识别，普通文本 PDF 可以选 Naive。发布版的 PDF 解析实现包含逐页处理、文档版面组合与表格处理。引用定位依赖结构化位置数据。后端读取切片 `position_int`，预览组件利用 `positions` 中的页和矩形坐标渲染高亮。位置数据来自解析流程，打开引用时使用已有位置预览原文。 [解析性能指南](https://ragflow.io/docs/v1.0.0/accelerate_doc_indexing) · [PDF 解析源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/deepdoc/parser/pdf/parser.go#L450-L635) · [引用数据源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/service/agentic_reference.go#L151-L175) · [预览组件](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/web/src/components/pdf-drawer/index.tsx#L37-L72) · [高亮坐标转换](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/web/src/utils/document-util.ts#L24-L57)

**学智汇。** `pypdf` 已逐页提取文本，当前实现将所有页拼成字符串再切片。聊天已展示来源片段，但页码没有贯穿解析、索引、实时回答和历史引用。建议先保存稳定切片 ID、文件版本和页范围，让普通 PDF 的回答引用直接打开对应页。旧文档和无页码来源继续展示已有片段。再用扫描课件、双栏论文和带表格讲义对比 OCR 与版面解析，验证阅读顺序、表头与数据对应、关键事实召回及处理耗时后，再为复杂 PDF 启用坐标高亮。预览沿用知识库访问校验。 [PDF 提取入口](../python-agent/api/knowledge_api.py) · [聊天引用展示](../frontend/src/views/ChatView.vue) · [Java 响应模型](../java-backend/src/main/java/com/agent/rag/dto/resp/ChatResponse.java)

## 4. 评估把混合召回下沉到索引引擎

**RAGFlow。** Elasticsearch 路径构建全文查询和向量 kNN，并用融合参数设置权重。Infinity 路径在引擎查询中调用 `MatchText`、`MatchDense` 和 `Fusion`。两种实现的具体融合与得分取得方式有区别，官方检索测试另提供相似度阈值和可选的应用层重排。 [Elasticsearch 源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/engine/elasticsearch/chunk.go#L1254-L1380) · [Infinity 源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/engine/infinity/chunk.go#L1245-L1373) · [检索测试参数](https://ragflow.io/docs/v1.0.0/retrieval_testing#configure-retrieval-parameters)

**学智汇。** 当前已用 RRF 融合 BM25 和 Milvus 向量命中。BM25 从持久层拉取文本后在进程内重建，Milvus 查询有 `limit=10000`。当前 BM25 返回只含正文和分数，融合按正文去重，因此仅关键词命中可能没有 `doc_id`，不同文档中的相同文字也可能合并。建议先让两路返回同一稳定切片 ID 和来源，并用相同问题记录冷启动、入库后首次查询、常态延迟及 Recall@K。现有 Compose 镜像为 Milvus v2.4.4。若基线测出回灌成本或覆盖问题，可评估 Milvus v2.5 起支持的原生全文 BM25，在新集合中用稀疏字段、稠密字段和中文分词器做对照，验证效果、版本兼容和回切路径后再迁移。 [当前检索器](../python-agent/rag/retriever.py) · [Milvus 回灌实现](../python-agent/storage/milvus_store.py) · [Compose 镜像](../docker-compose.yml) · [Milvus 2.5 版本说明](https://milvus.io/docs/v2.5.x/release_notes.md) · [Milvus 全文检索](https://milvus.io/docs/v2.5.x/full-text-search.md)

## 5. 让文档入库任务可追查

**RAGFlow。** 文件列表展示解析状态和进度，日志可按文件或数据集任务查看，文档任务记录包括启动时间、解析方法、执行状态及错误详情。发布版实现按任务运行记录进度和终态，日志弹窗还可加载先前的事件。 [文件与解析状态指南](https://ragflow.io/docs/v1.0.0/files_dataset_document_management#view-parsing-status) · [日志指南](https://ragflow.io/docs/v1.0.0/dataset_logs) · [任务服务源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/internal/service/ingestion_task_service.go#L1047-L1178) · [日志弹窗源码](https://github.com/infiniflow/ragflow/blob/db1cd5929a7885a3074547791f5ecc4d2f891739/web/src/pages/dataset/process-log-modal.tsx#L101-L228)

**学智汇。** 已有 Redis 队列、任务状态和前端轮询，并有失败后重新向量化的入口。任务 Redis Hash 的有效期为 24 小时。建议在文档行可查看最近的任务和分阶段状态，例如排队、读取、解析、切片、嵌入、写入索引与完成，展示开始/结束时间和可操作的失败原因。进度由 worker 实际完成的步骤或处理页数产生。重跑应有独立任务 ID，旧任务回写需核对是否仍是该文档的当前任务。 [任务存储](../python-agent/storage/task_store.py) · [向量化 worker](../python-agent/worker/vectorize_worker.py) · [文档任务轮询](../frontend/src/views/KnowledgeDetailView.vue)

## 建议交付顺序

1. **P0 · 切片来源、检索测试和评测基线。** 最小输入复现了两个结果：纯 BM25 命中的 `doc_id` 为 `None`，不同文档的同文片段会合并且后者覆盖前者的来源。统一两路召回与重排的切片标识，同时整理 30–50 条标有原文证据的校园问题，覆盖课程编号、奖学金表格、跨页制度和库内无答案。比较纯向量、混合检索、混合检索加重排的 Recall@5、首个正确命中的排名和检索 P95。现有反思节点可以触发重检索，重试用完后仍会进入回答节点。在无答案样例中加入「片段相关但缺关键事实」，单独检查能否说明证据不足。 [RRF 融合](../python-agent/rag/retriever.py#L116) · [重排](../python-agent/rag/reranker.py#L54) · [重试分支](../python-agent/agent/graph.py#L71) · [回答逻辑](../python-agent/agent/nodes/answer_agent.py#L87)

2. **P1 · 普通 PDF 页码、Word 表格和入库恢复。** 普通 PDF 先保存页范围。用一个含奖学金条件表格的 DOCX 做了最小复现，现有解析只返回标题段落，跳过表格单元格。可先补 DOCX 表头和数据行，再验证扫描件和多栏 PDF 需要的解析器。入库侧用 `BRPOP` 取出即出队，回调失败只记日志，替换向量时先删后写。建议加处理中记录、超时重领、按任务版本幂等回写，更新索引时等新版本写入成功再切换。验收时分别在解析、写索引与回调阶段中断任务，验证可以恢复，原有有效索引仍可查询。 [文件解析](../python-agent/api/knowledge_api.py#L133) · [入库 worker](../python-agent/worker/vectorize_worker.py#L38) · [向量写入](../python-agent/storage/milvus_store.py#L48)

3. **P2 · 用样本决定进阶能力。** 以扫描课件、双栏论文和表格文件试点 OCR 与区域高亮。规模对照覆盖超过 10,000 个切片的知识库，测冷启动、入库后首查、常态延迟及词法覆盖，再决定是否迁移到持久化词法索引。持续维护 Wiki 时，可在现有 `source_doc_ids` 上关联原始切片，将 Wiki 回答定位到一手资料。 [词法回灌](../python-agent/storage/milvus_store.py#L99) · [Wiki 来源](../python-agent/wiki/generator.py#L86)

本次核对的是本地源码和最小复现实例。线上语料的召回率、延迟和任务恢复效果有待按上述验收项测量。
