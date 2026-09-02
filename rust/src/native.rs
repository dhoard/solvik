//! Standard native/host function implementations.
//!
//! Port of internal/native/native.go.

use crate::gocompat::{go_format_float, go_parse_float, go_to_lower, go_to_upper, Pcg};
use crate::vm::{NativeRegistry, Value};
use base64::Engine;
use md5::Digest;
use std::cell::RefCell;
use std::rc::Rc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

thread_local! {
    static RANDOM_SOURCE: RefCell<Option<Pcg>> = const { RefCell::new(None) };
}

fn with_random<T>(f: impl FnOnce(&mut Pcg) -> T) -> T {
    RANDOM_SOURCE.with(|cell| {
        let mut src = cell.borrow_mut();
        if src.is_none() {
            // Matches Go: seeded from two global draws when uninitialized.
            let (a, b) = os_random_pair();
            *src = Some(Pcg::new(a, b));
        }
        f(src.as_mut().unwrap())
    })
}

fn os_random_pair() -> (u64, u64) {
    let mut buf = [0u8; 16];
    match getrandom::getrandom(&mut buf) {
        Ok(_) => {
            let a = u64::from_be_bytes(buf[0..8].try_into().unwrap());
            let b = u64::from_be_bytes(buf[8..16].try_into().unwrap());
            (a, b)
        }
        Err(_) => {
            let nanos = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_nanos() as u64)
                .unwrap_or(0);
            (nanos, nanos.rotate_left(32) ^ 0x9e3779b97f4a7c15)
        }
    }
}

pub fn register_all(registry: &mut NativeRegistry) {
    register_core(registry);
    register_string(registry);
    register_math(registry);
    register_env(registry);
    register_file(registry);
    register_process(registry);
    register_time(registry);
    register_random(registry);
    register_path(registry);
    register_base64(registry);
    register_hash(registry);
    register_secrets(registry);
    register_list(registry);
    register_stack(registry);
    register_map(registry);
    register_aliases(registry);
}

fn err(msg: &str) -> String {
    msg.to_string()
}

// ===== Core module =====

fn register_core(registry: &mut NativeRegistry) {
    registry.register("core.print", |args| {
        if args.len() != 1 {
            return Err(err("print expects 1 argument"));
        }
        print!("{}", args[0].display_string());
        use std::io::Write;
        let _ = std::io::stdout().flush();
        Ok(Value::Null)
    });

    registry.register("core.println", |args| {
        if args.len() != 1 {
            return Err(err("println expects 1 argument"));
        }
        println!("{}", args[0].display_string());
        Ok(Value::Null)
    });

    registry.register("core.string", |args| {
        if args.len() != 1 {
            return Err(err("string expects 1 argument"));
        }
        Ok(Value::str(args[0].display_string()))
    });

    registry.register("core.int", |args| {
        if args.len() != 1 {
            return Err(err("int expects 1 argument"));
        }
        match &args[0] {
            Value::Str(s) => {
                let v = parse_go_int10(s).ok_or_else(|| err(&format!("cannot convert '{}' to int", s)))?;
                Ok(Value::Int(v))
            }
            Value::Int(_) | Value::Byte(_) | Value::Char(_) | Value::Float(_) | Value::Bool(_) => {
                Ok(Value::Int(args[0].as_int()))
            }
            _ => Err(err(&format!("cannot convert {} to int", type_name(&args[0])))),
        }
    });

    registry.register("core.float", |args| {
        if args.len() != 1 {
            return Err(err("float expects 1 argument"));
        }
        match &args[0] {
            Value::Str(s) => {
                let v = go_parse_float(s).ok_or_else(|| err(&format!("cannot convert '{}' to float", s)))?;
                Ok(Value::Float(v))
            }
            Value::Int(_) | Value::Byte(_) | Value::Float(_) => Ok(Value::Float(args[0].as_double())),
            _ => Err(err(&format!("cannot convert {} to float", type_name(&args[0])))),
        }
    });

    registry.register("core.byte", |args| {
        if args.len() != 1 {
            return Err(err("byte expects 1 argument"));
        }
        let v = args[0].as_int();
        if !(0..=255).contains(&v) {
            return Err(err("byte conversion out of range"));
        }
        Ok(Value::Byte(v as u8))
    });

    registry.register("core.bool", |args| {
        if args.len() != 1 {
            return Err(err("bool expects 1 argument"));
        }
        Ok(Value::Bool(args[0].is_truthy()))
    });

    registry.register("core.typeOf", |args| {
        if args.len() != 1 {
            return Err(err("typeOf expects 1 argument"));
        }
        Ok(Value::str(type_name(&args[0])))
    });

    registry.register("core.regex", |args| {
        if args.len() != 1 {
            return Err(err("regex expects exactly 1 argument"));
        }
        let pattern = match &args[0] {
            Value::Str(s) => s.to_string(),
            _ => return Err(err(&format!("regex expects a string argument, got {}", type_name(&args[0])))),
        };
        match regex::Regex::new(&pattern) {
            Ok(re) => Ok(Value::Regex(std::rc::Rc::new(re))),
            Err(e) => Err(err(&format!("invalid regular expression \"{}\": {}", pattern, e))),
        }
    });

    registry.register("core.isType", |args| {
        if args.len() != 2 {
            return Err(err("isType expects 2 arguments"));
        }
        if !matches!(args[1], Value::Str(_)) {
            return Err(err("isType expects a string as second argument"));
        }
        let actual = type_name(&args[0]);
        let expected = args[1].display_string();
        Ok(Value::Bool(actual == expected))
    });
}

