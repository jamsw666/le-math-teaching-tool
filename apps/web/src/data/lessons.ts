import type { Lesson } from '../types'

/**
 * 自编演示课时（函数与导数方向）。
 * 非教材原文；仅供课堂工具 UI 演示。
 */
export const SAMPLE_LESSONS: Lesson[] = [
  {
    id: 'lesson-monotonicity',
    title: '第1节 · 导数的概念',
    subtitle: '平均变化率 → 瞬时变化率',
    gradeHint: '高中 · 函数与导数 · 样例第 1 节',
    sourceLabel: '内置 · 自编演示',
    canvas: {
      chapterLabel: '第1节 · 导数的概念',
      goals: ['说清平均 → 瞬时', '写出导数定义式直觉', '看图识别「瞬时快慢」'],
      tip: '「平均是一段的快慢；导数是压成一瞬间的快慢。」',
      blocks: [
        {
          id: 'b1',
          tag: '情境导入（教材味）',
          tagTone: 'orange',
          title: '高铁进站时，速度如何「瞬间」变化？',
          body: '先看一段路程的平均快慢，再问：某一瞬间有多快？引出「瞬时变化率」。',
          illustrationId: 'train-path',
          x: 36,
          y: 28,
          w: 340,
        },
        {
          id: 'b2',
          tag: '复习旧知',
          tagTone: 'green',
          title: '平均变化率',
          body: '从 x=a 到 x=b，像「整段路程的平均速度」。',
          formulaPlain: '[f(b)−f(a)] / (b−a)',
          x: 420,
          y: 36,
          w: 280,
        },
        {
          id: 'b3',
          tag: '提出新问题',
          tagTone: 'blue',
          title: '当间隔越来越短呢？',
          body: '让 b 逼近 a，平均变化率会趋向一个确定值吗？——这就是导数要回答的。',
          illustrationId: 'secant-tangent',
          x: 740,
          y: 48,
          w: 300,
        },
        {
          id: 'b4',
          tag: '本课目标',
          tagTone: 'orange',
          title: '今天要会三件事',
          body: '见下方目标条。',
          x: 120,
          y: 320,
          w: 520,
        },
        {
          id: 'b5',
          tag: '课堂提示卡',
          tagTone: 'amber',
          title: '老师可口述的一句话',
          body: '「平均是一段的快慢；导数是压成一瞬间的快慢。」',
          x: 680,
          y: 340,
          w: 360,
        },
      ],
    },
    concepts: [
      {
        id: 'c1',
        title: '① 平均变化率',
        bodyText: '整段路程的「平均速度」——先建立直觉。从 x=a 到 x=b，用函数值差除以自变量差。',
        bodyTex: '\\frac{f(b)-f(a)}{b-a}',
        diagram: {
          kind: 'life',
          caption: '生活：整段路程的平均快慢',
          illustrationId: 'avg-rate',
        },
      },
      {
        id: 'c2',
        title: '② 瞬时变化率',
        bodyText:
          '把时间间隔不断缩短，得到的极限快慢，就是该时刻的瞬时变化率。在图像上，它对应切线的斜率。',
        bodyTex: "f'(x_0)=\\lim_{h\\to 0}\\frac{f(x_0+h)-f(x_0)}{h}",
        diagram: {
          kind: 'graph',
          caption: '图像：割线 → 切线（瞬时）',
          illustrationId: 'instant-rate',
        },
      },
      {
        id: 'c3',
        title: '③ 导数记号直觉',
        bodyText: 'f′(x) 表示「在 x 处的瞬时快慢」。先会读、会用，再谈严谨极限语言。',
        bodyTex: "f'(x)",
        diagram: {
          kind: 'symbol',
          caption: '符号：f′(x) 表示瞬时快慢',
          illustrationId: 'deriv-symbol',
        },
      },
    ],
    examples: [
      {
        id: 'ex1',
        title: '例 1 · 三次函数增减',
        stem: '已知函数 f(x) = x³ − 3x + 1，求其单调递增区间。',
        stemTex: 'f(x)=x^3-3x+1',
        steps: [
          {
            id: 's1',
            caption: '先对 f(x) 求导。',
            formulaTex: "f'(x)=3x^2-3",
            animate: true,
          },
          {
            id: 's2',
            caption: '令导数等于零，求临界点。',
            formulaTex: "3x^2-3=0 \\Rightarrow x=\\pm 1",
            animate: true,
          },
          {
            id: 's3',
            caption: '列表判断各区间导数符号。',
            formulaTex: "f'(x)>0 \\Leftrightarrow |x|>1",
            animate: true,
          },
          {
            id: 's4',
            caption: '写出单调递增区间。',
            formulaTex: '(-\\infty,-1]\\cup[1,+\\infty)',
            animate: true,
          },
        ],
        answerTex: '(-\\infty,-1]\\cup[1,+\\infty)',
        answerText: '单调递增区间为 (−∞, −1] ∪ [1, +∞)',
      },
      {
        id: 'ex2',
        title: '例 2 · 含参数讨论（简）',
        stem: '设 g(x) = x + a/x（x > 0）。当 a = 4 时，求 g 的最小值。',
        stemTex: 'g(x)=x+\\frac{a}{x}\\ (x>0)',
        steps: [
          {
            id: 't1',
            caption: '对 g 求导。',
            formulaTex: "g'(x)=1-\\frac{a}{x^2}",
            animate: true,
          },
          {
            id: 't2',
            caption: '代入 a = 4，求驻点。',
            formulaTex: "1-\\frac{4}{x^2}=0 \\Rightarrow x=2\\ (x>0)",
            animate: true,
          },
          {
            id: 't3',
            caption: '由一阶导数变号确认极小，并求值。',
            formulaTex: 'g(2)=2+\\frac{4}{2}=4',
            animate: true,
          },
        ],
        answerTex: '4',
        answerText: '最小值为 4',
      },
    ],
    practices: [
      {
        id: 'p1',
        question: '求 h(x) = x² − 4x + 3 的单调递减区间。',
        questionTex: 'h(x)=x^2-4x+3',
        answerTex: '(-\\infty,2]',
        answerText: '递减区间 (−∞, 2]',
      },
      {
        id: 'p2',
        question: '若 k′(x) = (x − 1)(x − 3)，写出 k 的一个递增区间。',
        answerTex: '(-\\infty,1]\\cup[3,+\\infty)',
        answerText: '例如 (−∞, 1] 或 [3, +∞)',
      },
    ],
  },
  {
    id: 'lesson-tangent',
    title: '第2节 · 导数与切线',
    subtitle: '瞬时变化率 · 切线斜率',
    gradeHint: '高中 · 函数与导数 · 样例第 2 节',
    sourceLabel: '内置 · 自编演示',
    canvas: {
      chapterLabel: '第2节 · 导数与切线',
      goals: ['会求切线斜率', '会写切线方程', '分清切点与斜率'],
      tip: '「先求导再代入横坐标；纵坐标用原函数。」',
      blocks: [
        {
          id: 't1',
          tag: '情境导入',
          tagTone: 'orange',
          title: '曲线在一点「贴」得多紧？',
          body: '切线是局部最贴合曲线的直线；斜率就是该点导数。',
          illustrationId: 'secant-tangent',
          x: 48,
          y: 40,
          w: 360,
        },
        {
          id: 't2',
          tag: '核心公式',
          tagTone: 'blue',
          title: '切线方程',
          body: '过点 (x₀, f(x₀))、斜率为 f′(x₀)。',
          formulaPlain: 'y − f(x₀) = f′(x₀)(x − x₀)',
          x: 460,
          y: 48,
          w: 360,
        },
        {
          id: 't3',
          tag: '课堂提醒',
          tagTone: 'amber',
          title: '易错点',
          body: '切点纵坐标用原函数求，不要把导数当成纵坐标。',
          x: 200,
          y: 300,
          w: 480,
        },
      ],
    },
    concepts: [
      {
        id: 'c1',
        title: '① 几何意义',
        bodyText: '函数在某点的导数，等于该点处切线的斜率。',
        bodyTex: "f'(x_0)=k_{\\text{切线}}",
        diagram: {
          kind: 'graph',
          caption: '切线斜率 = 导数',
          illustrationId: 'instant-rate',
        },
      },
      {
        id: 'c2',
        title: '② 切线方程',
        bodyText: '过点 (x₀, f(x₀))、斜率为 f′(x₀) 的直线即为该点切线。',
        bodyTex: "y-f(x_0)=f'(x_0)(x-x_0)",
        diagram: {
          kind: 'symbol',
          caption: '点斜式写出切线',
          illustrationId: 'deriv-symbol',
        },
      },
      {
        id: 'c3',
        title: '③ 课堂提醒',
        bodyText: '先算导数再代入点的横坐标；切点纵坐标用原函数求。',
        diagram: {
          kind: 'life',
          caption: '步骤口诀示意',
          illustrationId: 'avg-rate',
        },
      },
    ],
    examples: [
      {
        id: 'ex1',
        title: '例 1 · 求切线方程',
        stem: '求曲线 y = x² 在点 x = 2 处的切线方程。',
        stemTex: 'y=x^2',
        steps: [
          {
            id: 's1',
            caption: '求导得到斜率函数。',
            formulaTex: "y'=2x",
            animate: true,
          },
          {
            id: 's2',
            caption: '代入 x = 2 得切线斜率与切点。',
            formulaTex: 'k=4,\\quad P(2,4)',
            animate: true,
          },
          {
            id: 's3',
            caption: '写出点斜式并整理。',
            formulaTex: 'y-4=4(x-2)\\Rightarrow y=4x-4',
            animate: true,
          },
        ],
        answerTex: 'y=4x-4',
        answerText: '切线方程 y = 4x − 4',
      },
    ],
    practices: [
      {
        id: 'p1',
        question: '曲线 y = x³ 在 x = 1 处的切线斜率是多少？',
        questionTex: 'y=x^3',
        answerTex: '3',
        answerText: '斜率为 3',
      },
      {
        id: 'p2',
        question: '求 y = √x 在 x = 4 处的切线方程。',
        questionTex: 'y=\\sqrt{x}',
        answerTex: 'y=\\frac{1}{4}x+1',
        answerText: 'y = (1/4)x + 1',
      },
    ],
  },
]

/** 演示用「导入包」假数据（非真实教材文件） */
export const DEMO_IMPORT_PACK = {
  name: '函数与导数 · 演示课包（自编）',
  lessons: ['第1节 · 导数的概念', '第2节 · 导数与切线'],
  note: '本包为团队自编演示内容，非出版社教材原文。',
}
