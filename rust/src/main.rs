//! solvik — command-line tool for the Solvik language (Rust build).
//!
//! Python is the tree-walking *semantic reference*: language changes are
//! developed and solidified there first, then ported to the two compliant
//! implementations, Go and Rust, which execute as bytecode VMs.
//!
//! The Rust interpreter compiles the semantic AST to bytecode
//! (`semantic_runtime`'s `BcCode`) and executes it on the native stack VM
//! (`bc_run`/`bc_call`); it never tree-walks user programs.

mod semantic_ast;
mod semantic_lexer;
mod semantic_parser;
mod semantic_runtime;
mod semantic_types;
mod semantic_validator;

use std::process::exit;

const EXIT_SUCCESS: i32 = 0;
const EXIT_COMPILE_ERROR: i32 = 1;
const EXIT_RUNTIME_ERROR: i32 = 2;
const EXIT_INTERNAL_ERROR: i32 = 3;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

    if args.is_empty() {
        eprintln!("error: a source file is required");
        exit(EXIT_INTERNAL_ERROR);
    }
    if args.iter().any(|arg| arg == "--version" || arg == "-version") {
        println!("solvik version development");
        exit(EXIT_SUCCESS);
    }
    if args.iter().any(|arg| arg == "--help" || arg == "-h" || arg == "-help") {
        eprintln!("Usage: solvik [--check] <file> [args...]");
        exit(EXIT_SUCCESS);
    }
    if args.iter().any(|arg| arg == "--compile" || arg == "--out" || arg == "--arch") {
        eprintln!("error: --compile is not supported in the Rust build");
        exit(EXIT_INTERNAL_ERROR);
    }

    let check = args[0] == "--check" || args[0] == "-check";
    let index = if check { 1 } else { 0 };
    let Some(file) = args.get(index) else {
        eprintln!("error: expected source file");
        exit(EXIT_INTERNAL_ERROR);
    };
    let program_args: Vec<String> = if check {
        Vec::new()
    } else {
        args[index + 1..].to_vec()
    };

    if check {
        check_file(file)
    } else {
        run_file(file, &program_args)
    }
}

fn check_file(path: &str) -> ! {
    match semantic_runtime::check_file(path) {
        Ok(()) => exit(EXIT_SUCCESS),
        Err(error) => {
            if error.code.is_empty() {
                eprintln!("error: {}", error.message);
            } else {
                eprintln!("error {}: {}", error.code, error.message);
            }
            eprintln!("error: compilation failed");
            exit(EXIT_COMPILE_ERROR);
        }
    }
}

fn run_file(path: &str, program_args: &[String]) -> ! {
    match semantic_runtime::run_file(path, program_args) {
        Ok(code) => exit(code),
        Err(error) => {
            if error.code.starts_with('C') || error.code.starts_with('P') || error.code.starts_with('L') {
                eprintln!("error {}: {}", error.code, error.message);
                eprintln!("error: compilation failed");
                exit(EXIT_COMPILE_ERROR);
            }
            if error.code.is_empty() {
                eprintln!("uncaught exception: {}", error.message);
            } else {
                eprintln!("uncaught exception [{}]: {}", error.code, error.message);
            }
            exit(EXIT_RUNTIME_ERROR);
        }
    }
}