/// Parses a base-10 integer like Go's strconv.ParseInt(s, 10, 64).
fn parse_go_int10(s: &str) -> Option<i64> {
    if s.is_empty() {
        return None;
    }
    s.parse::<i64>().ok()
}

fn type_name(v: &Value) -> String {
    crate::vm::value_type_tag(v)
}

fn collection_len_handler(args: &[Value]) -> Result<Value, String> {
    if args.len() != 1 {
        return Err(err("len expects 1 argument"));
    }
    match &args[0] {
        Value::List(_) => Ok(Value::Int(args[0].list_len() as i64)),
        Value::Map(_) => Ok(Value::Int(args[0].map_len() as i64)),
        Value::Stack(_) => Ok(Value::Int(args[0].stack_len() as i64)),
        Value::Str(s) => Ok(Value::Int(s.chars().count() as i64)),
        _ => Err(err(&format!(
            "len expects a list, map, stack, or string, got {}",
            args[0].display_string()
        ))),
    }
}

// ===== String module =====

fn register_string(registry: &mut NativeRegistry) {
    registry.register("string.len", |args| {
        if args.len() != 1 {
            return Err(err("string.len expects 1 argument"));
        }
        Ok(Value::Int(args[0].display_string().chars().count() as i64))
    });

    registry.register("string.byteLength", |args| {
        if args.len() != 1 {
            return Err(err("string.byteLength expects 1 argument"));
        }
        Ok(Value::Int(args[0].display_string().len() as i64))
    });

    registry.register("string.charAt", |args| {
        if args.len() != 2 {
            return Err(err("string.charAt expects 2 arguments"));
        }
        let s = args[0].display_string();
        let idx = args[1].as_int();
        let runes: Vec<char> = s.chars().collect();
        if idx < 0 || idx as usize >= runes.len() {
            return Err(err(&format!(
                "string.charAt: index {} out of range (length {})",
                idx,
                runes.len()
            )));
        }
        Ok(Value::Char(runes[idx as usize] as u32))
    });

    registry.register("string.substring", |args| {
        if args.len() != 3 {
            return Err(err("string.substring expects 3 arguments"));
        }
        let s = args[0].display_string();
        let mut start = args[1].as_int();
        let mut end = args[2].as_int();
        let runes: Vec<char> = s.chars().collect();
        if start < 0 {
            start = 0;
        }
        if end > runes.len() as i64 {
            end = runes.len() as i64;
        }
        if start > end {
            start = end;
        }
        Ok(Value::str(runes[start as usize..end as usize].iter().collect::<String>()))
    });

    registry.register("string.contains", |args| {
        if args.len() != 2 {
            return Err(err("string.contains expects 2 arguments"));
        }
        let s = args[0].display_string();
        let substr = args[1].display_string();
        Ok(Value::Bool(s.contains(&substr)))
    });

    registry.register("string.startsWith", |args| {
        if args.len() != 2 {
            return Err(err("string.startsWith expects 2 arguments"));
        }
        let s = args[0].display_string();
        let prefix = args[1].display_string();
        Ok(Value::Bool(s.starts_with(&prefix)))
    });

    registry.register("string.endsWith", |args| {
        if args.len() != 2 {
            return Err(err("string.endsWith expects 2 arguments"));
        }
        let s = args[0].display_string();
        let suffix = args[1].display_string();
        Ok(Value::Bool(s.ends_with(&suffix)))
    });

    registry.register("string.indexOf", |args| {
        if args.len() != 2 {
            return Err(err("string.indexOf expects 2 arguments"));
        }
        let s = args[0].display_string();
        let substr = args[1].display_string();
        // Go's strings.Index returns a byte offset
        let idx = s.find(&substr).map(|i| i as i64).unwrap_or(-1);
        Ok(Value::Int(idx))
    });

    registry.register("string.toUpper", |args| {
        if args.len() != 1 {
            return Err(err("string.toUpper expects 1 argument"));
        }
        Ok(Value::str(go_to_upper(&args[0].display_string())))
    });

    registry.register("string.toLower", |args| {
        if args.len() != 1 {
            return Err(err("string.toLower expects 1 argument"));
        }
        Ok(Value::str(go_to_lower(&args[0].display_string())))
    });

    registry.register("string.trim", |args| {
        if args.len() != 1 {
            return Err(err("string.trim expects 1 argument"));
        }
        Ok(Value::str(go_trim_space(&args[0].display_string())))
    });

    registry.register("string.split", |args| {
        if args.len() != 2 {
            return Err(err("string.split expects 2 arguments"));
        }
        let s = args[0].display_string();
        let delim = args[1].display_string();
        let parts: Vec<Value> = go_split(&s, &delim)
            .into_iter()
            .map(Value::str)
            .collect();
        Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(parts))))
    });

    registry.register("string.join", |args| {
        if args.len() != 2 {
            return Err(err("string.join expects 2 arguments"));
        }
        let list = &args[0];
        let delim = args[1].display_string();
        if !matches!(list, Value::List(_)) {
            return Err(err("string.join expects a list as first argument"));
        }
        let mut parts: Vec<String> = Vec::with_capacity(list.list_len());
        for i in 0..list.list_len() {
            parts.push(list.list_get(i).unwrap_or(Value::Null).display_string());
        }
        Ok(Value::str(parts.join(&delim)))
    });
}

