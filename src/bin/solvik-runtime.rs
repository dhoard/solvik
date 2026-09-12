//! Standalone Solvik runtime stub.
//!
//! This is the native runtime image that `solvik --package` uses as the base
//! of a self-contained executable. When executed, it locates its own
//! executable, reads the embedded Solvik bytecode payload through the
//! SOLVPKG footer, verifies it, and runs it with the existing VM.
//!
//! Launch options are parsed here, at run time: leading `-Dkey=value` tokens
//! (and an optional `--` terminator) initialize the program property store;
//! every remaining value is a program argument delivered to
//! `Main.run(args)`. Properties are never embedded in the package payload,
//! so the same executable can be launched with different property values on
//! different runs.
//!
//! It contains no lexer, parser, resolver, type checker, optimizer, or
//! compiler — only bytecode decoding, verification, the VM, and the package
//! loader. See `PACKAGE.md` for the container format.

use std::process::exit;

use solvik_rs::{launch, package, vm};

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

    // Leading -Dkey=value launch options (and an optional -- terminator)
    // initialize the program property store; the remaining values alone are
    // program arguments for Main.run(args).
    let raw_args: Vec<String> = std::env::args().skip(1).collect();
    let (properties, program_args) = match launch::split_launch_options(&raw_args) {
        Ok(split) => split,
        Err(e) => {
            eprintln!("runtime error: {e}");
            exit(2);
        }
    };
    let config = vm::RunConfig {
        args: program_args,
        properties,
    };
    match vm::Vm::run_main(module, config) {
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
