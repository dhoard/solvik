//! Solvik compiler and bytecode VM entry point.

mod ast;
mod bytecode;
mod check;
mod compiler;
mod diagnostic;
mod ir;
mod lexer;
mod parser;
mod resolve;
mod source;
mod stdlib;
mod types;
mod verifier;
mod vm;

use std::process::exit;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args
        .first()
        .is_some_and(|a| a == "--version" || a == "-version")
    {
        println!("solvik 0.1.0");
        exit(0);
    }
    let file = match args.first() {
        Some(f) => f.clone(),
        None => {
            eprintln!("error: a source file is required");
            exit(3);
        }
    };
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
    let resolved = resolve::resolve_program(&program, &mut diags);
    if diags.report(&sources) {
        exit(1);
    }
    let mut checker = check::Checker::new(&resolved, &sources);
    checker.check_program();
    if checker.diags.report(&sources) {
        exit(1);
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
    let module = match bytecode::decode::decode(&bytes) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("error: internal: bytecode decode failed: {}", e);
            exit(3);
        }
    };

    if std::env::var("SOLVIK_DUMP_BC").is_ok() {
        if let Some(e) = module.entry {
            let f = &module.functions[e as usize];
            eprintln!("BC {}:", f.name);
            let code = &f.code;
            for chunk in code.chunks(16) {
                let hex: Vec<String> = chunk.iter().map(|b| format!("{:02x}", b)).collect();
                eprintln!("  {}", hex.join(" "));
            }
        }
    }

    // Verify before executing.
    let mut vdiags = diagnostic::Diagnostics::default();
    if !verifier::verify(&module, &mut vdiags) {
        vdiags.report(&sources);
        exit(1);
    }

    if std::env::var("SOLVIK_DEBUG").is_ok() {
        eprintln!(
            "compiled package={} classes={} functions={} bytes={}",
            resolved.package,
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