/// Go's strings.TrimSpace (Unicode whitespace).
fn go_trim_space(s: &str) -> String {
    s.trim_matches(|c: char| c.is_whitespace()).to_string()
}

/// Go's strings.Split semantics for non-empty delimiters.
fn go_split(s: &str, delim: &str) -> Vec<String> {
    if delim.is_empty() {
        // Go splits into UTF-8 sequences; trailing empty string dropped.
        let mut parts: Vec<String> = s.chars().map(|c| c.to_string()).collect();
        if s.is_empty() {
            return vec![String::new()];
        }
        parts.push(String::new());
        return parts;
    }
    s.split(delim).map(|p| p.to_string()).collect()
}

// ===== Math module =====

fn register_math(registry: &mut NativeRegistry) {
    registry.register("math.PI", |_| Ok(Value::Float(std::f64::consts::PI)));
    registry.register("math.E", |_| Ok(Value::Float(std::f64::consts::E)));

    registry.register("math.abs", |args| {
        if args.len() != 1 {
            return Err(err("math.abs expects 1 argument"));
        }
        match &args[0] {
            Value::Int(v) => {
                if *v < 0 {
                    Ok(Value::Int(v.wrapping_neg()))
                } else {
                    Ok(args[0].clone())
                }
            }
            Value::Float(v) => {
                if *v < 0.0 {
                    Ok(Value::Float(-v))
                } else {
                    Ok(args[0].clone())
                }
            }
            _ => Err(err("math.abs expects a numeric argument")),
        }
    });

    registry.register("math.min", |args| {
        if args.len() != 2 {
            return Err(err("math.min expects 2 arguments"));
        }
        let (a, b) = (&args[0], &args[1]);
        if matches!(a, Value::Int(_)) && matches!(b, Value::Int(_)) {
            return Ok(if a.as_int() < b.as_int() { a.clone() } else { b.clone() });
        }
        if matches!(a, Value::Float(_)) || matches!(b, Value::Float(_)) {
            return Ok(if a.as_double() < b.as_double() { a.clone() } else { b.clone() });
        }
        Err(err("math.min expects numeric arguments"))
    });

    registry.register("math.max", |args| {
        if args.len() != 2 {
            return Err(err("math.max expects 2 arguments"));
        }
        let (a, b) = (&args[0], &args[1]);
        if matches!(a, Value::Int(_)) && matches!(b, Value::Int(_)) {
            return Ok(if a.as_int() > b.as_int() { a.clone() } else { b.clone() });
        }
        if matches!(a, Value::Float(_)) || matches!(b, Value::Float(_)) {
            return Ok(if a.as_double() > b.as_double() { a.clone() } else { b.clone() });
        }
        Err(err("math.max expects numeric arguments"))
    });

    registry.register("math.floor", |args| {
        if args.len() != 1 {
            return Err(err("math.floor expects 1 argument"));
        }
        match &args[0] {
            Value::Float(v) => Ok(Value::Float(v.floor())),
            Value::Int(_) => Ok(args[0].clone()),
            _ => Err(err("math.floor expects a numeric argument")),
        }
    });

    registry.register("math.ceil", |args| {
        if args.len() != 1 {
            return Err(err("math.ceil expects 1 argument"));
        }
        match &args[0] {
            Value::Float(v) => Ok(Value::Float(v.ceil())),
            Value::Int(_) => Ok(args[0].clone()),
            _ => Err(err("math.ceil expects a numeric argument")),
        }
    });

    registry.register("math.round", |args| {
        if args.len() != 1 {
            return Err(err("math.round expects 1 argument"));
        }
        match &args[0] {
            Value::Float(v) => Ok(Value::Float(go_round(*v))),
            Value::Int(_) => Ok(args[0].clone()),
            _ => Err(err("math.round expects a numeric argument")),
        }
    });

    registry.register("math.sqrt", |args| {
        if args.len() != 1 {
            return Err(err("math.sqrt expects 1 argument"));
        }
        match &args[0] {
            Value::Float(v) => Ok(Value::Float(v.sqrt())),
            Value::Int(v) => Ok(Value::Float((*v as f64).sqrt())),
            _ => Err(err("math.sqrt expects a numeric argument")),
        }
    });

    registry.register("math.pow", |args| {
        if args.len() != 2 {
            return Err(err("math.pow expects 2 arguments"));
        }
        Ok(Value::Float(args[0].as_double().powf(args[1].as_double())))
    });

    registry.register("math.sin", |args| {
        if args.len() != 1 {
            return Err(err("math.sin expects 1 argument"));
        }
        Ok(Value::Float(args[0].as_double().sin()))
    });
    registry.register("math.cos", |args| {
        if args.len() != 1 {
            return Err(err("math.cos expects 1 argument"));
        }
        Ok(Value::Float(args[0].as_double().cos()))
    });
    registry.register("math.tan", |args| {
        if args.len() != 1 {
            return Err(err("math.tan expects 1 argument"));
        }
        Ok(Value::Float(args[0].as_double().tan()))
    });
}

