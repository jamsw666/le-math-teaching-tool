//! ExplanationProvider（Tauri 侧）：密钥本地存储 + OpenAI 兼容 chat/completions。
//!
//! - 可在**无** `desktop` feature 下编译与 `cargo test`
//! - 密钥永不写入日志 / ExplainFailure.message
//! - 契约：`specs/math-teaching-tool-explanation-provider.md`

use serde::{Deserialize, Serialize};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

const DEFAULT_TIMEOUT_MS: u64 = 20_000;
const MAX_TEXT_CHARS: usize = 2_000;
const SYSTEM_PROMPT: &str = "你是高中数学助教。用简明中文解释用户给出的概念或解题步骤，面向课堂老师备课/讲解，不要输出整份教案，不要索取更多课时全文。";

/// 应用私有目录下的 AI 配置路径（settings.json + api_key）。
#[derive(Debug, Clone)]
pub struct AiPaths {
    dir: PathBuf,
}

impl AiPaths {
    pub fn new(dir: impl Into<PathBuf>) -> Self {
        Self { dir: dir.into() }
    }

    pub fn dir(&self) -> &Path {
        &self.dir
    }

    pub fn settings_path(&self) -> PathBuf {
        self.dir.join("settings.json")
    }

    pub fn api_key_path(&self) -> PathBuf {
        self.dir.join("api_key")
    }

    pub fn ensure_dir(&self) -> std::io::Result<()> {
        fs::create_dir_all(&self.dir)
    }
}

/// 非密钥设置（可回前端）。
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AiSettings {
    pub enabled: bool,
    /// 如 `openai-compatible` / `deepseek`
    pub provider: String,
    pub base_url: String,
    pub model: String,
    #[serde(default = "default_timeout_ms")]
    pub timeout_ms: u64,
}

fn default_timeout_ms() -> u64 {
    DEFAULT_TIMEOUT_MS
}

