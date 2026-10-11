/**
 * 演示 list / get / 投影视图（I1：按 reveal_index 过滤）
 * 用法：先 seed，再 node storage/query-demo.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import initSqlJs from "sql.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DB_PATH = path.join(__dirname, "data/app.db");

async function openDb() {
  if (!fs.existsSync(DB_PATH)) {
    console.error("库不存在，请先运行: npm run seed");
    process.exit(1);
  }
  const SQL = await initSqlJs();
  return new SQL.Database(fs.readFileSync(DB_PATH));
}

function rows(db, sql, params = []) {
  const stmt = db.prepare(sql);
  stmt.bind(params);
  const out = [];
  while (stmt.step()) out.push(stmt.getAsObject());
  stmt.free();
  return out;
}

async function main() {
  const db = await openDb();

  console.log("\n=== list_lessons ===");
  const lessons = rows(
    db,
    `SELECT id, title, chapter, reveal_index, source FROM lessons ORDER BY created_at`
  );
  console.log(JSON.stringify(lessons, null, 2));

  if (lessons.length === 0) {
    console.log("无课时");
    db.close();
    return;
  }

  const id = lessons[0].id;

  // 自测：把 reveal_index 设为 2，投影应只见 ordinal<=2
  db.run(`UPDATE lessons SET reveal_index = 2, updated_at = ? WHERE id = ?`, [
    new Date().toISOString(),
    id,
  ]);

  console.log(`\n=== get_lesson (${id}) 全量步骤 ===`);
  const allSteps = rows(
    db,
    `SELECT id, ordinal, kind FROM lesson_steps WHERE lesson_id = ? ORDER BY ordinal`,
    [id]
  );
  console.log(JSON.stringify(allSteps, null, 2));

  console.log(`\n=== get_projection_view (reveal_index=2，I1 过滤) ===`);
  const meta = rows(
    db,
    `SELECT id, title, chapter, reveal_index FROM lessons WHERE id = ?`,
    [id]
  )[0];
  const revealed = rows(
    db,
    `SELECT id, ordinal, kind FROM lesson_steps
     WHERE lesson_id = ? AND ordinal <= ?
     ORDER BY ordinal`,
    [id, meta.reveal_index]
  );
  console.log(
    JSON.stringify({ meta, revealedSteps: revealed }, null, 2)
  );

  const hidden = allSteps.filter((s) => s.ordinal > meta.reveal_index);
  console.log(
    `\nI1 检查: 全量 ${allSteps.length} 步，已揭示 ${revealed.length} 步，未揭示 ${hidden.length} 步（不得出现在投影路径）`
  );

  // 写回（保留 reveal_index=2 便于联调）
  const data = db.export();
  fs.writeFileSync(DB_PATH, Buffer.from(data));
  db.close();
  console.log("\n自测完成。");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