/// Go's math.Round: half away from zero, matching Rust's f64::round.
fn go_round(v: f64) -> f64 {
    v.round()
}

// ===== Env module =====

fn register_env(registry: &mut NativeRegistry) {
    registry.register("env.get", |args| {
        if args.len() != 1 {
            return Err(err("env.get expects 1 argument"));
        }
        let key = args[0].display_string();
        match std::env::var(&key) {
            Ok(v) => Ok(Value::str(v)),
            Err(_) => Ok(Value::Null),
        }
    });

    registry.register("env.set", |args| {
        if args.len() != 2 {
            return Err(err("env.set expects 2 arguments"));
        }
        let key = args[0].display_string();
        let value = args[1].display_string();
        std::env::set_var(&key, &value);
        Ok(Value::Null)
    });

    registry.register("env.keys", |_| {
        let values: Vec<Value> = std::env::vars().map(|(k, _)| Value::str(k)).collect();
        Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(values))))
    });
}

// ===== File module =====

fn register_file(registry: &mut NativeRegistry) {
    registry.register("file.read", |args| {
        if args.len() != 1 {
            return Err(err("file.read expects 1 argument"));
        }
        let path = args[0].display_string();
        match std::fs::read(&path) {
            Ok(data) => Ok(Value::str(String::from_utf8_lossy(&data))),
            Err(e) => Err(err(&format!("file.read: {}", go_io_error(&e, "open", &path)))),
        }
    });

    registry.register("file.write", |args| {
        if args.len() != 2 {
            return Err(err("file.write expects 2 arguments"));
        }
        let path = args[0].display_string();
        let content = args[1].display_string();
        std::fs::write(&path, content.as_bytes())
            .map_err(|e| err(&format!("file.write: {}", go_io_error(&e, "open", &path))))?;
        Ok(Value::Null)
    });

    registry.register("file.append", |args| {
        if args.len() != 2 {
            return Err(err("file.append expects 2 arguments"));
        }
        let path = args[0].display_string();
        let content = args[1].display_string();
        use std::io::Write;
        let mut f = std::fs::OpenOptions::new()
            .append(true)
            .create(true)
            .open(&path)
            .map_err(|e| err(&format!("file.append: {}", go_io_error(&e, "open", &path))))?;
        f.write_all(content.as_bytes())
            .map_err(|e| err(&format!("file.append: {}", e)))?;
        Ok(Value::Null)
    });

    registry.register("file.delete", |args| {
        if args.len() != 1 {
            return Err(err("file.delete expects 1 argument"));
        }
        let path = args[0].display_string();
        // Go's os.Remove deletes files and empty directories.
        match std::fs::remove_file(&path) {
            Ok(_) => Ok(Value::Null),
            Err(e) if e.kind() == std::io::ErrorKind::IsADirectory => {
                std::fs::remove_dir(&path)
                    .map_err(|e2| err(&format!("file.delete: {}", go_io_error(&e2, "remove", &path))))?;
                Ok(Value::Null)
            }
            Err(e) => Err(err(&format!("file.delete: {}", go_io_error(&e, "remove", &path)))),
        }
    });

    registry.register("file.exists", |args| {
        if args.len() != 1 {
            return Err(err("file.exists expects 1 argument"));
        }
        let path = args[0].display_string();
        Ok(Value::Bool(std::fs::metadata(&path).is_ok()))
    });

    registry.register("file.temp", |args| {
        if args.len() != 1 {
            return Err(err("file.temp expects 1 argument"));
        }
        let pattern = args[0].display_string();
        let path = create_temp_file(&pattern)
            .map_err(|e| err(&format!("file.temp: {}", e)))?;
        Ok(Value::str(path))
    });

    registry.register("file.tempDir", |args| {
        if args.len() != 1 {
            return Err(err("file.tempDir expects 1 argument"));
        }
        let pattern = args[0].display_string();
        let path = create_temp_dir(&pattern)
            .map_err(|e| err(&format!("file.tempDir: {}", e)))?;
        Ok(Value::str(path))
    });
}

