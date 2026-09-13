//! System process/runtime methods: stream accessors, line separator,
//! environment access, clocks, and the program-local property store.
//!
//! Covers compiler/runtime behavior that is awkward to express in the shell
//! conformance runner: overload selection, static return nullability,
//! launch-property configuration through the embedding entrypoint, thread
//! visibility, run isolation, and controlled-environment lookup.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

use solvik_rs::vm::{RunConfig, Vm};

fn run(source: &str) -> i64 {
    let module = solvik_rs::compile("system_methods.sol", source).expect("compile");
    Vm::run_main(module, RunConfig::default()).expect("run")
}

fn run_with_properties(source: &str, properties: Vec<(&str, &str)>) -> i64 {
    let module = solvik_rs::compile("system_methods.sol", source).expect("compile");
    let config = RunConfig {
        args: vec![],
        properties: properties
            .into_iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect(),
    };
    Vm::run_main(module, config).expect("run")
}

fn compile_fails(source: &str) -> String {
    solvik_rs::compile("system_methods.sol", source)
        .expect_err("must be rejected at compile time")
        .message()
        .to_string()
}

// ---------------------------------------------------------------------------
// Streams and line separator
// ---------------------------------------------------------------------------

#[test]
fn stream_accessors_and_line_separator() {
    // No stdin read here: an in-process readln would block on the test
    // process's inherited stdin. getIn() with controlled /dev/null stdin is
    // covered by conformance case 207.
    let code = run(r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        if System.getLineSeparator() != "\n" { return 1 }
        System.getOut().print("")
        System.getErr().print("")
        if System.getIn() == null { return 2 }
        return 0
    }
}
"#);
    assert_eq!(code, 0);
}

// ---------------------------------------------------------------------------
// Overload selection and static return nullability
// ---------------------------------------------------------------------------

#[test]
fn get_property_overloads_select_precisely() {
    // Both arities compile; the one-argument form is nullable, the
    // two-argument form is non-null, and coalescing/narrowing work.
    let code = run(r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let absent: String? = System.getProperty("nope")
        if absent != null { return 1 }
        let withFallback: String = System.getProperty("nope", "fb")
        if withFallback != "fb" { return 2 }
        let coalesced: String = System.getProperty("nope") ?? "coalesced"
        if coalesced != "coalesced" { return 3 }
        let maybe: String? = System.getProperty("nope")
        if maybe != null {
            let narrowed: String = maybe
            if narrowed != "x" { return 4 }
        }
        return 0
    }
}
"#);
    assert_eq!(code, 0);
}

#[test]
fn get_env_forms_have_correct_static_types() {
    let code = run(r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let snapshot: Map<String, String> = System.getEnv()
        if snapshot == null { return 1 }
        let absent: String? = System.getEnv("SOLVIK_DEFINITELY_ABSENT_VAR")
        if absent != null { return 2 }
        return 0
    }
}
"#);
    assert_eq!(code, 0);
}

#[test]
fn wrong_arity_is_a_normal_diagnostic() {
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        System.getProperty()
        return 0
    }
}
"#,
    );
    assert!(
        msg.contains("System.getProperty expects 1 or 2 argument(s), found 0"),
        "{msg}"
    );

    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        System.getEnv("a", "b")
        return 0
    }
}
"#,
    );
    assert!(
        msg.contains("System.getEnv expects 0 or 1 argument(s), found 2"),
        "{msg}"
    );
}

#[test]
fn wrong_argument_types_are_rejected() {
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        System.getProperty("key", 1)
        return 0
    }
}
"#,
    );
    assert!(msg.contains("does not match"), "{msg}");

    // Nullable keys/values are not accepted where String is required.
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let key: String? = null
        System.getProperty(key)
        return 0
    }
}
"#,
    );
    assert!(msg.contains("does not match"), "{msg}");
}

#[test]
fn nullable_results_cannot_bind_to_non_nullable_strings() {
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let s: String = System.getEnv("X")
        return 0
    }
}
"#,
    );
    assert!(!msg.is_empty());

    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let s: String = System.getProperty("X")
        return 0
    }
}
"#,
    );
    assert!(!msg.is_empty());
}

#[test]
fn env_snapshot_cannot_bind_to_non_map_type() {
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let n: Long = System.getEnv()
        return 0
    }
}
"#,
    );
    assert!(!msg.is_empty());
}

#[test]
fn unknown_system_members_are_rejected() {
    let msg = compile_fails(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        System.bogus()
        return 0
    }
}
"#,
    );
    assert!(msg.contains("no static member 'bogus' on System"), "{msg}");
}

// ---------------------------------------------------------------------------
// Launch properties through the embedding entrypoint
// ---------------------------------------------------------------------------

const PROP_PROGRAM: &str = r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        if System.getProperty("mode") != "test" { return 1 }
        if System.getProperty("mode", "fb") != "test" { return 2 }
        if args.size() != 0 { return 3 }
        return 0
    }
}
"#;

#[test]
fn launch_properties_initialize_the_store_before_entry() {
    assert_eq!(run_with_properties(PROP_PROGRAM, vec![("mode", "test")]), 0);
    // Without the launch property the same module sees an empty store.
    assert_eq!(run(PROP_PROGRAM), 1);
}

#[test]
fn repeated_launch_keys_resolve_last_wins() {
    let code = run_with_properties(
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        if System.getProperty("a") != "2" { return 1 }
        return 0
    }
}
"#,
        vec![("a", "1"), ("a", "2")],
    );
    assert_eq!(code, 0);
}

#[test]
fn separate_runs_get_fresh_property_state() {
    let setter = r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        System.setProperty("k", "v")
        return 0
    }
}
"#;
    let reader = r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        if System.getProperty("k") != null { return 1 }
        return 0
    }
}
"#;
    assert_eq!(run(setter), 0);
    // A later run_main call receives an independent property store.
    assert_eq!(run(reader), 0);
}

