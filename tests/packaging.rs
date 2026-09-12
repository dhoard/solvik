//! End-to-end tests for `solvik --package`: CLI behavior, packaged executable
//! execution parity with normal execution, corruption handling, permissions,
//! and packaging without a Rust toolchain on PATH.

use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::atomic::{AtomicUsize, Ordering};

fn solvik_bin() -> &'static Path {
    Path::new(env!("CARGO_BIN_EXE_solvik"))
}

fn runtime_bin() -> PathBuf {
    // CARGO_BIN_EXE_* is not defined for every bin target in all cargo
    // versions; the runtime sits beside the compiler binary, so derive it.
    Path::new(env!("CARGO_BIN_EXE_solvik"))
        .with_file_name("solvik-runtime")
        .to_path_buf()
}

/// A unique working directory per test invocation. Removed on drop so the
/// suite does not accumulate runtime-image copies in the system temp dir.
struct WorkDir(PathBuf);

impl WorkDir {
    fn new(label: &str) -> Self {
        static COUNTER: AtomicUsize = AtomicUsize::new(0);
        let n = COUNTER.fetch_add(1, Ordering::Relaxed);
        let dir = std::env::temp_dir().join(format!(
            "solvik-package-test-{}-{}-{label}",
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

impl AsRef<Path> for WorkDir {
    fn as_ref(&self) -> &Path {
        &self.0
    }
}

impl Drop for WorkDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

/// Create a per-invocation working directory (cleaned up on drop).
fn workdir(label: &str) -> WorkDir {
    WorkDir::new(label)
}

/// Invoke the built `solvik` compiler with the runtime override set, so
/// runtime discovery never depends on the install layout.
fn solvik(dir: &Path, args: &[&str]) -> Output {
    let mut cmd = Command::new(solvik_bin());
    cmd.current_dir(dir)
        .env("SOLVIK_RUNTIME", runtime_bin())
        .args(args)
        .stdin(std::process::Stdio::null());
    cmd.output().unwrap()
}

fn write_program(dir: &Path, name: &str, source: &str) -> String {
    std::fs::write(dir.join(name), source).unwrap();
    name.to_string()
}

const HELLO: &str = r#"package hello

class Main {

    public static run(args: String...): Long {
        System.out().println("hello from solvik")
        return 0
    }
}
"#;

const ARGS: &str = r#"package argsdemo

class Main {

    public static run(args: String...): Long {
        for a in args {
            System.out().println("arg: " .. a)
        }
        return 0
    }
}
"#;

const EXIT_CODE: &str = r#"package exitcode

class Main {

    public static run(args: String...): Long {
        return 7
    }
}
"#;

const RUNTIME_ERROR: &str = r#"package badruntime

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [1]
        System.out().println(x.get(5))
        return 0
    }
}
"#;

const STDLIB: &str = r#"package stdlibdemo

class Main {

    public static run(args: String...): Long {
        System.out().println(Hash.sha256("abc"))
        let n: Long = Long.from("42")
        System.out().println(n * 2)
        return 0
    }
}
"#;

// ---------------------------------------------------------------------------
// CLI behavior
// ---------------------------------------------------------------------------

#[test]
fn package_requires_source() {
    let dir = workdir("requires_source");
    let out = solvik(&dir, &["--package"]);
    assert_eq!(out.status.code(), Some(3));
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(stderr.contains("a source file is required"), "{stderr}");
}

#[test]
fn package_validates_source_and_leaves_no_output() {
    let dir = workdir("validates_source");
    let src = write_program(&dir, "broken.sol", "package broken\nthis is not solvik\n");
    let out = solvik(&dir, &["--package", &src]);
    assert_eq!(
        out.status.code(),
        Some(1),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(!dir.join("broken").exists(), "no output may be created");
}

#[test]
fn package_default_output_name() {
    let dir = workdir("default_name");
    let src = write_program(&dir, "hello.sol", HELLO);
    let out = solvik(&dir, &["--package", &src]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(dir.join("hello").is_file());
}

#[test]
fn package_custom_output_both_orderings() {
    let dir = workdir("custom_output");
    let src = write_program(&dir, "hello.sol", HELLO);
    let out = solvik(&dir, &["--package", &src, "-o", "myapp"]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(dir.join("myapp").is_file());

    let out = solvik(&dir, &["-o", "myapp2", "--package", &src]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(dir.join("myapp2").is_file());
}

#[test]
fn package_missing_o_value_is_an_error() {
    let dir = workdir("missing_o");
    let src = write_program(&dir, "hello.sol", HELLO);
    let out = solvik(&dir, &["--package", &src, "-o"]);
    assert_eq!(out.status.code(), Some(3));
    assert!(String::from_utf8_lossy(&out.stderr).contains("-o requires a value"));
}

#[test]
fn package_runtime_not_found_is_a_clear_error() {
    let dir = workdir("no_runtime");
    let src = write_program(&dir, "hello.sol", HELLO);
    let mut cmd = Command::new(solvik_bin());
    cmd.current_dir(&dir)
        .env("SOLVIK_RUNTIME", dir.join("definitely-not-a-runtime"))
        .arg("--package")
        .arg(&src)
        .stdin(std::process::Stdio::null());
    let out = cmd.output().unwrap();
    assert_eq!(out.status.code(), Some(3));
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(stderr.contains("runtime image not found"), "{stderr}");
    assert!(!dir.join("hello").exists());
}

#[test]
fn package_output_already_exists_fails() {
    let dir = workdir("already_exists");
    let src = write_program(&dir, "hello.sol", HELLO);
    assert!(solvik(&dir, &["--package", &src]).status.success());
    let out = solvik(&dir, &["--package", &src]);
    assert_eq!(out.status.code(), Some(3));
    assert!(String::from_utf8_lossy(&out.stderr).contains("already exists"));
}

#[test]
fn existing_modes_still_work() {
    let dir = workdir("existing_modes");
    let src = write_program(&dir, "hello.sol", HELLO);

    let out = solvik(&dir, &["--version"]);
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("solvik"));

    let out = solvik(&dir, &["--check", &src]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );

    let broken = write_program(&dir, "broken.sol", "package broken\nnot solvik\n");
    let out = solvik(&dir, &["--check", &broken]);
    assert_eq!(out.status.code(), Some(1));

    let out = solvik(&dir, &["--format", &src]);
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("class Main"));

    let out = solvik(&dir, &["hello.sol"]);
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("hello from solvik"));
}

// ---------------------------------------------------------------------------
// Packaged executable behavior parity
// ---------------------------------------------------------------------------

/// Package `source` into `dir/app` and return its path.
fn make_package(dir: &Path, name: &str, source: &str) -> PathBuf {
    let src = write_program(dir, name, source);
    let out = solvik(dir, &["--package", &src, "-o", "app"]);
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    dir.join("app")
}

#[test]
fn simple_output_matches_normal_execution() {
    let dir = workdir("simple_output");
    let app = make_package(&dir, "prog.sol", HELLO);

    let normal = solvik(&dir, &["prog.sol"]);
    let packaged = Command::new(&app)
        .current_dir(&dir)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();

    assert_eq!(normal.status.code(), packaged.status.code());
    assert_eq!(normal.stdout, packaged.stdout);
    assert_eq!(normal.stderr, packaged.stderr);
    assert!(String::from_utf8_lossy(&packaged.stdout).contains("hello from solvik"));
}

#[test]
fn runtime_arguments_are_forwarded() {
    let dir = workdir("args");
    let app = make_package(&dir, "prog.sol", ARGS);

    let normal = solvik(&dir, &["prog.sol", "one", "two", "three"]);
    let packaged = Command::new(&app)
        .current_dir(&dir)
        .args(["one", "two", "three"])
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();

    assert_eq!(normal.status.code(), packaged.status.code());
    assert_eq!(normal.stdout, packaged.stdout);
    let stdout = String::from_utf8_lossy(&packaged.stdout);
    for a in ["arg: one", "arg: two", "arg: three"] {
        assert!(stdout.contains(a), "{stdout}");
    }
}

#[test]
fn exit_code_matches_normal_execution() {
    let dir = workdir("exit_code");
    let app = make_package(&dir, "prog.sol", EXIT_CODE);

    let normal = solvik(&dir, &["prog.sol"]);
    let packaged = Command::new(&app)
        .current_dir(&dir)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();

    assert_eq!(normal.status.code(), Some(7));
    assert_eq!(packaged.status.code(), Some(7));
}

#[test]
fn runtime_error_matches_normal_execution() {
    let dir = workdir("runtime_error");
    let app = make_package(&dir, "prog.sol", RUNTIME_ERROR);

    let normal = solvik(&dir, &["prog.sol"]);
    let packaged = Command::new(&app)
        .current_dir(&dir)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();

    assert_eq!(normal.status.code(), Some(2));
    assert_eq!(packaged.status.code(), Some(2));
    // Same error text, including the source location carried by the bytecode.
    assert_eq!(normal.stderr, packaged.stderr);
    let stderr = String::from_utf8_lossy(&packaged.stderr);
    assert!(stderr.starts_with("runtime error at prog.sol:"), "{stderr}");
    assert!(stderr.contains("list index out of range"), "{stderr}");
}

#[test]
fn stdlib_native_functionality_works_in_package() {
    let dir = workdir("stdlib");
    let app = make_package(&dir, "prog.sol", STDLIB);

    let normal = solvik(&dir, &["prog.sol"]);
    let packaged = Command::new(&app)
        .current_dir(&dir)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();

    assert_eq!(normal.status.code(), packaged.status.code());
    assert_eq!(normal.stdout, packaged.stdout);
    // The SHA-256 of "abc" proves native/stdlib code ran inside the package.
    let stdout = String::from_utf8_lossy(&packaged.stdout);
    assert!(stdout.contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    assert!(stdout.contains("84"));
}

// ---------------------------------------------------------------------------
// Corruption and robustness
// ---------------------------------------------------------------------------

fn corrupt_variant(app: &Path, label: &str, mutate: impl Fn(&mut Vec<u8>)) -> PathBuf {
    let mut data = std::fs::read(app).unwrap();
    mutate(&mut data);
    let stem = app.file_stem().unwrap().to_string_lossy();
    let p = app.with_file_name(format!("{stem}-{label}"));
    std::fs::write(&p, &data).unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&p, std::fs::Permissions::from_mode(0o755)).unwrap();
    }
    p
}

fn assert_fails_cleanly(app: &Path, label: &str) {
    let out = Command::new(app)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap_or_else(|e| panic!("{label}: failed to spawn {}: {e}", app.display()));
    assert_ne!(
        out.status.code(),
        Some(0),
        "{label}: corrupted package must not succeed"
    );
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(
        !stderr.contains("panic"),
        "{label}: must not panic: {stderr}"
    );
    assert!(!stderr.is_empty(), "{label}: must produce a useful error");
}

#[test]
fn corrupted_packages_fail_cleanly() {
    let dir = workdir("corruption");
    let app = make_package(&dir, "prog.sol", HELLO);
    let footer = 52usize;

    // 1. Flip one payload byte (hash mismatch).
    let p = corrupt_variant(&app, "payload", |d| {
        let off = d.len() - footer - 10;
        d[off] ^= 0xFF;
    });
    assert_fails_cleanly(&p, "payload byte flip");

    // 2. Corrupt the footer magic.
    let p = corrupt_variant(&app, "magic", |d| {
        let off = d.len() - footer;
        d[off] = b'X';
    });
    assert_fails_cleanly(&p, "footer magic");

    // 3. Corrupt the package version.
    let p = corrupt_variant(&app, "version", |d| {
        let off = d.len() - footer;
        d[off + 8..off + 12].copy_from_slice(&99u32.to_le_bytes());
    });
    assert_fails_cleanly(&p, "package version");

    // 4. Replace payload length with an impossible value.
    let p = corrupt_variant(&app, "length", |d| {
        let off = d.len() - footer;
        d[off + 12..off + 20].copy_from_slice(&u64::MAX.to_le_bytes());
    });
    assert_fails_cleanly(&p, "impossible length");

    // 5. Truncate the executable.
    let p = corrupt_variant(&app, "truncated", |d| d.truncate(d.len() / 2));
    assert_fails_cleanly(&p, "truncated");
}

#[cfg(unix)]
#[test]
fn packaged_executable_has_execute_permission() {
    use std::os::unix::fs::PermissionsExt;
    let dir = workdir("permissions");
    let app = make_package(&dir, "prog.sol", HELLO);
    let mode = std::fs::metadata(&app).unwrap().permissions().mode();
    assert_ne!(mode & 0o111, 0, "execute bit missing: {mode:o}");
    // Execute it directly, without any interpreter.
    let out = Command::new(&app)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("hello from solvik"));
}

#[test]
fn packaged_executable_needs_no_source_or_sidecar() {
    let dir = workdir("no_sidecar");
    let src = write_program(&dir, "prog.sol", HELLO);
    let app = make_package(&dir, "prog.sol", HELLO);

    // Remove the original source entirely.
    std::fs::remove_file(dir.join(&src)).unwrap();
    let out = Command::new(&app)
        .current_dir(&dir)
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    assert!(String::from_utf8_lossy(&out.stdout).contains("hello from solvik"));

    // No bytecode sidecar or temporary artifact may have been created.
    let entries: Vec<_> = std::fs::read_dir(&dir)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(entries, vec!["app"], "unexpected entries: {entries:?}");
}

#[test]
fn packaging_works_without_rust_on_path() {
    // The already-built compiler must package using only the prebuilt
    // runtime image: no cargo, rustc, or linker may be invoked.
    let dir = workdir("no_rust_path");
    let src = write_program(&dir, "prog.sol", HELLO);

    let out = Command::new(solvik_bin())
        .current_dir(&dir)
        .env("PATH", "")
        .env("SOLVIK_RUNTIME", runtime_bin())
        .arg("--package")
        .arg(&src)
        .arg("-o")
        .arg("app")
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert!(
        out.status.success(),
        "stderr: {}",
        String::from_utf8_lossy(&out.stderr)
    );

    let out = Command::new(dir.join("app"))
        .env("PATH", "")
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    assert!(out.status.success());
    assert!(String::from_utf8_lossy(&out.stdout).contains("hello from solvik"));
}

#[test]
fn packaging_is_deterministic() {
    let dir = workdir("determinism");
    let src = write_program(&dir, "prog.sol", HELLO);
    assert!(solvik(&dir, &["--package", &src, "-o", "a"])
        .status
        .success());
    assert!(solvik(&dir, &["--package", &src, "-o", "b"])
        .status
        .success());
    assert_eq!(
        std::fs::read(dir.join("a")).unwrap(),
        std::fs::read(dir.join("b")).unwrap()
    );
}

// ---------------------------------------------------------------------------
// Full conformance-suite parity
// ---------------------------------------------------------------------------

/// Every conformance case must behave identically when executed normally
/// and when executed from a packaged executable: packaging must not create
/// a subtly different execution mode.
#[test]
fn all_conformance_cases_match_when_packaged() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR"));
    let mut cases: Vec<_> = std::fs::read_dir(root.join("test/cases"))
        .unwrap()
        .map(|e| e.unwrap().path())
        .filter(|p| p.is_dir())
        .collect();
    cases.sort();
    assert!(!cases.is_empty(), "conformance cases missing");