/// Formats io errors like Go's os package ("open <file>: no such file or directory").
fn go_io_error(e: &std::io::Error, op: &str, path: &str) -> String {
    match e.kind() {
        std::io::ErrorKind::NotFound => format!("{} {}: no such file or directory", op, path),
        std::io::ErrorKind::PermissionDenied => format!("{} {}: permission denied", op, path),
        std::io::ErrorKind::AlreadyExists => format!("{} {}: file exists", op, path),
        _ => format!("{} {}: {}", op, path, e),
    }
}

/// Implements Go's os.CreateTemp pattern semantics ("*" is replaced).
fn temp_pattern(pattern: &str) -> (String, String) {
    match pattern.rfind('*') {
        Some(i) => (pattern[..i].to_string(), pattern[i + 1..].to_string()),
        None => (pattern.to_string(), String::new()),
    }
}

fn random_temp_suffix() -> String {
    let (a, _) = os_random_pair();
    let mut bytes = [0u8; 8];
    bytes.copy_from_slice(&a.to_be_bytes());
    let mut out = String::new();
    for b in bytes {
        out.push(char::from_digit((b % 10) as u32, 10).unwrap_or('0'));
    }
    out
}

fn create_temp_file(pattern: &str) -> Result<String, String> {
    let (prefix, suffix) = temp_pattern(pattern);
    let dir = std::env::temp_dir();
    for _ in 0..10000 {
        let name = format!("{}{}{}", prefix, random_temp_suffix(), suffix);
        let path = dir.join(&name);
        if !path.exists() {
            std::fs::File::create(&path).map_err(|e| go_io_error(&e, "open", &path.to_string_lossy()))?;
            return Ok(path.to_string_lossy().to_string());
        }
    }
    Err("too many attempts".to_string())
}

fn create_temp_dir(pattern: &str) -> Result<String, String> {
    let (prefix, suffix) = temp_pattern(pattern);
    let dir = std::env::temp_dir();
    for _ in 0..10000 {
        let name = format!("{}{}{}", prefix, random_temp_suffix(), suffix);
        let path = dir.join(&name);
        match std::fs::create_dir(&path) {
            Ok(_) => return Ok(path.to_string_lossy().to_string()),
            Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => continue,
            Err(e) => return Err(go_io_error(&e, "mkdir", &path.to_string_lossy())),
        }
    }
    Err("too many attempts".to_string())
}

// ===== Process module =====

fn register_process(registry: &mut NativeRegistry) {
    registry.register("process.args", |_| {
        Ok(Value::List(Rc::new(RefCell::new(
            std::env::args().skip(2).map(Value::str).collect(),
        ))))
    });
    registry.register("process.run", |args| {
        if args.is_empty() {
            return Err(err("process.run expects at least 1 argument"));
        }
        let executable = args[0].display_string();
        let proc_args: Vec<String> = args[1..].iter().map(|a| a.display_string()).collect();
        let status = std::process::Command::new(&executable)
            .args(&proc_args)
            .status()
            .map_err(|e| err(&format!("process.run: {}", e)))?;
        let code = status.code().unwrap_or(-1);
        Ok(Value::Int(code as i64))
    });
}

// ===== Time module =====

fn register_time(registry: &mut NativeRegistry) {
    registry.register("time.now", |_| {
        let millis = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0);
        Ok(Value::Int(millis))
    });

    registry.register("time.sleep", |args| {
        if args.len() != 1 {
            return Err(err("time.sleep expects 1 argument"));
        }
        let millis = args[0].as_int().max(0) as u64;
        std::thread::sleep(Duration::from_millis(millis));
        Ok(Value::Null)
    });
}

// ===== Random module =====

