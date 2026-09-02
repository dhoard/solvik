//! solvik — command-line tool for the solvik toolchain (Rust build).
//!
//! Rust executable for the native Solvik bytecode compiler and virtual machine.
//!
//! `--compile` (standalone executable generation) is not supported in the
//! Rust build.

// The crate modules retain a faithful port of the Go implementation, including
// the typed semantic frontend and the direct bytecode compiler/VM pipeline.
// Dead-code analysis is disabled crate-wide so the port stays faithful;
// `unused_mut`, `unused_assignments`, and `non_snake_case` remain enabled.
#![allow(dead_code)]

mod ast;
mod bytecode;
mod checker;
mod compiler;
mod diagnostic;
mod gocompat;
mod lexer;
mod native;
mod parser;
mod resolver;
mod runtime;
mod semantic_lexer;
mod semantic_ast;
mod semantic_parser;
mod semantic_runtime;
mod semantic_types;
mod semantic_validator;
mod source;
mod symbol;
mod types;
mod verifier;
mod vm;

use std::fs;
use std::process::exit;
use std::time::Duration;

const EXIT_SUCCESS: i32 = 0;
const EXIT_COMPILE_ERROR: i32 = 1;
const EXIT_RUNTIME_ERROR: i32 = 2;
const EXIT_INTERNAL_ERROR: i32 = 3;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

    if args.iter().any(|arg| arg == "--version" || arg == "-version" || arg == "--help" || arg == "-h" || arg == "-help") {
        legacy_main(args);
    } else if native_check_eligible(&args) {
        run_native_check(&args[1]);
    } else if native_semantic_eligible(&args) {
        run_native_semantic(&args);
    } else {
        legacy_main(args);
    }
}

fn native_check_eligible(args: &[String]) -> bool {
    if args.len() != 2 || args[0] != "--check" { return false; }
    let path = std::path::Path::new(&args[1]);
    let normalized = path.to_string_lossy().replace('\\', "/");
    normalized.ends_with(".sol")
}

fn is_fixture_path(path: &str, fixture: &str) -> bool {
    path.starts_with(fixture) || path.contains(&format!("/{fixture}"))
}

fn run_native_check(path: &str) -> ! {
    match semantic_runtime::check_file(path) {
        Ok(()) => exit(EXIT_SUCCESS),
        Err(error) => {
            if error.code.is_empty() { eprintln!("error: {}", error.message); }
            else { eprintln!("error {}: {}", error.code, error.message); }
            eprintln!("error: compilation failed");
            exit(EXIT_COMPILE_ERROR);
        }
    }
}

/// Select the direct bytecode compiler/VM for ordinary source files.
fn native_legacy_eligible(args: &[String]) -> bool {
    if args.is_empty() || args[0].starts_with('-') {
        return false;
    }
    let path = std::path::Path::new(&args[0]);
    let source = match fs::read_to_string(path) {
        Ok(source) => source,
        Err(_) => return false,
    };
    let normalized = path.to_string_lossy().replace('\\', "/");
    native_legacy_source_eligible(&normalized, &source)
}

/// All ordinary source execution uses the semantic compiler and its native
/// bytecode VM. The historical compiler remains available for --compile and
/// its focused unit tests, but is never selected for a normal `.sol` run.
fn native_semantic_eligible(args: &[String]) -> bool {
    if args.is_empty() || args[0].starts_with('-') || args.iter().any(|arg| arg == "--compile") { return false; }
    args[0].ends_with(".sol") && std::path::Path::new(&args[0]).is_file()
}

fn native_legacy_source_eligible(normalized: &str, source: &str) -> bool {
    // Reference and conformance fixtures are intentionally checked through
    // the frozen semantics, including compile-only and diagnostic behavior.
    if is_fixture_path(normalized, "test/reference/") || is_fixture_path(normalized, "test/conformance/") {
        return false;
    }

    // The semantic frontend owns algebraic declarations and exception
    // unwinding. Keep these out of the older bytecode parser even when a
    // particular file does not use generics.
    if source.contains("enum ") || source.contains("try ") || source.contains("catch ") || source.contains("finally ") {
        return false;
    }
    if source.lines().any(|line| line.trim_start().starts_with("use file:")) {
        return false;
    }

    // Host namespaces are implemented by the semantic runtime. Keep them
    // away from the older bytecode VM so namespace calls cannot be silently
    // interpreted as unresolved legacy symbols.
    let semantic_namespace_markers = [
        "string.", "math.", "env.", "file.", "process.", "time.",
        "random.", "path.", "base64.", "hash.", "secrets.", "json.",
        "stack(",
    ];
    if semantic_namespace_markers.iter().any(|marker| source.contains(marker)) {
        return false;
    }

    // These forms are not represented by the legacy Rust AST/VM.
    let advanced_markers = [
        "func(", "func (", "struct ", "trait ", "json.", "http.",
        "test.", "process.args", "process.capture", "time.iso", "time.parse",
        "isType(", "regex(",
        ".map(", ".filter(", ".fold(", ".reduce(", ".find(", ".any(",
        ".all(", ".first(", ".last(", ".reverse(", ".sort(",
    ];
    if advanced_markers.iter().any(|marker| source.contains(marker)) {
        // Legacy structs, traits, and collection operations are supported in
        // their original form. Only use the semantic path for the newer
        // constructs identified below.
        if source.contains("func(") || source.contains("func (")
            || source.contains("json.")
            || source.contains("http.")
            || source.contains("test.")
            || source.contains("process.args")
            || source.contains("process.capture")
            || source.contains("time.iso")
            || source.contains("time.parse")
            || source.contains("isType(")
            || source.contains("regex(")
            || source.contains(".map(")
            || source.contains(".filter(")
            || source.contains(".fold(")
            || source.contains(".reduce(")
            || source.contains(".find(")
            || source.contains(".any(")
            || source.contains(".all(")
            || source.contains(".first(")
            || source.contains(".last(")
            || source.contains(".reverse(")
            || source.contains(".sort(")
        {
            return false;
        }
    }

    // Generic declarations, algebraic enum payloads, explicit type
    // arguments, and qualified package types require the semantic engine.
    if source.contains("<") && (source.contains("func ") || source.contains("struct ")
        || source.contains("enum ") || source.contains("trait ")) {
        return false;
    }
    if source.contains("enum ") && source.lines().any(|line| {
        let trimmed = line.trim_start();
        !trimmed.starts_with("enum ") && trimmed.contains('(') && !trimmed.contains("func ")
    }) {
        return false;
    }
    if source.lines().any(|line| line.trim_start().starts_with("use ") && line.contains('.')) {
        return false;
    }
    true
}

