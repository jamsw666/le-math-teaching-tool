# D2 P1 · ScratchPad 拖动 Y clamp 自检（v2 · live dock.top）

**日期**：2026-10-13（Asia/Shanghai）  
**结论**：v2 已修 · `npm run build` PASS

## 根因（一行）

v1 按 `innerHeight − DOCK_SAFE(88)` 钳制，保证距视口底 ≥88；但 `.projection-dock` 在文档流中（1280×800 顶≈570、底≈665）并不贴视口底，故 `spaceToViewportBottom=88` 仍可 `gapAboveDock≈−142`。

## v2 改动

| 文件 | 改动 |
|------|------|
| `apps/web/src/components/ScratchPad.tsx` | `getDockCeiling()` 读 `.projection-dock.getBoundingClientRect().top`；`clampPos` / `centerPos` / open·resize·drag 均使 **panel.bottom ≤ dock.top**；dock 缺失/隐藏时回退 `innerHeight − DOCK_SAFE`；`maxHeight` 按可用高度收缩，避免 h > 可用空间时仍重叠 |

未改 CSS 主题/玻璃 token；未改 AI / lessonClient / SQLite 契约。v1 的 `svh/dvh` 高度回退链保留。

## Build

```text
cd apps/web && npm run build
→ tsc -b && vite build · OK（2026-10-13）
```

## 复测标准（请测试）

视口 1280×800（或同布局）：

1. **默认打开**：`gapAboveDock = dock.top − panel.bottom ≥ 0`（不得再出现 ≈−66）。
2. **下拖极限**：同上 `gapAboveDock ≥ 0`（不得再出现 ≈−142）；勿只验 `spaceToViewportBottom ≥ 88`。
3. **B3**：底栏下一步/跳过仍可点。
4. **B4**：关闭再开草稿保留。

截图建议对比：`reports/math-d2-verify-shots/10-p1-default.png`、`11-p1-drag-down.png`。