fn register_random(registry: &mut NativeRegistry) {
    registry.register("random.float", |_| {
        let f = with_random(|r| r.float64());
        Ok(Value::Float(f))
    });

    registry.register("random.int", |args| {
        if args.len() != 2 {
            return Err(err("random.int expects 2 arguments"));
        }
        let a = args[0].as_int();
        let b = args[1].as_int();
        if a > b {
            return Err(err(&format!("random.int: min ({}) must be <= max ({})", a, b)));
        }
        let v = with_random(|r| a + r.int64n(b - a + 1));
        Ok(Value::Int(v))
    });

    registry.register("random.range", |args| {
        if args.len() != 2 {
            return Err(err("random.range expects 2 arguments"));
        }
        let start = args[0].as_int();
        let stop = args[1].as_int();
        if stop <= start {
            return Err(err(&format!("random.range: stop ({}) must be > start ({})", stop, start)));
        }
        let v = with_random(|r| start + r.int64n(stop - start));
        Ok(Value::Int(v))
    });

    registry.register("random.uniform", |args| {
        if args.len() != 2 {
            return Err(err("random.uniform expects 2 arguments"));
        }
        let a = args[0].as_double();
        let b = args[1].as_double();
        let v = with_random(|r| a + (b - a) * r.float64());
        Ok(Value::Float(v))
    });

    registry.register("random.choice", |args| {
        if args.len() != 1 {
            return Err(err("random.choice expects 1 argument"));
        }
        if !matches!(args[0], Value::List(_)) {
            return Err(err("random.choice expects a list"));
        }
        let n = args[0].list_len();
        if n == 0 {
            return Ok(Value::Null);
        }
        let idx = with_random(|r| r.int64n(n as i64)) as usize;
        Ok(args[0].list_get(idx).unwrap_or(Value::Null))
    });

    registry.register("random.shuffle", |args| {
        if args.len() != 1 {
            return Err(err("random.shuffle expects 1 argument"));
        }
        if !matches!(args[0], Value::List(_)) {
            return Err(err("random.shuffle expects a list"));
        }
        let n = args[0].list_len();
        let mut result: Vec<Value> = (0..n).filter_map(|i| args[0].list_get(i)).collect();
        with_random(|r| {
            for i in (1..n).rev() {
                let j = r.int64n((i + 1) as i64) as usize;
                result.swap(i, j);
            }
        });
        Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(result))))
    });

    registry.register("random.sample", |args| {
        if args.len() != 2 {
            return Err(err("random.sample expects 2 arguments"));
        }
        if !matches!(args[0], Value::List(_)) {
            return Err(err("random.sample expects a list as first argument"));
        }
        let k = args[1].as_int();
        let n = args[0].list_len();
        if k <= 0 {
            return Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(Vec::new()))));
        }
        let get = |i: usize| args[0].list_get(i).unwrap_or(Value::Null);
        if k >= n as i64 {
            let mut all: Vec<Value> = (0..n).map(get).collect();
            with_random(|r| {
                for i in (1..n).rev() {
                    let j = r.int64n((i + 1) as i64) as usize;
                    all.swap(i, j);
                }
            });
            return Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(all))));
        }
        let kk = k as usize;
        let mut result: Vec<Value> = (0..kk).map(get).collect();
        with_random(|r| {
            for i in kk..n {
                let j = r.int64n((i + 1) as i64) as usize;
                if j < kk {
                    result[j] = get(i);
                }
            }
        });
        Ok(Value::List(std::rc::Rc::new(std::cell::RefCell::new(result))))
    });

    registry.register("random.seed", |args| {
        if args.len() != 1 {
            return Err(err("random.seed expects 1 argument"));
        }
        let s = args[0].as_int();
        let seed = s as u64;
        RANDOM_SOURCE.with(|cell| {
            *cell.borrow_mut() = Some(Pcg::new(seed, seed ^ 0xdeadbeefcafebabe));
        });
        Ok(Value::Null)
    });
}

// ===== Path module =====

fn register_path(registry: &mut NativeRegistry) {
    registry.register("path.join", |args| {
        if args.is_empty() {
            return Err(err("path.join expects at least 1 argument"));
        }
        let parts: Vec<String> = args.iter().map(|a| a.display_string()).collect();
        Ok(Value::str(go_path_join(&parts)))
    });

    registry.register("path.basename", |args| {
        if args.len() != 1 {
            return Err(err("path.basename expects 1 argument"));
        }
        Ok(Value::str(go_path_base(&args[0].display_string())))
    });

    registry.register("path.dirname", |args| {
        if args.len() != 1 {
            return Err(err("path.dirname expects 1 argument"));
        }
        Ok(Value::str(go_path_dir(&args[0].display_string())))
    });

    registry.register("path.ext", |args| {
        if args.len() != 1 {
            return Err(err("path.ext expects 1 argument"));
        }
        Ok(Value::str(go_path_ext(&args[0].display_string())))
    });

    registry.register("path.abs", |args| {
        if args.len() != 1 {
            return Err(err("path.abs expects 1 argument"));
        }
        let p = args[0].display_string();
        match std::fs::canonicalize(&p) {
            Ok(abs) => Ok(Value::str(abs.to_string_lossy().to_string())),
            // Go's Abs does not require existence; it joins with cwd and cleans
            Err(_) => {
                let cwd = std::env::current_dir()
                    .map_err(|e| err(&format!("path.abs: {}", e)))?;
                let joined = go_path_join(&[cwd.to_string_lossy().to_string(), p]);
                Ok(Value::str(joined))
            }
        }
    });

    registry.register("path.exists", |args| {
        if args.len() != 1 {
            return Err(err("path.exists expects 1 argument"));
        }
        Ok(Value::Bool(std::fs::metadata(args[0].display_string()).is_ok()))
    });
}