impl Default for AiSettings {
    fn default() -> Self {
        Self {
            enabled: false,
            provider: "openai-compatible".into(),
            base_url: "https://api.deepseek.com".into(),
            model: "deepseek-chat".into(),
            timeout_ms: DEFAULT_TIMEOUT_MS,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ExplainScope {
    Snippet,
    Step,
    Concept,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ExplainContext {
    pub lesson_title: Option<String>,
    pub item_title: Option<String>,
    pub step_index: Option<i64>,
    #[serde(default)]
    pub include_prev_step: bool,
    pub prev_step_text: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExplainRequest {
    pub text: String,
    pub scope: ExplainScope,
    #[serde(default)]
    pub context: Option<ExplainContext>,
    pub timeout_ms: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ExplainSuccess {
    pub ok: bool,
    pub text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub model: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub latency_ms: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ExplainFailure {
    pub ok: bool,
    pub code: String,
    pub message: String,
}

/// 成功 / 失败统一 JSON（ok 判别）。
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(untagged)]
pub enum ExplainResult {
    Ok(ExplainSuccess),
    Err(ExplainFailure),
}

impl ExplainResult {
    pub fn success(text: String, model: Option<String>, latency_ms: Option<u64>) -> Self {
        Self::Ok(ExplainSuccess {
            ok: true,
            text,
            model,
            latency_ms,
        })
    }

    pub fn failure(code: &str, message: &str) -> Self {
        Self::Err(ExplainFailure {
            ok: false,
            code: code.into(),
            message: message.into(),
        })
    }

    pub fn is_ok(&self) -> bool {
        matches!(self, Self::Ok(_))
    }
}

// ——— 存储 ———

pub fn get_settings(paths: &AiPaths) -> AiSettings {
    let path = paths.settings_path();
    match fs::read_to_string(&path) {
        Ok(s) => serde_json::from_str(&s).unwrap_or_default(),
        Err(_) => AiSettings::default(),
    }
}

pub fn save_settings(paths: &AiPaths, settings: &AiSettings) -> Result<(), String> {
    paths.ensure_dir().map_err(|e| e.to_string())?;
    let path = paths.settings_path();
    let json = serde_json::to_string_pretty(settings).map_err(|e| e.to_string())?;
    atomic_write(&path, json.as_bytes()).map_err(|e| e.to_string())?;
    tighten_perms(&path);
    Ok(())
}

pub fn set_api_key(paths: &AiPaths, api_key: &str) -> Result<(), String> {
    let key = api_key.trim();
    if key.is_empty() {
        return Err("apiKey 不能为空".into());
    }
    paths.ensure_dir().map_err(|e| e.to_string())?;
    let path = paths.api_key_path();
    atomic_write(&path, key.as_bytes()).map_err(|e| e.to_string())?;
    tighten_perms(&path);
    Ok(())
}

pub fn clear_api_key(paths: &AiPaths) -> Result<(), String> {
    let path = paths.api_key_path();
    if path.exists() {
        fs::remove_file(&path).map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn has_api_key(paths: &AiPaths) -> bool {
    match read_api_key(paths) {
        Some(k) => !k.is_empty(),
        None => false,
    }
}

fn read_api_key(paths: &AiPaths) -> Option<String> {
    let path = paths.api_key_path();
    let mut f = fs::File::open(&path).ok()?;
    let mut buf = String::new();
    f.read_to_string(&mut buf).ok()?;
    let k = buf.trim().to_string();
    if k.is_empty() {
        None
    } else {
        Some(k)
    }
}

fn atomic_write(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    let tmp = path.with_extension("tmp");
    {
        let mut f = fs::File::create(&tmp)?;
        f.write_all(bytes)?;
        f.sync_all()?;
    }
    fs::rename(&tmp, path)?;
    Ok(())
}

fn tighten_perms(path: &Path) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o600));
    }
    let _ = path;
}

// ——— 请求组装（I10） ———

/// 截断 text；返回 (text, truncated)。
pub fn truncate_text(text: &str) -> (String, bool) {
    let count = text.chars().count();
    if count <= MAX_TEXT_CHARS {
        return (text.to_string(), false);
    }
    let truncated: String = text.chars().take(MAX_TEXT_CHARS).collect();
    (truncated, true)
}

/// 组装 user content：仅 text + 短 context；禁止整课。
pub fn build_user_content(req: &ExplainRequest) -> (String, bool) {
    let (text, truncated) = truncate_text(&req.text);
    let mut parts = vec![text];

    if let Some(ctx) = &req.context {
        let mut tags: Vec<String> = Vec::new();
        if let Some(t) = ctx.lesson_title.as_ref().filter(|s| !s.is_empty()) {
            tags.push(format!("课时：{t}"));
        }
        if let Some(t) = ctx.item_title.as_ref().filter(|s| !s.is_empty()) {
            tags.push(format!("条目：{t}"));
        }
        if let Some(i) = ctx.step_index {
            tags.push(format!("步骤：{i}"));
        }
        if !tags.is_empty() {
            parts.push(tags.join("｜"));
        }
        if ctx.include_prev_step {
            if let Some(prev) = ctx.prev_step_text.as_ref().filter(|s| !s.is_empty()) {
                let (prev_t, _) = truncate_text(prev);
                parts.push(format!("上一句：{prev_t}"));
            }
        }
    }

    (parts.join("\n"), truncated)
}

/// 规范化 baseUrl，得到 chat/completions URL（避免重复 /v1）。
pub fn chat_completions_url(base_url: &str) -> String {
    let base = base_url.trim().trim_end_matches('/').to_string();
    if base.ends_with("/v1") {
        format!("{base}/chat/completions")
    } else {
        format!("{base}/v1/chat/completions")
    }
}

fn human_message(code: &str) -> &'static str {
    match code {
        "disabled" => "AI 释义已关闭，可在设置中开启",
        "no_api_key" => "尚未配置密钥，请先在设置中填写",
        "offline" => "当前无法联网，释义暂不可用",
        "timeout" => "释义超时，请稍后重试",
        "unauthorized" => "密钥无效或无权限，请检查设置",
        "rate_limited" => "请求过于频繁，请稍后重试",
        "quota" => "服务余额不足或配额用尽",
        "bad_request" => "请求无效，请稍后重试",
        "upstream" => "释义服务暂时不可用，请稍后重试",
        "cancelled" => "已取消",
        _ => "释义失败，请稍后重试",
    }
}

/// 粗判余额/配额文案。
pub fn looks_like_quota(body: &str) -> bool {
    let lower = body.to_lowercase();
    lower.contains("insufficient")
        || lower.contains("quota")
        || lower.contains("balance")
        || lower.contains("billing")
        || body.contains("余额")
        || body.contains("欠费")
        || body.contains("配额")
}

pub fn map_http_status(status: u16, body: &str) -> ExplainResult {
    let code = match status {
        401 | 403 => "unauthorized",
        429 => "rate_limited",
        400 if looks_like_quota(body) => "quota",
        400 => "bad_request",
        402 => "quota",
        s if (500..600).contains(&s) => "upstream",
        _ if looks_like_quota(body) => "quota",
        _ => "upstream",
    };
    ExplainResult::failure(code, human_message(code))
}

/// 可注入的 HTTP POST（测试用 mock）。
pub type HttpPoster = Box<dyn Fn(&str, &str, &str, u64) -> Result<(u16, String), HttpCallError>>;

#[derive(Debug)]
pub enum HttpCallError {
    Timeout,
    Offline(String),
}

fn default_http_post(
    url: &str,
    api_key: &str,
    body_json: &str,
    timeout_ms: u64,
) -> Result<(u16, String), HttpCallError> {
    let agent = ureq::AgentBuilder::new()
        .timeout(Duration::from_millis(timeout_ms.max(1)))
        .build();
    let auth = format!("Bearer {api_key}");
    let resp = agent
        .post(url)
        .set("Authorization", &auth)
        .set("Content-Type", "application/json")
        .send_string(body_json);
    match resp {
        Ok(r) => {
            let status = r.status();
            let text = r.into_string().unwrap_or_default();
            Ok((status, text))
        }
        Err(ureq::Error::Status(code, r)) => {
            let text = r.into_string().unwrap_or_default();
            Ok((code, text))
        }
        Err(ureq::Error::Transport(t)) => {
            let msg = t.to_string();
            // 不把可能含 URL query 的细节原样塞进用户 message；仅分类
            if msg.to_lowercase().contains("timed out")
                || msg.to_lowercase().contains("timeout")
            {
                Err(HttpCallError::Timeout)
            } else {
                Err(HttpCallError::Offline(msg))
            }
        }
    }
}

/// 核心 explain（可注入 HTTP）。
pub fn explain_with(
    paths: &AiPaths,
    req: &ExplainRequest,
    poster: &HttpPoster,
) -> ExplainResult {
    let settings = get_settings(paths);
    if !settings.enabled {
        return ExplainResult::failure("disabled", human_message("disabled"));
    }
    let Some(api_key) = read_api_key(paths) else {
        return ExplainResult::failure("no_api_key", human_message("no_api_key"));
    };

    let timeout_ms = req
        .timeout_ms
        .unwrap_or(settings.timeout_ms)
        .max(1);
    let (user_content, _truncated) = build_user_content(req);
    // 防御：user content 不得意外拼进整课结构关键词检测留给单测；此处只发片段

    let url = chat_completions_url(&settings.base_url);
    let body = serde_json::json!({
        "model": settings.model,
        "messages": [
            { "role": "system", "content": SYSTEM_PROMPT },
            { "role": "user", "content": user_content },
        ],
        "temperature": 0.3,
    });
    let body_str = body.to_string();

    let started = Instant::now();
    let http_result = poster(&url, &api_key, &body_str, timeout_ms);
    // api_key 离开本作用域后不再使用；错误路径禁止回显
    drop(api_key);

    match http_result {
        Err(HttpCallError::Timeout) => {
            ExplainResult::failure("timeout", human_message("timeout"))
        }
        Err(HttpCallError::Offline(_)) => {
            ExplainResult::failure("offline", human_message("offline"))
        }
        Ok((status, resp_body)) => {
            if !(200..300).contains(&status) {
                return map_http_status(status, &resp_body);
            }
            match extract_assistant_content(&resp_body) {
                Some(text) => {
                    let latency = started.elapsed().as_millis() as u64;
                    ExplainResult::success(text, Some(settings.model.clone()), Some(latency))
                }
                None => ExplainResult::failure("upstream", human_message("upstream")),
            }
        }
    }
}

pub fn explain(paths: &AiPaths, req: &ExplainRequest) -> ExplainResult {
    let poster: HttpPoster = Box::new(|url, key, body, timeout| {
        default_http_post(url, key, body, timeout)
    });
    explain_with(paths, req, &poster)
}

fn extract_assistant_content(resp_body: &str) -> Option<String> {
    let v: serde_json::Value = serde_json::from_str(resp_body).ok()?;
    let content = v
        .pointer("/choices/0/message/content")?
        .as_str()?
        .trim();
    if content.is_empty() {
        None
    } else {
        Some(content.to_string())
    }
}

// ——— 对外薄封装（与 command 同名，供 main / 单测） ———

pub fn ai_get_settings(paths: &AiPaths) -> AiSettings {
    get_settings(paths)
}

pub fn ai_save_settings(paths: &AiPaths, settings: AiSettings) -> Result<(), String> {
    save_settings(paths, &settings)
}

pub fn ai_set_api_key(paths: &AiPaths, api_key: String) -> Result<(), String> {
    set_api_key(paths, &api_key)
}

pub fn ai_clear_api_key(paths: &AiPaths) -> Result<(), String> {
    clear_api_key(paths)
}

pub fn ai_has_api_key(paths: &AiPaths) -> bool {
    has_api_key(paths)
}

pub fn ai_explain(paths: &AiPaths, req: ExplainRequest) -> ExplainResult {
    explain(paths, &req)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::sync::{Arc, Mutex};
    use std::thread;

    fn tmp_paths() -> (tempfile::TempDir, AiPaths) {
        let dir = tempfile::tempdir().unwrap();
        let paths = AiPaths::new(dir.path().join("ai"));
        paths.ensure_dir().unwrap();
        (dir, paths)
    }

    #[test]
    fn disabled_returns_disabled() {
        let (_d, paths) = tmp_paths();
        let mut s = AiSettings::default();
        s.enabled = false;
        save_settings(&paths, &s).unwrap();
        set_api_key(&paths, "sk-test").unwrap();
        let r = explain(
            &paths,
            &ExplainRequest {
                text: "导数".into(),
                scope: ExplainScope::Concept,
                context: None,
                timeout_ms: Some(1000),
            },
        );
        match r {
            ExplainResult::Err(e) => {
                assert_eq!(e.code, "disabled");
                assert!(!e.message.to_lowercase().contains("sk-"));
            }
            _ => panic!("expected failure"),
        }
    }

    #[test]
    fn no_key_returns_no_api_key() {
        let (_d, paths) = tmp_paths();
        let mut s = AiSettings::default();
        s.enabled = true;
        save_settings(&paths, &s).unwrap();
        let r = explain(
            &paths,
            &ExplainRequest {
                text: "切线".into(),
                scope: ExplainScope::Snippet,
                context: None,
                timeout_ms: Some(1000),
            },
        );
        match r {
            ExplainResult::Err(e) => assert_eq!(e.code, "no_api_key"),
            _ => panic!("expected failure"),
        }
    }

    #[test]
    fn user_content_no_full_lesson() {
        let req = ExplainRequest {
            text: "求 f'(x)".into(),
            scope: ExplainScope::Step,
            context: Some(ExplainContext {
                lesson_title: Some("导数与切线".into()),
                item_title: Some("例1".into()),
                step_index: Some(2),
                include_prev_step: false,
                prev_step_text: Some("上一句不该出现".into()),
            }),
            timeout_ms: None,
        };
        let (content, truncated) = build_user_content(&req);
        assert!(!truncated);
        assert!(content.contains("求 f'(x)"));
        assert!(content.contains("课时：导数与切线"));
        assert!(content.contains("条目：例1"));
        assert!(content.contains("步骤：2"));
        assert!(!content.contains("上一句不该出现"));
        assert!(!content.contains("examples"));
        assert!(!content.contains("\"steps\""));
        // 整课禁止：不应出现大块 JSON 结构
        assert!(!content.contains("revealIndex"));
    }

    #[test]
    fn include_prev_step_opt_in() {
        let req = ExplainRequest {
            text: "当前句".into(),
            scope: ExplainScope::Step,
            context: Some(ExplainContext {
                lesson_title: None,
                item_title: None,
                step_index: Some(1),
                include_prev_step: true,
                prev_step_text: Some("上一句内容".into()),
            }),
            timeout_ms: None,
        };
        let (content, _) = build_user_content(&req);
        assert!(content.contains("上一句：上一句内容"));
    }

    #[test]
    fn truncate_over_2000() {
        let long: String = "字".repeat(2500);
        let (t, truncated) = truncate_text(&long);
        assert!(truncated);
        assert_eq!(t.chars().count(), 2000);
    }

    #[test]
    fn chat_url_normalization() {
        assert_eq!(
            chat_completions_url("https://api.deepseek.com"),
            "https://api.deepseek.com/v1/chat/completions"
        );
        assert_eq!(
            chat_completions_url("https://api.deepseek.com/"),
            "https://api.deepseek.com/v1/chat/completions"
        );
        assert_eq!(
            chat_completions_url("https://api.deepseek.com/v1"),
            "https://api.deepseek.com/v1/chat/completions"
        );
        assert_eq!(
            chat_completions_url("https://api.deepseek.com/v1/"),
            "https://api.deepseek.com/v1/chat/completions"
        );
    }

    #[test]
    fn error_code_mapping() {
        assert_eq!(
            match map_http_status(401, "") {
                ExplainResult::Err(e) => e.code,
                _ => panic!(),
            },
            "unauthorized"
        );
        assert_eq!(
            match map_http_status(429, "") {
                ExplainResult::Err(e) => e.code,
                _ => panic!(),
            },
            "rate_limited"
        );
        assert_eq!(
            match map_http_status(500, "") {
                ExplainResult::Err(e) => e.code,
                _ => panic!(),
            },
            "upstream"
        );
        assert_eq!(
            match map_http_status(400, "insufficient_quota") {
                ExplainResult::Err(e) => e.code,
                _ => panic!(),
            },
            "quota"
        );
        assert_eq!(
            match map_http_status(400, "bad") {
                ExplainResult::Err(e) => e.code,
                _ => panic!(),
            },
            "bad_request"
        );
    }

    #[test]
    fn failure_message_never_contains_key() {
        let (_d, paths) = tmp_paths();
        let mut s = AiSettings::default();
        s.enabled = true;
        s.base_url = "http://127.0.0.1:9".into(); // closed port → offline
        save_settings(&paths, &s).unwrap();
        let secret = "sk-super-secret-key-xyz";
        set_api_key(&paths, secret).unwrap();
        let r = explain(
            &paths,
            &ExplainRequest {
                text: "x".into(),
                scope: ExplainScope::Snippet,
                context: None,
                timeout_ms: Some(500),
            },
        );
        let msg = match r {
            ExplainResult::Err(e) => e.message,
            ExplainResult::Ok(_) => panic!("expected err"),
        };
        assert!(!msg.contains(secret));
        assert!(!msg.contains("sk-super"));
    }

    #[test]
    fn has_api_key_bool_only() {
        let (_d, paths) = tmp_paths();
        assert!(!ai_has_api_key(&paths));
        ai_set_api_key(&paths, "sk-abc".into()).unwrap();
        assert!(ai_has_api_key(&paths));
        ai_clear_api_key(&paths).unwrap();
        assert!(!ai_has_api_key(&paths));
    }

    #[test]
    fn settings_roundtrip() {
        let (_d, paths) = tmp_paths();
        let s = AiSettings {
            enabled: true,
            provider: "openai-compatible".into(),
            base_url: "https://example.com".into(),
            model: "m1".into(),
            timeout_ms: 15_000,
        };
        ai_save_settings(&paths, s.clone()).unwrap();
        assert_eq!(ai_get_settings(&paths), s);
    }

    #[test]
    fn mock_http_success_path() {
        let (_d, paths) = tmp_paths();
        let mut s = AiSettings::default();
        s.enabled = true;
        s.model = "mock-model".into();
        save_settings(&paths, &s).unwrap();
        set_api_key(&paths, "sk-mock").unwrap();

        let captured = Arc::new(Mutex::new(String::new()));
        let captured2 = captured.clone();
        let poster: HttpPoster = Box::new(move |_url, key, body, _timeout| {
            assert_eq!(key, "sk-mock");
            *captured2.lock().unwrap() = body.to_string();
            let resp = serde_json::json!({
                "choices": [{ "message": { "content": "  这是释义  " } }]
            });
            Ok((200, resp.to_string()))
        });

        let r = explain_with(
            &paths,
            &ExplainRequest {
                text: "斜率".into(),
                scope: ExplainScope::Concept,
                context: Some(ExplainContext {
                    lesson_title: Some("导数".into()),
                    item_title: None,
                    step_index: None,
                    include_prev_step: false,
                    prev_step_text: None,
                }),
                timeout_ms: Some(2000),
            },
            &poster,
        );
        match r {
            ExplainResult::Ok(ok) => {
                assert_eq!(ok.text, "这是释义");
                assert_eq!(ok.model.as_deref(), Some("mock-model"));
            }
            ExplainResult::Err(e) => panic!("unexpected err {}", e.code),
        }
        let body = captured.lock().unwrap().clone();
        assert!(body.contains("斜率"));
        assert!(body.contains("课时：导数"));
        assert!(!body.contains("sk-mock"));
        assert!(!body.contains("\"examples\""));
    }

    #[test]
    fn local_tcp_mock_success() {
        // 起一个极简 HTTP 服务，走真实 ureq
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        thread::spawn(move || {
            if let Ok((mut stream, _)) = listener.accept() {
                let mut buf = [0u8; 4096];
                let _ = stream.read(&mut buf);
                let body = r#"{"choices":[{"message":{"content":"本地释义成功"}}]}"#;
                let resp = format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                    body.len(),
                    body
                );
                let _ = stream.write_all(resp.as_bytes());
            }
        });

        let (_d, paths) = tmp_paths();
        let mut s = AiSettings::default();
        s.enabled = true;
        s.base_url = format!("http://{addr}");
        s.model = "local".into();
        save_settings(&paths, &s).unwrap();
        set_api_key(&paths, "sk-local").unwrap();

        let r = ai_explain(
            &paths,
            ExplainRequest {
                text: "导数定义".into(),
                scope: ExplainScope::Concept,
                context: None,
                timeout_ms: Some(3000),
            },
        );
        match r {
            ExplainResult::Ok(ok) => assert_eq!(ok.text, "本地释义成功"),
            ExplainResult::Err(e) => panic!("err {}: {}", e.code, e.message),
        }
    }
}
