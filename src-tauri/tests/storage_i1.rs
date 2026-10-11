//! 灌样例 → list → set_reveal_index → projection 过滤未揭示步（I1）

use math_teaching_tool_storage::{
    export_lesson, get_lesson, get_projection_view, import_sample, list_lessons, set_reveal_index,
    LessonSampleFile, Store,
};

fn load_fixture() -> LessonSampleFile {
    let raw = include_str!("../fixtures/lesson-derivative-tangent.json");
    serde_json::from_str(raw).expect("fixture json")
}

#[test]
fn seed_list_reveal_projection_i1() {
    let store = Store::open_memory().expect("open memory db");
    let sample = load_fixture();
    let lesson_id = sample.id.clone();
    let total_steps = sample.steps.len();

    let imported = import_sample(&store, &sample).expect("import");
    assert_eq!(imported.meta.id, lesson_id);
    assert_eq!(imported.meta.reveal_index, -1);
    assert_eq!(imported.steps.len(), total_steps);

    let listed = list_lessons(&store).expect("list");
    assert!(listed.iter().any(|m| m.id == lesson_id));

    // 未揭示：投影视图 steps 为空
    let proj0 = get_projection_view(&store, &lesson_id)
        .expect("proj")
        .expect("exists");
    assert_eq!(proj0.meta.reveal_index, -1);
    assert!(proj0.revealed_steps.is_empty());

    set_reveal_index(&store, &lesson_id, 2).expect("reveal 2");

    let full = get_lesson(&store, &lesson_id)
        .expect("get")
        .expect("exists");
    assert_eq!(full.meta.reveal_index, 2);
    assert_eq!(full.steps.len(), total_steps); // 编辑态全量

    let proj = get_projection_view(&store, &lesson_id)
        .expect("proj")
        .expect("exists");
    assert_eq!(proj.meta.reveal_index, 2);
    assert_eq!(proj.revealed_steps.len(), 3); // ordinal 0,1,2
    assert!(proj.revealed_steps.iter().all(|s| s.ordinal <= 2));
    assert!(!proj.revealed_steps.iter().any(|s| s.ordinal > 2));

    let exported = export_lesson(&store, &lesson_id)
        .expect("export")
        .expect("exists");
    assert_eq!(exported.id, lesson_id);
    assert_eq!(exported.steps.len(), total_steps);
}