/// Go's filepath.Join: joins and cleans, skipping empty elements.
fn go_path_join(parts: &[String]) -> String {
    let mut cleaned: Vec<&str> = Vec::new();
    for p in parts {
        if !p.is_empty() {
            cleaned.push(p);
        }
    }
    if cleaned.is_empty() {
        return String::new();
    }
    let joined = cleaned.join("/");
    go_path_clean(&joined)
}

/// Go's filepath.Clean.
fn go_path_clean(path: &str) -> String {
    if path.is_empty() {
        return ".".to_string();
    }
    let rooted = path.starts_with('/');
    let mut out: Vec<&str> = Vec::new();
    for part in path.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                if let Some(last) = out.last() {
                    if *last != ".." {
                        out.pop();
                        continue;
                    }
                }
                if !rooted {
                    out.push("..");
                }
            }
            _ => out.push(part),
        }
    }
    let mut s = out.join("/");
    if rooted {
        s = format!("/{}", s);
    }
    if s.is_empty() {
        return ".".to_string();
    }
    s
}

/// Go's filepath.Base.
fn go_path_base(path: &str) -> String {
    if path.is_empty() {
        return ".".to_string();
    }
    let mut p = path.trim_end_matches('/');
    if p.is_empty() {
        return "/".to_string();
    }
    if let Some(i) = p.rfind('/') {
        p = &p[i + 1..];
    }
    if p.is_empty() {
        return "/".to_string();
    }
    p.to_string()
}

/// Go's filepath.Dir.
fn go_path_dir(path: &str) -> String {
    let mut end = path.len();
    let bytes = path.as_bytes();
    while end > 0 && bytes[end - 1] != b'/' {
        end -= 1;
    }
    let dir = go_path_clean(&path[..end]);
    dir
}

/// Go's filepath.Ext.
fn go_path_ext(path: &str) -> String {
    for (i, c) in path.char_indices().rev() {
        if c == '/' {
            break;
        }
        if c == '.' {
            return path[i..].to_string();
        }
    }
    String::new()
}

// ===== Base64 module =====

fn register_base64(registry: &mut NativeRegistry) {
    registry.register("base64.encode", |args| {
        if args.len() != 1 {
            return Err(err("base64.encode expects 1 argument"));
        }
        let s = args[0].display_string();
        Ok(Value::str(base64::engine::general_purpose::STANDARD.encode(s.as_bytes())))
    });

    registry.register("base64.decode", |args| {
        if args.len() != 1 {
            return Err(err("base64.decode expects 1 argument"));
        }
        let s = args[0].display_string();
        match base64::engine::general_purpose::STANDARD.decode(s.as_bytes()) {
            Ok(data) => Ok(Value::str(String::from_utf8_lossy(&data))),
            Err(_) => Err(err("base64.decode: illegal base64 data")),
        }
    });
}

// ===== Hash module =====

fn register_hash(registry: &mut NativeRegistry) {
    registry.register("hash.md5", |args| {
        if args.len() != 1 {
            return Err(err("hash.md5 expects 1 argument"));
        }
        let sum = md5::Md5::digest(args[0].display_string().as_bytes());
        Ok(Value::str(hex_lower(&sum)))
    });
    registry.register("hash.sha1", |args| {
        if args.len() != 1 {
            return Err(err("hash.sha1 expects 1 argument"));
        }
        let sum = sha1::Sha1::digest(args[0].display_string().as_bytes());
        Ok(Value::str(hex_lower(&sum)))
    });
    registry.register("hash.sha256", |args| {
        if args.len() != 1 {
            return Err(err("hash.sha256 expects 1 argument"));
        }
        let sum = sha2::Sha256::digest(args[0].display_string().as_bytes());
        Ok(Value::str(hex_lower(&sum)))
    });
    registry.register("hash.sha512", |args| {
        if args.len() != 1 {
            return Err(err("hash.sha512 expects 1 argument"));
        }
        let sum = sha2::Sha512::digest(args[0].display_string().as_bytes());
        Ok(Value::str(hex_lower(&sum)))
    });
}

