---
name: diagram-design
description: Use when the user asks to draw architecture diagrams, system design diagrams, flowcharts, ER diagrams, sequence diagrams, deployment diagrams, class diagrams, or any diagram design/drawing task (画图 / 架构图 / 流程图 / 时序图 / 拓扑图 / 结构图). Produces Mermaid diagrams rendered natively in chat, based on the real code and docs of this repo.
---

# Diagram Design（画图 / 架构图）

## Mission

用最小的 Mermaid 图忠实回答用户的画图请求。图中的每个节点、连线和标签都必须来自用户输入或本仓库真实的代码 / 文档，不得虚构。

## 路由

按请求选择 Mermaid 图类型：

| 请求类型 | 语法 |
|----------|------|
| 架构图（分层 / 模块 / 依赖） | `flowchart TB`（用 `subgraph` 表达层/边界） |
| 流程 / 管线 / 数据流 | `flowchart LR` |
| 交互时序（谁调用谁） | `sequenceDiagram` |
| 数据库表结构 | `erDiagram` |
| 类 / 接口关系 | `classDiagram` |
| 状态流转 | `stateDiagram-v2` |
| 甘特 / 时间线 | `timeline` 或 `gantt` |

## 约定

- 标签用中文（与仓库文档一致）；节点 ID 用 ASCII（`subgraph shell["Tauri 壳"]`）。
- 分层架构用 `TB`，流程/管线用 `LR`；不要混用。
- 用 `subgraph` 划分边界（客户端 / 壳 / 服务 / 数据层），层名写清楚。
- 同类节点用同一形状（`[]` / `()` / `[()]` / `{{}}`）；一种颜色只表达一种含义，且只在有含义时使用 `classDef`。
- 连线标签写清条件与方向，如 `-- "存在 window.__TAURI_INTERNALS__ → invoke" -->`。
- 画架构图前先读相关目录与 `README.md` / `docs/`，以真实模块命名；不确定的依赖宁可不画。
- 架构图优先按 C4 层次自顶向下：先问用户要「上下文图」「容器图」还是「组件图」，默认给容器级（模块/进程 + 存储），不要一次塞进所有细节。

## 交付

- 默认：直接在回复里给出 fenced `mermaid` 代码块，不写文件。
- 用户要求存档时：写入 `docs/diagrams/<slug>.md`（slug 用小写 ASCII + 连字符），内容为「标题 + 一句话说明 + mermaid 块」，并在回复中给出文件路径。
- 修改已有图：直接给出修改后的完整 mermaid 块，并同步更新对应 `docs/diagrams/` 文件（若存在）。

## 检查

- 是否先回答了主要问题，层级清晰、一眼可读？
- 每个节点都能对应到仓库里的真实目录 / 模块 / 文档吗？
- 连线方向、条件标签与真实调用关系一致吗？
- 是否避免了把同一信息画第二遍（重复表格 / 重复图）？