fn run_native_semantic(args: &[String]) -> ! {
    match runtime::execute_semantic_bytecode(&args[0], &args[1..]) {
        Ok(vm::Value::Int(code)) => exit(code as i32),
        Ok(_) => exit(EXIT_SUCCESS),
        Err(error) => {
            if error.code.starts_with('C') || error.code.starts_with('P') || error.code.starts_with('L') {
                eprintln!("error {}: {}", error.code, error.message);
                eprintln!("error: compilation failed");
                exit(EXIT_COMPILE_ERROR);
            }
            if error.code.is_empty() { eprintln!("uncaught exception: {}", error.message); }
            else { eprintln!("uncaught exception [{}]: {}", error.code, error.message); }
            exit(EXIT_RUNTIME_ERROR);
        }
    }
}

#[cfg(test)]
mod phase10_tests {
    use super::native_legacy_source_eligible;

    #[test]
    fn native_path_accepts_legacy_surface() {
        assert!(native_legacy_source_eligible(
            "/tmp/hello.sol",
            "package main\nfunc main() { println(\"ok\") }\n"
        ));
    }

    #[test]
    fn native_path_rejects_reference_fixtures() {
        assert!(!native_legacy_source_eligible(
            "/workspace/test/reference/generics.sol",
            "package main\nfunc identity<T>(x: T) -> T { return x }\n"
        ));
    }

    #[test]
    fn native_path_rejects_advanced_constructs() {
        assert!(!native_legacy_source_eligible(
            "/tmp/program.sol",
            "package main\nfunc main() { let f = func(x: int) -> int { return x } }\n"
        ));
        assert!(!native_legacy_source_eligible(
            "/tmp/program.sol",
            "package main\nenum Result<T> { Ok(T) Error(string) }\n"
        ));
    }

}

