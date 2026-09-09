//! Solvik compiler and bytecode VM entry point.

use std::process::exit;

use solvik_rs::bytecode;
use solvik_rs::check;
use solvik_rs::compiler;
use solvik_rs::diagnostic;
use solvik_rs::disasm;
use solvik_rs::formatter;
use solvik_rs::lexer;
use solvik_rs::parser;
use solvik_rs::resolve;
use solvik_rs::source;
use solvik_rs::verifier;
use solvik_rs::vm;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args
        .first()
        .is_some_and(|a| a == "--version" || a == "-version")
    {
        println!("solvik 0.1.0");
        exit(0);
    }
    let mode = match args.first().map(String::as_str) {
        Some("--format") => Some("format"),
        Some("--check") => Some("check"),
        _ => None,
    };
    let file_index = usize::from(mode.is_some());
    let file = match args.get(file_index) {
        Some(f) => f.clone(),
        None => {
            eprintln!("error: a source file is required");
            eprintln!("usage: solvik [--check|--format] <filename> [args...]");
            exit(3);
        }
    };
    if let Some(mode_name) = mode {
        if args.len() != file_index + 1 {
            eprintln!("error: {} accepts exactly one filename", mode_name);
            exit(3);
        }
    }
    let text = match std::fs::read_to_string(&file) {
        Ok(t) => t,
        Err(e) => {
            eprintln!("error: cannot read {}: {}", file, e);
            exit(3);
        }
    };
    let mut sources = source::SourceManager::default();
    let fid = sources.add(&file, text.clone());
    let mut diags = diagnostic::Diagnostics::default();
    let tokens = lexer::Lexer::new(fid, &text).tokenize(&mut diags);
    let mut parser = parser::Parser::new(tokens);
    let program = parser.parse_program();
    diags.items.append(&mut parser.diags.items);
    if diags.report(&sources) || program.is_none() {
        exit(1);
    }
    let program = program.unwrap();

    if mode == Some("format") {
        print!("{}", formatter::format_source(&text));
        exit(0);
    }

    let resolved = resolve::resolve_program(&program, &mut diags);
    if diags.report(&sources) {
        exit(1);
    }
    let mut checker = check::Checker::new(&resolved, &sources);
    checker.check_program();
    if checker.diags.report(&sources) {
        exit(1);
    }

    // Peephole constant folding. SOLVIK_NO_OPT=1 disables it so tests can
    // run the same program through both pipelines and compare results.
    if std::env::var_os("SOLVIK_NO_OPT").is_none() {
        solvik_rs::optimize::optimize(&mut checker.ir);
    }

    if mode == Some("check") {
        exit(0);
    }

    if std::env::var("SOLVIK_DUMP_IR").is_ok() {
        for f in &checker.ir.functions {
            eprintln!(
                "== {} (locals={} ret={})",
                f.name, f.local_count, f.returns_value
            );
            for (i, ins) in f.instrs.iter().enumerate() {
                eprintln!("  {:4} {}", i, ins);
            }
        }
    }

    // IR -> bytecode.
    let module = compiler::compile_module(&checker.ir, &sources);

    // Round-trip through the binary format (the VM only ever sees decoded
    // modules, exactly like a loaded .solb file would be).
    let bytes = bytecode::encode::encode(&module);
    let mut module = match bytecode::decode::decode(&bytes) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("error: internal: bytecode decode failed: {}", e);
            exit(3);
        }
    };

    if std::env::var("SOLVIK_DUMP_BC").is_ok() {
        eprint!("{}", disasm::disassemble(&module));
    }

    // Verify before executing. Verification also fills each function's
    // max_stack, which the VM uses for stack capacity reservation.
    let mut vdiags = diagnostic::Diagnostics::default();
    if !verifier::verify_with_max_stacks(&mut module, &mut vdiags) {
        vdiags.report(&sources);
        exit(1);
    }

    if std::env::var("SOLVIK_DEBUG").is_ok() {
        eprintln!(
            "compiled module={} classes={} functions={} bytes={}",
            resolved.module,
            resolved.classes.len(),
            module.functions.len(),
            bytes.len()
        );
        for c in &module.classes {
            eprintln!(
                "DBG class {} parent={:?} vtable_names={:?} vtable={:?}",
                c.name, c.parent, c.vtable_names, c.vtable
            );
        }
        for (i, f) in module.functions.iter().enumerate() {
            eprintln!("DBG fn {} = {}", i, f.name);
        }
    }

    // Execute.
    let program_args: Vec<String> = args.iter().skip(1).cloned().collect();
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