#[test]
fn launch_properties_do_not_touch_the_host_environment() {
    run_with_properties(PROP_PROGRAM, vec![("mode", "test")]);
    assert!(std::env::var("mode").is_err());
}

// ---------------------------------------------------------------------------
// Thread visibility
// ---------------------------------------------------------------------------

#[test]
fn worker_threads_observe_the_shared_property_store() {
    let code = run_with_properties(
        r#"package sysm
struct Worker implements Runnable {
    results: List<Long>

    public static func new(results: List<Long>): Self {
        return Self { results: results, }
    }

    public func run(self): Void {
        if System.getProperty("shared") == "yes" {
            self.results.add(1)
        } else {
            self.results.add(0)
        }
    }
}
struct Main {
    public static func run(args: String...): Long {
        let results: List<Long> = List.new()
        let t: Thread = Thread.new(Worker.new(results))
        t.start()
        t.join()
        if results.size() != 1 || results.get(0) != 1 { return 1 }
        return 0
    }
}
"#,
        vec![("shared", "yes")],
    );
    assert_eq!(code, 0);
}

#[test]
fn static_initializers_observe_launch_properties() {
    let code = run_with_properties(
        r#"package sysm
struct Holder {
    static mutable ok: Long = 0

    static {
        // Launch properties are installed before any struct initializes.
        if System.getProperty("mode") == "test" {
            Holder.ok = 1
        }
    }

    public static func check(): Long {
        return Holder.ok
    }
}
struct Main {
    public static func run(args: String...): Long {
        return Holder.check()
    }
}
"#,
        vec![("mode", "test")],
    );
    assert_eq!(code, 1);
}

// ---------------------------------------------------------------------------
// Clocks
// ---------------------------------------------------------------------------

#[test]
fn nano_time_samples_are_monotonic() {
    let code = run(r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let a: Long = System.getNanoTime()
        let b: Long = System.getNanoTime()
        let c: Long = System.getNanoTime()
        if b < a || c < b { return 1 }
        return 0
    }
}
"#);
    assert_eq!(code, 0);
}

#[test]
fn current_time_millis_matches_time_now_and_host_clock() {
    // The entry point returns the wall clock so the host can bound it.
    let module = solvik_rs::compile(
        "system_methods.sol",
        r#"package sysm
struct Main {
    public static func run(args: String...): Long {
        let now: Long = System.getCurrentTimeMillis()
        if now < Time.now() - 1000 || now > Time.now() + 1000 { return -1 }
        return now
    }
}
"#,
    )
    .expect("compile");
    let before = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    let millis = Vm::run_main(module, RunConfig::default()).expect("run");
    let after = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    // Broad tolerance: sanity, not precision.
    assert!(
        millis >= before - 60_000 && millis <= after + 60_000,
        "millis={millis}"
    );
}

// ---------------------------------------------------------------------------
// Controlled-environment lookup (subprocess isolation)
// ---------------------------------------------------------------------------

fn solvik_bin() -> &'static Path {
    Path::new(env!("CARGO_BIN_EXE_solvik"))
}

struct WorkDir(PathBuf);

impl WorkDir {
    fn new(label: &str) -> Self {
        static COUNTER: AtomicUsize = AtomicUsize::new(0);
        let n = COUNTER.fetch_add(1, Ordering::Relaxed);
        let dir = std::env::temp_dir().join(format!(
            "solvik-system-methods-{}-{}-{label}",
            std::process::id(),
            n
        ));
        std::fs::create_dir_all(&dir).unwrap();
        WorkDir(dir)
    }
}

impl std::ops::Deref for WorkDir {
    type Target = PathBuf;
    fn deref(&self) -> &PathBuf {
        &self.0
    }
}

impl Drop for WorkDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

#[test]
fn controlled_environment_lookup_via_cli() {
    let dir = WorkDir::new("env_lookup");
    let src = dir.join("env.sol");
    std::fs::write(
        &src,
        r#"package envdemo
struct Main {
    public static func run(args: String...): Long {
        let named: String? = System.getEnv("SOLVIK_SYSMETH_PROBE")
        if named != "probe-value" { return 1 }
        let snapshot: Map<String, String> = System.getEnv()
        let inSnapshot: String? = snapshot.get("SOLVIK_SYSMETH_PROBE")
        if inSnapshot != "probe-value" { return 2 }
        // Mutating the snapshot never touches the host environment.
        snapshot.put("SOLVIK_SYSMETH_PROBE", "mutated")
        if System.getEnv("SOLVIK_SYSMETH_PROBE") != "probe-value" { return 3 }
        return 0
    }
}
"#,
    )
    .unwrap();
    let out = Command::new(solvik_bin())
        .arg(src.to_str().unwrap())
        .env("SOLVIK_SYSMETH_PROBE", "probe-value")
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert_eq!(
        out.status.code(),
        Some(0),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );

    // Without the variable, named lookup is null and the snapshot lacks it.
    let src2 = dir.join("env_absent.sol");
    std::fs::write(
        &src2,
        r#"package envdemo
struct Main {
    public static func run(args: String...): Long {
        if System.getEnv("SOLVIK_SYSMETH_PROBE") != null { return 1 }
        let snapshot: Map<String, String> = System.getEnv()
        if snapshot.get("SOLVIK_SYSMETH_PROBE") != null { return 2 }
        return 0
    }
}
"#,
    )
    .unwrap();
    let out = Command::new(solvik_bin())
        .arg(src2.to_str().unwrap())
        .env_remove("SOLVIK_SYSMETH_PROBE")
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert_eq!(
        out.status.code(),
        Some(0),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
}
