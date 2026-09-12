//! Solvik compiler and bytecode VM library.
//!
//! Pipeline:
//!
//! ```text
//! source -> lexer -> parser -> AST -> resolve -> type check -> IR
//!        -> bytecode compile -> encode/decode -> verify -> VM
//! ```

pub mod ast;
pub mod bignum;
pub mod bytecode;
pub mod check;
pub mod compiler;
pub mod diagnostic;
pub mod disasm;
pub mod formatter;
pub mod ir;
pub mod lexer;
pub mod optimize;
pub mod package;
pub mod parser;
pub mod resolve;
pub mod source;
pub mod stdlib;
pub mod types;
pub mod verifier;
pub mod vm;

use std::fmt;

use bytecode::CodeModule;
use diagnostic::{Diagnostics, Severity};
use source::SourceManager;

/// Failure of the compile pipeline.
#[derive(Debug)]
pub enum CompileError {
    /// Rendered diagnostics from the lex/parse/resolve/check/verify stages.
    Diagnostics(String),
    /// Internal failure (bytecode encode/decode round trip).
    Internal(String),
}

impl fmt::Display for CompileError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.message())
    }
}

impl std::error::Error for CompileError {}

impl CompileError {
    pub fn message(&self) -> &str {
        match self {
            CompileError::Diagnostics(m) | CompileError::Internal(m) => m,
        }
    }
}

/// The result of a successful compile: the verified module, its canonical
/// encoding, and any rendered non-fatal warnings.
pub struct Compiled {
    /// Verified module ready for `vm::Vm::run_main`.
    pub module: CodeModule,
    /// Canonical binary encoding of the verified module.
    pub bytes: Vec<u8>,
    /// Rendered warnings (non-fatal) from the compile pipeline.
    pub warnings: Vec<String>,
}

/// Run the full compile pipeline on one source file:
/// lex -> parse -> resolve -> type check -> IR -> bytecode (with an
/// encode/decode round trip, exactly like a loaded `.solb` file) -> verify.
///
/// Returns the verified module ready for `vm::Vm::run_main`.
pub fn compile(name: &str, text: &str) -> Result<CodeModule, CompileError> {
    Ok(compile_full(name, text, std::env::var_os("SOLVIK_NO_OPT").is_none())?.module)
}

/// Compile with an explicit optimizer setting, without changing process-wide
/// environment variables (useful for concurrent differential tests).
pub fn compile_with_optimization(
    name: &str,
    text: &str,
    optimize: bool,
) -> Result<CodeModule, CompileError> {
    Ok(compile_full(name, text, optimize)?.module)
}

/// Run the full compile pipeline and return the canonical binary encoding of
/// the *verified* module (verifier-populated metadata such as `max_stack` is
/// included). This is the exact byte stream that `--package` embeds and that
/// `load_verified_module` accepts.
pub fn compile_to_bytes(name: &str, text: &str, optimize: bool) -> Result<Vec<u8>, CompileError> {
    Ok(compile_full(name, text, optimize)?.bytes)
}

/// Run the full compile pipeline and return the verified module, its
/// canonical encoding, and rendered warnings. This is the entry point used
/// by the CLI so that non-fatal warnings are surfaced exactly as before.
pub fn compile_report(name: &str, text: &str, optimize: bool) -> Result<Compiled, CompileError> {
    compile_full(name, text, optimize)
}

/// Lex, parse, resolve, and type-check a source file without compiling or
/// executing it (`solvik --check`). Returns rendered warnings on success.
pub fn check_source(name: &str, text: &str) -> Result<Vec<String>, CompileError> {
    let mut sources = SourceManager::default();
    sources.add(name, text.to_string());
    let mut diags = Diagnostics::default();
    let tokens = lexer::Lexer::new(0, text).tokenize(&mut diags);
    let mut parser = parser::Parser::new(tokens);
    let program = parser.parse_program();
    diags.items.append(&mut parser.diags.items);
    if diags.has_errors() || program.is_none() {
        return Err(CompileError::Diagnostics(render(&diags, &sources)));
    }
    let program = program.unwrap();

    let resolved = resolve::resolve_program(&program, &mut diags);
    if diags.has_errors() {
        return Err(CompileError::Diagnostics(render(&diags, &sources)));
    }

    let mut checker = check::Checker::new(&resolved, &sources);
    checker.check_program();
    if checker.diags.has_errors() {
        return Err(CompileError::Diagnostics(render(&checker.diags, &sources)));
    }
    let mut warnings = collect_warnings(&diags, &sources);
    warnings.extend(collect_warnings(&checker.diags, &sources));
    Ok(warnings)
}

