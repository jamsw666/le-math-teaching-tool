import { useCallback, useMemo, useState } from 'react'
import { SAMPLE_LESSONS } from './data/lessons'
import type { Lesson, ViewMode, WorkbenchTab } from './types'
import { LessonList } from './components/LessonList'
import { Workbench } from './components/Workbench'
import { ProjectionMode } from './components/ProjectionMode'
import { ImportFlow, LessonCanvasView } from './components/ImportCanvas'
import { ScratchPad } from './components/ScratchPad'
import { Settings } from './components/Settings'
import { WelcomeSplash } from './components/WelcomeSplash'
import './styles/animations.css'

function App() {
  const [view, setView] = useState<ViewMode>('list')
  const [lessonId, setLessonId] = useState<string | null>(null)
  const [tab, setTab] = useState<WorkbenchTab>('concepts')
  const [conceptReveal, setConceptReveal] = useState(0)
  const [exampleIndex, setExampleIndex] = useState(0)
  const [stepReveal, setStepReveal] = useState(0)
  const [showAnswer, setShowAnswer] = useState(false)
  const [skipAnimation, setSkipAnimation] = useState(false)
  const [scratchOpen, setScratchOpen] = useState(false)
  const [showWelcome, setShowWelcome] = useState(true)

  const lesson: Lesson | undefined = useMemo(
    () => SAMPLE_LESSONS.find((l) => l.id === lessonId),
    [lessonId],
  )

  const resetLessonState = () => {
    setConceptReveal(0)
    setExampleIndex(0)
    setStepReveal(0)
    setShowAnswer(false)
    setSkipAnimation(false)
  }

  const openLesson = (
    id: string,
    next: ViewMode = 'workbench',
    nextTab: WorkbenchTab = 'concepts',
  ) => {
    setLessonId(id)
    resetLessonState()
    setTab(nextTab)
    setView(next)
  }

  const currentExample = lesson?.examples[exampleIndex]

  const onPrevStep = useCallback(() => {
    setStepReveal((n) => Math.max(0, n - 1))
    setShowAnswer(false)
    setSkipAnimation(false)
  }, [])

  const onNextStep = useCallback(() => {
    if (!currentExample) return
    setSkipAnimation(false)
    setStepReveal((n) => Math.min(currentExample.steps.length, n + 1))
  }, [currentExample])

  const onPrevExample = () => {
    setExampleIndex((i) => Math.max(0, i - 1))
    setStepReveal(0)
    setShowAnswer(false)
    setSkipAnimation(false)
  }

  const onNextExample = () => {
    if (!lesson) return
    setExampleIndex((i) => Math.min(lesson.examples.length - 1, i + 1))
    setStepReveal(0)
    setShowAnswer(false)
    setSkipAnimation(false)
  }

  const scratch = (
    <ScratchPad open={scratchOpen} onClose={() => setScratchOpen(false)} />
  )

  const welcome = showWelcome ? (
    <WelcomeSplash onEnter={() => setShowWelcome(false)} />
  ) : null

  if (view === 'settings') {
    return (
      <>
        <Settings onBack={() => setView('list')} />
        {welcome}
      </>
    )
  }

  if (view === 'list') {
    return (
      <>
        <LessonList
          lessons={SAMPLE_LESSONS}
          onOpen={(id) => openLesson(id, 'workbench', 'concepts')}
          onOpenCanvas={(id) => openLesson(id, 'workbench', 'canvas')}
          onProjection={(id) => openLesson(id, 'projection', 'examples')}
          onImport={() => setView('import')}
          onSettings={() => setView('settings')}
        />
        {scratch}
        {welcome}
      </>
    )
  }

  if (view === 'import') {
    return (
      <>
        <ImportFlow
          lessons={SAMPLE_LESSONS}
          onCancel={() => setView('list')}
          onOpenLesson={(id) => openLesson(id, 'workbench', 'canvas')}
        />
        {scratch}
      </>
    )
  }

  if (!lesson) {
    return (
      <div className="lesson-list">
        <p>未找到课时</p>
        <button type="button" className="btn" onClick={() => setView('list')}>
          返回
        </button>
      </div>
    )
  }

  const shared = {
    lesson,
    tab,
    conceptReveal,
    exampleIndex,
    stepReveal,
    showAnswer,
    skipAnimation,
    scratchOpen,
    onOpenScratch: () => setScratchOpen(true),
    onCloseScratch: () => setScratchOpen(false),
    onTab: setTab,
    onConceptRevealMore: () => {
      setSkipAnimation(false)
      setConceptReveal((n) => Math.min(lesson.concepts.length, n + 1))
    },
    onConceptRevealLess: () => {
      setSkipAnimation(false)
      setConceptReveal((n) => Math.max(0, n - 1))
    },
    onConceptReset: () => {
      setConceptReveal(0)
      setSkipAnimation(false)
    },
    onPrevExample,
    onNextExample,
    onPrevStep,
    onNextStep,
    onToggleAnswer: () => setShowAnswer((v) => !v),
    onSkipAnimation: () => {
      // I8：只跳过「当前步」动画过渡，落到当前已揭示步的终态，不跳到整题最后一步
      setSkipAnimation(true)
    },
  }

  if (view === 'projection') {
    return (
      <>
        <ProjectionMode {...shared} onExit={() => setView('workbench')} />
        {scratch}
      </>
    )
  }

  if (tab === 'canvas' && lesson.canvas) {
    return (
      <>
        <LessonCanvasView
          lesson={lesson}
          canvas={lesson.canvas}
          onBack={() => setView('list')}
          onWorkbench={() => setTab('concepts')}
          onProjection={() => {
            setTab('examples')
            setView('projection')
          }}
        />
        {scratch}
      </>
    )
  }

  return (
    <>
      <Workbench
        {...shared}
        onBack={() => setView('list')}
        onOpenCanvas={() => setTab('canvas')}
        onProjection={() => {
          setTab('examples')
          setView('projection')
        }}
        onSettings={() => setView('settings')}
      />
      {scratch}
    </>
  )
}

export default App
