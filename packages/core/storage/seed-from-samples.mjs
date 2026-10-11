/**
 * 从 content/samples/*.json 灌入开发态 SQLite（sql.js）
 * 用法：node storage/seed-from-samples.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import initSqlJs from "sql.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../../..");
const SAMPLES_DIR = path.join(ROOT, "content/samples");
const DATA_DIR = path.join(__dirname, "data");
const DB_PATH = path.join(DATA_DIR, "app.db");
const SCHEMA_PATH = path.join(__dirname, "schema.sql");

function nowIso() {
  return new Date().toISOString();
}

async function main() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  const schema = fs.readFileSync(SCHEMA_PATH, "utf8");
  db.run(schema);

  const files = fs
    .readdirSync(SAMPLES_DIR)
    .filter((f) => f.endsWith(".json"))
    .sort();

  if (files.length === 0) {
    console.error("未找到 samples JSON：", SAMPLES_DIR);
    process.exit(1);
  }

  const ts = nowIso();
  for (const file of files) {
    const sample = JSON.parse(
      fs.readFileSync(path.join(SAMPLES_DIR, file), "utf8")
    );
    db.run(
      `INSERT OR REPLACE INTO lessons
        (id, title, chapter, tags_json, reveal_index, source, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        sample.id,
        sample.title,
        sample.chapter,
        JSON.stringify(sample.tags ?? []),
        -1,
        "builtin",
        ts,
        ts,
      ]
    );
    db.run(`DELETE FROM lesson_steps WHERE lesson_id = ?`, [sample.id]);
    for (const step of sample.steps) {
      db.run(
        `INSERT INTO lesson_steps (id, lesson_id, ordinal, kind, content_json)
         VALUES (?, ?, ?, ?, ?)`,
        [
          step.id,
          sample.id,
          step.ordinal,
          step.kind,
          JSON.stringify(step.content),
        ]
      );
    }
    console.log(`✓ 已导入 ${file} → lesson ${sample.id} (${sample.steps.length} steps)`);
  }

  const data = db.export();
  fs.writeFileSync(DB_PATH, Buffer.from(data));
  db.close();
  console.log(`DB 写入: ${DB_PATH}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
