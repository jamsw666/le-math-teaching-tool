# 界面线框原型（静态 HTML）

对齐定稿 `specs/math-teaching-tool.md`（公式可视化编辑 + 投影批注）。**不含业务实现代码。**

## 打开

```bash
cd /workspace/tech-team/math-teaching-tool/prototypes/wireframes
python3 -m http.server 8790
```

访问 http://127.0.0.1:8790/

## 产品验收清单

| 项 | 线框页 | 预期 |
|---|---|---|
| 无术语 | ④ `formula-editor.html`、② 工作台 | 仅「公式编辑 / 点选模板 / 符号键」，无专业排版/引擎名 |
| 动画可跳过 | ② 工作台、③ 投影底栏 | 有「跳过动画」；与「下一步」并列 |
| 批注不挡主路径 | ③ `projection.html` | 画笔开/关、颜色≥2、粗细≥2、一键清空；底栏主控件独立且可点 |

## 页面

| 文件 | 内容 |
|---|---|
| `index.html` | 课时列表 |
| `workbench.html` | 课时工作台 |
| `projection.html` | 投影 + 批注工具条 + 安全底栏 |
| `formula-editor.html` | 点选模板 + 公式编辑区 |
