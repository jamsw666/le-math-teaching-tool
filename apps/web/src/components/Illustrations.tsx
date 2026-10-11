/** 内置示意 SVG：概念/画布共用，非教材插图 */

interface Props {
  id: string
  className?: string
}

export function Illustration({ id, className }: Props) {
  switch (id) {
    case 'train-path':
      return (
        <svg className={className} viewBox="0 0 300 90" width="100%" height="90" aria-hidden>
          <rect x="0" y="0" width="300" height="90" rx="8" fill="#0f172a" />
          <path
            d="M20 70 Q 80 70 110 40 T 200 25 T 280 50"
            fill="none"
            stroke="#38bdf8"
            strokeWidth="3"
          />
          <circle cx="200" cy="25" r="5" fill="#fbbf24" />
          <text x="208" y="22" fill="#fbbf24" fontSize="11">
            这一瞬间？
          </text>
          <text x="16" y="16" fill="#94a3b8" fontSize="10">
            路程-时间示意
          </text>
        </svg>
      )
    case 'secant-tangent':
      return (
        <svg className={className} viewBox="0 0 260 70" width="100%" height="70" aria-hidden>
          <rect width="260" height="70" rx="8" fill="#eff6ff" />
          <line x1="30" y1="55" x2="230" y2="55" stroke="#94a3b8" />
          <line x1="30" y1="55" x2="30" y2="15" stroke="#94a3b8" />
          <path d="M40 50 Q 100 50 140 28 T 220 18" fill="none" stroke="#2563eb" strokeWidth="2" />
          <line
            x1="140"
            y1="50"
            x2="200"
            y2="22"
            stroke="#f97316"
            strokeWidth="2"
            strokeDasharray="4 3"
          />
          <text x="150" y="16" fontSize="10" fill="#c2410c">
            割线 → 切线
          </text>
        </svg>
      )
    case 'avg-rate':
      return (
        <svg className={className} viewBox="0 0 560 220" width="100%" preserveAspectRatio="xMidYMid meet" aria-hidden>
          <rect width="560" height="220" rx="8" fill="#020617" />
          <line x1="50" y1="180" x2="520" y2="180" stroke="#475569" strokeWidth="2" />
          <line x1="50" y1="180" x2="50" y2="30" stroke="#475569" strokeWidth="2" />
          <path d="M70 160 Q 180 150 260 100 T 480 50" fill="none" stroke="#38bdf8" strokeWidth="3" />
          <line x1="140" y1="140" x2="340" y2="70" stroke="#fb923c" strokeWidth="3" strokeDasharray="6 4" />
          <circle cx="140" cy="140" r="5" fill="#fb923c" />
          <circle cx="340" cy="70" r="5" fill="#fb923c" />
          <text x="200" y="95" fill="#fb923c" fontSize="14">
            平均变化率（割线）
          </text>
          <text x="60" y="40" fill="#94a3b8" fontSize="12">
            一段上的「平均快慢」
          </text>
        </svg>
      )
    case 'instant-rate':
      return (
        <svg className={className} viewBox="0 0 560 280" width="100%" preserveAspectRatio="xMidYMid meet" aria-hidden>
          <rect width="560" height="280" rx="8" fill="#020617" />
          <line x1="50" y1="240" x2="520" y2="240" stroke="#475569" strokeWidth="2" />
          <line x1="50" y1="240" x2="50" y2="30" stroke="#475569" strokeWidth="2" />
          <text x="510" y="255" fill="#94a3b8" fontSize="12">
            x
          </text>
          <text x="30" y="40" fill="#94a3b8" fontSize="12">
            y
          </text>
          <path d="M70 230 Q 180 220 260 140 T 480 40" fill="none" stroke="#38bdf8" strokeWidth="3" />
          <line
            x1="180"
            y1="195"
            x2="360"
            y2="95"
            stroke="#fb923c"
            strokeWidth="2"
            strokeDasharray="6 4"
          />
          <text x="300" y="85" fill="#fb923c" fontSize="13">
            割线 · 平均
          </text>
          <line x1="200" y1="190" x2="420" y2="80" stroke="#fbbf24" strokeWidth="3" />
          <circle cx="280" cy="145" r="6" fill="#fbbf24" />
          <text x="290" y="160" fill="#fbbf24" fontSize="13">
            切点 · 瞬时（导数）
          </text>
          <rect x="380" y="180" width="150" height="70" rx="8" fill="#111827" stroke="#334155" />
          <text x="395" y="205" fill="#94a3b8" fontSize="11">
            间隔越来越短
          </text>
          <text x="395" y="225" fill="#e2e8f0" fontSize="12">
            割线 → 切线
          </text>
          <text x="395" y="242" fill="#67e8f9" fontSize="11">
            关键步骤可跳过
          </text>
        </svg>
      )
    case 'deriv-symbol':
      return (
        <svg className={className} viewBox="0 0 560 200" width="100%" preserveAspectRatio="xMidYMid meet" aria-hidden>
          <rect width="560" height="200" rx="8" fill="#0f172a" />
          <text x="280" y="90" textAnchor="middle" fill="#fde68a" fontSize="48" fontWeight="700">
            f′(x)
          </text>
          <text x="280" y="140" textAnchor="middle" fill="#94a3b8" fontSize="16">
            在 x 处的瞬时快慢
          </text>
          <text x="280" y="170" textAnchor="middle" fill="#67e8f9" fontSize="13">
            点模板插入 · 投影大字可读
          </text>
        </svg>
      )
    default:
      return (
        <div className={`illus-fallback ${className ?? ''}`}>
          <span>示意占位</span>
        </div>
      )
  }
}