fn hex_lower(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{:02x}", b));
    }
    s
}

// ===== Secrets module =====

fn register_secrets(registry: &mut NativeRegistry) {
    registry.register("secrets.token", |args| {
        if args.len() != 1 {
            return Err(err("secrets.token expects 1 argument"));
        }
        let n = args[0].as_int();
        if n <= 0 {
            return Err(err("secrets.token: n must be > 0"));
        }
        let mut buf = vec![0u8; n as usize];
        getrandom::getrandom(&mut buf).map_err(|e| err(&format!("secrets.token: {}", e)))?;
        Ok(Value::str(
            base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(&buf),
        ))
    });

    registry.register("secrets.hex", |args| {
        if args.len() != 1 {
            return Err(err("secrets.hex expects 1 argument"));
        }
        let n = args[0].as_int();
        if n <= 0 {
            return Err(err("secrets.hex: n must be > 0"));
        }
        let mut buf = vec![0u8; n as usize];
        getrandom::getrandom(&mut buf).map_err(|e| err(&format!("secrets.hex: {}", e)))?;
        Ok(Value::str(hex_lower(&buf)))
    });
}

// ===== Stack module =====

fn register_stack(registry: &mut NativeRegistry) {
    registry.register("stack.len", collection_len_handler);

    registry.register("stack.push", |args| {
        if args.len() != 2 {
            return Err(err("stack.push expects 2 arguments (stack, value)"));
        }
        if !matches!(args[0], Value::Stack(_)) {
            return Err(err("stack.push expects a stack as first argument"));
        }
        args[0].stack_push(args[1].clone());
        Ok(Value::Null)
    });

    registry.register("stack.pop", |args| {
        if args.len() != 1 {
            return Err(err("stack.pop expects 1 argument"));
        }
        if !matches!(args[0], Value::Stack(_)) {
            return Err(err("stack.pop expects a stack"));
        }
        if args[0].stack_len() == 0 {
            return Err(err("stack.pop: stack is empty"));
        }
        Ok(args[0].stack_pop().unwrap_or(Value::Null))
    });

    registry.register("stack.peek", |args| {
        if args.len() != 1 {
            return Err(err("stack.peek expects 1 argument"));
        }
        if !matches!(args[0], Value::Stack(_)) {
            return Err(err("stack.peek expects a stack"));
        }
        if args[0].stack_len() == 0 {
            return Err(err("stack.peek: stack is empty"));
        }
        Ok(args[0].stack_peek().unwrap_or(Value::Null))
    });

    registry.register("stack.isEmpty", |args| {
        if args.len() != 1 {
            return Err(err("stack.isEmpty expects 1 argument"));
        }
        if !matches!(args[0], Value::Stack(_)) {
            return Err(err("stack.isEmpty expects a stack"));
        }
        Ok(Value::Bool(args[0].stack_len() == 0))
    });
}

// ===== List module =====

fn register_list(registry: &mut NativeRegistry) {
    registry.register("list.len", collection_len_handler);
}

// ===== Map module =====

fn register_map(registry: &mut NativeRegistry) {
    registry.register("map.len", collection_len_handler);
    registry.register("map.contains", |args| {
        if args.len() != 2 {
            return Err(err("map.contains expects 2 arguments (map, key)"));
        }
        if !matches!(args[0], Value::Map(_)) {
            return Err(err("map.contains expects a map as first argument"));
        }
        Ok(Value::Bool(args[0].map_contains(&args[1])))
    });
}

// ===== Aliases =====

fn register_aliases(registry: &mut NativeRegistry) {
    registry.register("print", |args| {
        if args.len() != 1 {
            return Err(err("print expects 1 argument"));
        }
        print!("{}", args[0].display_string());
        use std::io::Write;
        let _ = std::io::stdout().flush();
        Ok(Value::Null)
    });
    registry.register("println", |args| {
        if args.len() != 1 {
            return Err(err("println expects 1 argument"));
        }
        println!("{}", args[0].display_string());
        Ok(Value::Null)
    });
    registry.register("typeOf", |args| {
        if args.len() != 1 {
            return Err(err("typeOf expects 1 argument"));
        }
        Ok(Value::str(type_name(&args[0])))
    });
    registry.register("isType", |args| {
        if args.len() != 2 {
            return Err(err("isType expects 2 arguments"));
        }
        let actual = type_name(&args[0]);
        let expected = args[1].display_string();
        Ok(Value::Bool(actual == expected))
    });
}

// Keep formatting helper referenced (used by tests).
#[allow(dead_code)]
fn _assert_fmt(f: f64) -> String {
    go_format_float(f)
}
