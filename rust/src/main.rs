//! solvik — command-line tool for the solvik toolchain (Rust build).
//!
//! Port of cmd/solvik/main.go. `--compile` (standalone executable generation)
//! is not supported in the Rust build.

// This crate is a faithful 1:1 port of the Go implementation. Many items —
// struct fields, enum variants, and helper functions — mirror the Go API
// surface even though the CLI entry point exercises only a subset of it.
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
mod source;
mod symbol;
mod types;
mod verifier;
mod vm;

use runtime::format_stack_trace;
use std::process::exit;
use std::time::{Duration, Instant};

const EXIT_SUCCESS: i32 = 0;
const EXIT_COMPILE_ERROR: i32 = 1;
const EXIT_RUNTIME_ERROR: i32 = 2;
const EXIT_INTERNAL_ERROR: i32 = 3;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

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
    let deadline = timeout.map(|t| {
        let d = match parse_duration(t) {
            Ok(d) => d,
            Err(e) => {
                eprintln!("error: {}", e);
                exit(EXIT_INTERNAL_ERROR);
            }
        };
        Instant::now() + d
    });

    let mut limits = runtime::default_options_limits();
    limits.max_instructions = max_insts;
    limits.max_call_depth = max_depth.max(0) as usize;

    if verbose {
        eprintln!("Compiling {}...", path);
    }

    let (bc_prog, diags, sources, err) = runtime::compile_with_sources(path);
    if !diags.all().is_empty() {
        for d in diags.all() {
            eprint!("{}", diagnostic::format_diagnostic(d, sources.get(&d.span.file)));
        }
    }
    if let Some(err) = err {
        eprintln!("error: {}", err);
        exit(EXIT_COMPILE_ERROR);
    }

    let prog = bc_prog.unwrap();
    match runtime::execute(prog, limits, deadline) {
        Ok(val) => {
            if let vm::Value::Int(code) = val {
                if code != 0 {
                    exit(code as i32);
                }
            }
        }
        Err(e) => {
            eprintln!("error: {}", e.error_string());
            eprint!("{}", format_stack_trace(&e));
            exit(EXIT_RUNTIME_ERROR);
        }
    }
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
