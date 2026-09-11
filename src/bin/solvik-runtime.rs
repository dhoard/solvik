//! Standalone Solvik runtime stub.
//!
//! This is the native runtime image that `solvik --package` uses as the base
//! of a self-contained executable. When executed, it locates its own
//! executable, reads the embedded Solvik bytecode payload through the
//! SOLVPKG footer, verifies it, and runs it with the existing VM.
//!
//! It contains no lexer, parser, resolver, type checker, optimizer, or
//! compiler — only bytecode decoding, verification, the VM, and the package
//! loader. See `PACKAGE.md` for the container format.

use std::process::exit;

use solvik_rs::{package, vm};

fn main() {
    let exe = match std::env::current_exe() {
        Ok(p) => p,
        Err(e) => {
            eprintln!("runtime error: cannot locate own executable: {e}");
            exit(2);
        }
    };

    // Footer validation, bounds checking, and SHA-256 integrity happen here;
    // bytecode decoding and verification happen next. Nothing executes until
    // both succeed.
    let payload = match package::read_embedded_bytecode(&exe) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("runtime error: {e}");
            exit(2);
        }
    };
    let module = match solvik_rs::load_verified_module(&payload) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("runtime error: {}", e.message());
            exit(2);
        }
    };

    let program_args: Vec<String> = std::env::args().skip(1).collect();
    match vm::Vm::run_main(module, program_args) {
        Ok(code) => exit(code as i32),
        Err(e) => {
            if let Some((file, line)) = e.location {
                eprintln!("runtime error at {}:{}: {}", file, line, e.message);
            } else {
                eprintln!("runtime error: {}", e.message);
            }
            exit(2);
        }
    }
}