    for case in &cases {
        let label = case.file_name().unwrap().to_string_lossy().into_owned();
        // Compile-error cases have no module to package; their behavior is
        // covered by the dedicated CLI tests above.
        let want_code = std::fs::read_to_string(case.join("expected.code"))
            .ok()
            .and_then(|s| s.trim().parse::<i32>().ok())
            .unwrap_or(0);
        if want_code == 1 {
            continue;
        }
        let rel = case
            .join("main.sol")
            .strip_prefix(root)
            .unwrap()
            .to_string_lossy()
            .into_owned();

        let dir = workdir(&label);
        let app = dir.join("app");
        let app_str = app.to_string_lossy().into_owned();
        let out = solvik(root, &["--package", &rel, "-o", &app_str]);
        assert!(
            out.status.success(),
            "{label}: packaging failed: {}",
            String::from_utf8_lossy(&out.stderr)
        );

        let configure = |cmd: &mut Command| {
            cmd.current_dir(root);
            if case.join("args.txt").exists() {
                cmd.args(
                    std::fs::read_to_string(case.join("args.txt"))
                        .unwrap()
                        .lines(),
                );
            }
            if case.join("stdin.txt").exists() {
                cmd.stdin(std::fs::File::open(case.join("stdin.txt")).unwrap());
            } else {
                cmd.stdin(std::process::Stdio::null());
            }
        };

        let mut normal = Command::new(solvik_bin());
        normal.env("SOLVIK_RUNTIME", runtime_bin()).arg(&rel);
        configure(&mut normal);
        let normal = normal.output().unwrap();

        let mut packaged = Command::new(app);
        configure(&mut packaged);
        let packaged = packaged.output().unwrap();

        assert_eq!(
            normal.status.code(),
            packaged.status.code(),
            "{label}: exit"
        );
        assert_eq!(normal.stdout, packaged.stdout, "{label}: stdout");
        // Compile-time warnings surface when the package is created, not
        // when the packaged program runs; compare everything else exactly.
        let strip_warnings = |out: &[u8]| -> Vec<u8> {
            String::from_utf8_lossy(out)
                .lines()
                .filter(|l| !l.contains(": warning W"))
                .collect::<Vec<_>>()
                .join("\n")
                .into_bytes()
        };
        assert_eq!(
            strip_warnings(&normal.stderr),
            strip_warnings(&packaged.stderr),
            "{label}: stderr"
        );
    }
}