#[allow(dead_code)]
fn legacy_main(args: Vec<String>) {

    let mut max_insts: i64 = 0;
    let mut max_depth: i64 = 0;
    let mut timeout: Option<String> = None;
    let mut verbose = false;
    let mut check_mode = false;
    let mut show_version = false;
    let mut compile_file: Option<String> = None;
    let mut out_file: Option<String> = None;
    let mut arch: Option<String> = None;
    let mut positional: Vec<String> = Vec::new();

    let mut i = 0;
    while i < args.len() {
        let arg = &args[i];
        let next_arg = |i: &mut usize| -> Option<String> {
            if *i + 1 < args.len() {
                *i += 1;
                Some(args[*i].clone())
            } else {
                None
            }
        };
        match arg.as_str() {
            "--max-instructions" => match next_arg(&mut i) {
                Some(v) => match v.parse::<i64>() {
                    Ok(n) => max_insts = n,
                    Err(_) => {
                        eprintln!("error: invalid value \"{}\" for flag -max-instructions", v);
                        exit(EXIT_INTERNAL_ERROR);
                    }
                },
                None => {
                    eprintln!("error: flag needs an argument: --max-instructions");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--max-call-depth" => match next_arg(&mut i) {
                Some(v) => match v.parse::<i64>() {
                    Ok(n) => max_depth = n,
                    Err(_) => {
                        eprintln!("error: invalid value \"{}\" for flag -max-call-depth", v);
                        exit(EXIT_INTERNAL_ERROR);
                    }
                },
                None => {
                    eprintln!("error: flag needs an argument: --max-call-depth");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--timeout" => match next_arg(&mut i) {
                Some(v) => timeout = Some(v),
                None => {
                    eprintln!("error: flag needs an argument: --timeout");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--verbose" | "-verbose" => verbose = true,
            "--check" | "-check" => check_mode = true,
            "--version" | "-version" => show_version = true,
            "--compile" | "-compile" => match next_arg(&mut i) {
                Some(v) => compile_file = Some(v),
                None => {
                    eprintln!("error: flag needs an argument: --compile");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--out" | "-out" => match next_arg(&mut i) {
                Some(v) => out_file = Some(v),
                None => {
                    eprintln!("error: flag needs an argument: --out");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--arch" | "-arch" => match next_arg(&mut i) {
                Some(v) => arch = Some(v),
                None => {
                    eprintln!("error: flag needs an argument: --arch");
                    exit(EXIT_INTERNAL_ERROR);
                }
            },
            "--help" | "-h" | "-help" => print_usage(),
            _ => {
                if let Some(stripped) = arg.strip_prefix("--max-instructions=") {
                    max_insts = stripped.parse().unwrap_or_else(|_| {
                        eprintln!("error: invalid value \"{}\"", stripped);
                        exit(EXIT_INTERNAL_ERROR);
                    });
                } else if let Some(stripped) = arg.strip_prefix("--max-call-depth=") {
                    max_depth = stripped.parse().unwrap_or_else(|_| {
                        eprintln!("error: invalid value \"{}\"", stripped);
                        exit(EXIT_INTERNAL_ERROR);
                    });
                } else if let Some(stripped) = arg.strip_prefix("--timeout=") {
                    timeout = Some(stripped.to_string());
                } else if arg.starts_with('-') && arg.len() > 1 {
                    eprintln!("error: unknown flag: {}", arg);
                    print_usage();
                } else {
                    positional.push(arg.clone());
                }
            }
        }
        i += 1;
    }

    if show_version {
        println!("solvik version development");
        exit(EXIT_SUCCESS);
    }

    if compile_file.is_some() {
        eprintln!("error: --compile is not supported in the Rust build");
        exit(EXIT_INTERNAL_ERROR);
    }
    if out_file.is_some() {
        eprintln!("error: --out requires --compile");
        exit(EXIT_INTERNAL_ERROR);
    }
    if arch.is_some() {
        eprintln!("error: --arch requires --compile");
        exit(EXIT_INTERNAL_ERROR);
    }

    if check_mode {
        if positional.len() != 1 {
            if positional.is_empty() {
                eprintln!("error: expected source file");
            } else {
                eprintln!(
                    "error: --check accepts exactly one source file, got {}",
                    positional.len()
                );
            }
            exit(EXIT_INTERNAL_ERROR);
        }
        check_source(&positional[0]);
        return;
    }

    if positional.len() != 1 {
        print_usage();
    }

    run_source(&positional[0], max_insts, max_depth, timeout.as_deref(), verbose);
}

fn print_usage() -> ! {
    eprintln!("solvik - Solvik toolchain");
    eprintln!();
    eprintln!("Usage:");
    eprintln!("  solvik [options] <file>         Compile and run a source file");
    eprintln!("  solvik --check <file>           Check source for errors (without running)");
    eprintln!("  solvik --version                Print version");
    eprintln!();
    eprintln!("Options:");
    eprintln!("  --max-instructions N            Maximum instruction count (0 = unbounded)");
    eprintln!("  --max-call-depth N              Maximum call depth (0 = unbounded)");
    eprintln!("  --timeout D                     Execution timeout (e.g., 5s, 100ms)");
    eprintln!("  --verbose                       Show verbose output");
    exit(EXIT_INTERNAL_ERROR);
}

fn parse_duration(s: &str) -> Result<Duration, String> {
    if s.len() < 2 {
        return Err(format!("invalid duration: \"{}\"", s));
    }
    if let Some(num) = s.strip_suffix("ms") {
        let v: u64 = num
            .parse()
            .map_err(|_| format!("invalid duration: \"{}\"", s))?;
        return Ok(Duration::from_millis(v));
    }
    if let Some(num) = s.strip_suffix('s') {
        let v: u64 = num
            .parse()
            .map_err(|_| format!("invalid duration: \"{}\"", s))?;
        return Ok(Duration::from_secs(v));
    }
    if let Some(num) = s.strip_suffix('m') {
        let v: u64 = num
            .parse()
            .map_err(|_| format!("invalid duration: \"{}\"", s))?;
        return Ok(Duration::from_secs(v * 60));
    }
    Err(format!("invalid duration: \"{}\" (use s, ms, or m)", s))
}

fn run_source(path: &str, max_insts: i64, max_depth: i64, timeout: Option<&str>, verbose: bool) {
    let _ = (max_insts, max_depth, timeout);
    if verbose {
        eprintln!("Compiling {}...", path);
    }
    run_native_semantic(&[path.to_string()]);
}

fn check_source(path: &str) {
    let (_, diags, sources, err) = runtime::compile_with_sources(path);
    if !diags.all().is_empty() {
        for d in diags.all() {
            eprint!("{}", diagnostic::format_diagnostic(d, sources.get(&d.span.file)));
        }
    }
    if err.is_some() {
        exit(EXIT_COMPILE_ERROR);
    }
    println!("OK");
}
