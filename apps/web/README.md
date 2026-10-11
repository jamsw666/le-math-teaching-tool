# apps/web

React + Vite + TypeScript 可点原型（课堂投影 / 编辑 / 画布 / 公式 / 批注）。

后续将嵌入 **Tauri 2** 打成 Windows exe；本目录可先独立 `npm run dev` 验收。

## 开发

```bash
cd apps/web
npm install
npm run dev
```

默认 Vite 端口见终端输出（通常 `http://localhost:5173`）。

```bash
npm run build   # tsc + vite build
npm run preview # 预览构建产物
```

## 功能对照（产品线框 01–08）

| 线框 | 功能 | 入口 |
|---|---|---|
| 01 | 课时列表 + 导入入口 | 启动页 |
| 02 | 编辑/工作台（概念·例题·练习） | 打开课时 |
| 03 | 投影讲题（大字/步进/跳过动画/答案） | 进入投影 |
| 04 | 练习投影（答案默藏） | 投影 → 练习 |
| 05 | 公式可视化编辑（模板+点选槽） | 工作台 → 公式编辑 |
| 06 | 投影批注（笔/色/粗细/清空） | 投影顶栏 |
| 07 | 课程导入 → 教材感画布 | 导入课程 / 画布 |
| 08 | 概念 + 示例图（与下一步同步） | 概念页 |

老师侧 UI **不出现** LaTeX / MathLive 等字样；渲染用 katex（研发侧）。

## 对接存储

- 类型：`packages/core`（后续）；当前假数据在 `src/data/lessons.ts`
- 本地 API 草案：`docs/local-api.md`
- **无 HTTP Spring**；运行态计划 `invoke` Tauri commands

## 版权

样例为团队自编演示，页脚已声明；非出版社教材原文。