/// Decode and verify a Solvik bytecode payload (for example the embedded
/// payload of a packaged executable). The module is verified again even when
/// it was produced by this compiler: the hash proves integrity, not safety.
pub fn load_verified_module(bytes: &[u8]) -> Result<CodeModule, CompileError> {
    let module = decode_bytes(bytes)?;
    let (module, _warnings) = verify_decoded(module)?;
    Ok(module)
}

/// The single authoritative tail of the compile pipeline: encode a compiled
/// module, round-trip it through the binary format, verify the decoded
/// result, and re-encode the verified module. Returns the verified module
/// and its canonical encoding.
pub fn finalize_module(module: CodeModule) -> Result<(CodeModule, Vec<u8>), CompileError> {
    let (verified, _warnings) = verify_decoded(decode_bytes_round_trip(module)?)?;
    let bytes = bytecode::encode::encode(&verified);
    Ok((verified, bytes))
}

fn compile_full(name: &str, text: &str, optimize: bool) -> Result<Compiled, CompileError> {
    let mut sources = SourceManager::default();
    sources.add(name, text.to_string());
    let mut diags = Diagnostics::default();
    let tokens = lexer::Lexer::new(0, text).tokenize(&mut diags);
    let mut parser = parser::Parser::new(tokens);
    let program = parser.parse_program();
    diags.items.append(&mut parser.diags.items);
    if diags.has_errors() || program.is_none() {
        return Err(CompileError::Diagnostics(render(&diags, &sources)));
    }
    let program = program.unwrap();

    let resolved = resolve::resolve_program(&program, &mut diags);
    if diags.has_errors() {
        return Err(CompileError::Diagnostics(render(&diags, &sources)));
    }

    let mut checker = check::Checker::new(&resolved, &sources);
    checker.check_program();
    if checker.diags.has_errors() {
        return Err(CompileError::Diagnostics(render(&checker.diags, &sources)));
    }

    // Peephole constant folding. `SOLVIK_NO_OPT=1` disables it so tests can
    // run the same program through both pipelines and compare results.
    if optimize {
        optimize::optimize(&mut checker.ir);
    }

    let mut warnings = collect_warnings(&diags, &sources);
    warnings.extend(collect_warnings(&checker.diags, &sources));

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

    // IR -> bytecode, then the shared encode/decode/verify tail.
    let module = compiler::compile_module(&checker.ir, &sources);
    let decoded = decode_bytes_round_trip(module)?;
    let (module, verify_warnings) = verify_decoded(decoded)?;
    warnings.extend(verify_warnings);
    let bytes = bytecode::encode::encode(&module);

    if std::env::var("SOLVIK_DUMP_BC").is_ok() {
        eprint!("{}", disasm::disassemble(&module));
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
                "DBG class {} fields={} method_names={:?} method_table={:?} dyn={:?}",
                c.name, c.field_count, c.method_names, c.method_table, c.dyn_methods
            );
        }
        for (i, f) in module.functions.iter().enumerate() {
            eprintln!("DBG fn {} = {}", i, f.name);
        }
    }

    Ok(Compiled {
        module,
        bytes,
        warnings,
    })
}

fn decode_bytes(bytes: &[u8]) -> Result<CodeModule, CompileError> {
    bytecode::decode::decode(bytes)
        .map_err(|e| CompileError::Internal(format!("bytecode decode failed: {e}")))
}

fn decode_bytes_round_trip(module: CodeModule) -> Result<CodeModule, CompileError> {
    let bytes = bytecode::encode::encode(&module);
    decode_bytes(&bytes)
}

fn verify_decoded(mut module: CodeModule) -> Result<(CodeModule, Vec<String>), CompileError> {
    // Verification also fills each function's max_stack, which the VM uses
    // for stack capacity reservation.
    let mut vdiags = Diagnostics::default();
    if !verifier::verify_with_max_stacks(&mut module, &mut vdiags) {
        return Err(CompileError::Diagnostics(render(
            &vdiags,
            &sources_for(&module),
        )));
    }
    let warnings = collect_warnings(&vdiags, &sources_for(&module));
    Ok((module, warnings))
}

/// Rendered non-fatal diagnostics from a stage that succeeded.
fn collect_warnings(diags: &Diagnostics, sources: &SourceManager) -> Vec<String> {
    diags
        .items
        .iter()
        .filter(|d| d.severity == Severity::Warning)
        .map(|d| d.render(sources))
        .collect()
}

/// Rebuild a source manager from a decoded module's recorded source names so
/// verifier diagnostics can carry file:line:col locations.
fn sources_for(module: &CodeModule) -> SourceManager {
    let mut sm = SourceManager::default();
    for name in &module.sources {
        sm.add(name, String::new());
    }
    sm
}

fn render(diags: &Diagnostics, sources: &SourceManager) -> String {
    diags
        .items
        .iter()
        .map(|d| d.render(sources))
        .collect::<Vec<_>>()
        .join("\n")
}
