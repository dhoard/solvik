//! Solvik compiler and bytecode VM library.
//!
//! Pipeline:
//!
//! ```text
//! source -> lexer -> parser -> AST -> resolve -> type check -> IR
//!        -> bytecode compile -> encode/decode -> verify -> VM
//! ```

pub mod ast;
pub mod bytecode;
pub mod check;
pub mod compiler;
pub mod diagnostic;
pub mod disasm;
pub mod formatter;
pub mod ir;
pub mod lexer;
pub mod optimize;
pub mod parser;
pub mod resolve;
pub mod source;
pub mod stdlib;
pub mod types;
pub mod verifier;
pub mod vm;

use bytecode::CodeModule;
use diagnostic::Diagnostics;
use source::SourceManager;

/// Run the full compile pipeline on one source file:
/// lex -> parse -> resolve -> type check -> IR -> bytecode (with an
/// encode/decode round trip, exactly like a loaded `.solb` file) -> verify.
///
/// Returns the verified module ready for `vm::Vm::run_main`.
pub fn compile(name: &str, text: &str) -> Result<CodeModule, String> {
    compile_with_optimization(name, text, std::env::var_os("SOLVIK_NO_OPT").is_none())
}

/// Compile with an explicit optimizer setting, without changing process-wide
/// environment variables (useful for concurrent differential tests).
pub fn compile_with_optimization(
    name: &str,
    text: &str,
    optimize: bool,
) -> Result<CodeModule, String> {
    let mut sources = SourceManager::default();
    sources.add(name, text.to_string());
    let mut diags = Diagnostics::default();
    let tokens = lexer::Lexer::new(0, text).tokenize(&mut diags);
    let mut parser = parser::Parser::new(tokens);
    let program = parser.parse_program();
    diags.items.append(&mut parser.diags.items);
    if diags.has_errors() || program.is_none() {
        return Err(render(&diags, &sources));
    }
    let program = program.unwrap();

    let resolved = resolve::resolve_program(&program, &mut diags);
    if diags.has_errors() {
        return Err(render(&diags, &sources));
    }

    let mut checker = check::Checker::new(&resolved, &sources);
    checker.check_program();
    if checker.diags.has_errors() {
        return Err(render(&checker.diags, &sources));
    }

    // Peephole constant folding. `SOLVIK_NO_OPT=1` disables it so tests can
    // run the same program through both pipelines and compare results.
    if optimize {
        optimize::optimize(&mut checker.ir);
    }

    let module = compiler::compile_module(&checker.ir, &sources);
    let bytes = bytecode::encode::encode(&module);
    let mut module = bytecode::decode::decode(&bytes)
        .map_err(|e| format!("internal: bytecode decode failed: {e}"))?;

    // Verification also fills each function's max_stack, which the VM uses
    // for stack capacity reservation.
    let mut vdiags = Diagnostics::default();
    if !verifier::verify_with_max_stacks(&mut module, &mut vdiags) {
        return Err(render(&vdiags, &sources));
    }
    Ok(module)
}

fn render(diags: &Diagnostics, sources: &SourceManager) -> String {
    diags
        .items
        .iter()
        .map(|d| d.render(sources))
        .collect::<Vec<_>>()
        .join("\n")
}
