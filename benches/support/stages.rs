//! Per-stage compiler diagnostics; the regular harness measures total latency.
use solvik_rs::{
    bytecode, check, compiler, diagnostic::Diagnostics, lexer, optimize, parser, resolve,
    source::SourceManager, verifier,
};
use std::time::Instant;

pub fn report(name: &str, source: &str) {
    let mut samples = vec![Vec::new(); 7];
    for iteration in 0..11 {
        let mut sources = SourceManager::default();
        sources.add("stages.sol", source.into());
        let mut diags = Diagnostics::default();
        let mut previous = Instant::now();
        let mut record = |stage: usize| {
            let now = Instant::now();
            if iteration >= 2 {
                samples[stage].push(now.duration_since(previous).as_nanos());
            }
            previous = now;
        };
        let tokens = lexer::Lexer::new(0, source).tokenize(&mut diags);
        record(0);
        let mut parser = parser::Parser::new(tokens);
        let program = parser.parse_program().expect("parse");
        assert!(parser.diags.is_empty());
        record(1);
        let resolved = resolve::resolve_program(&program, &mut diags);
        assert!(diags.is_empty());
        record(2);
        let mut checker = check::Checker::new(&resolved, &sources);
        checker.check_program();
        assert!(checker.diags.is_empty());
        record(3);
        if std::env::var_os("SOLVIK_NO_OPT").is_none() {
            optimize::optimize(&mut checker.ir);
        }
        record(4);
        let module = compiler::compile_module(&checker.ir, &sources);
        record(5);
        let bytes = bytecode::encode::encode(&module);
        let module = bytecode::decode::decode(&bytes).expect("decode");
        assert!(verifier::verify(&module, &mut diags));
        record(6);
        std::hint::black_box(module);
    }
    for (stage, times) in [
        "lex",
        "parse",
        "resolve",
        "check+IR",
        "optimize",
        "lower",
        "roundtrip+verify",
    ]
    .into_iter()
    .zip(&mut samples)
    {
        times.sort_unstable();
        println!("{name} {stage} {}ns", times[times.len() / 2]);
    }
}

pub fn sizes(name: &str, module: &bytecode::CodeModule) {
    let mut instructions = 0;
    let mut code_bytes = 0;
    for f in &module.functions {
        code_bytes += f.code.len();
        let mut ip = 0;
        while ip < f.code.len() {
            let op = solvik_rs::ir::IrOp::from_code(f.code[ip]).expect("opcode");
            ip += 1
                + (0..op.operand_count())
                    .map(|i| op.operand_size(i))
                    .sum::<usize>();
            instructions += 1;
        }
    }
    let constant_bytes: usize = module
        .constants
        .iter()
        .map(|c| {
            use bytecode::ConstVal::*;
            match c {
                Null => 1,
                Bool(_) | Byte(_) => 2,
                Short(_) => 3,
                Integer(_) | Float(_) | Char(_) => 5,
                Long(_) | Double(_) => 9,
                BigInt(s) | BigDecimal(s) => 5 + s.len(),
                Str(s) => 5 + s.len(),
            }
        })
        .sum();
    let total = bytecode::encode::encode(module).len();
    println!("{name} instructions={instructions} code={code_bytes}B constants={} entries/{constant_bytes}B metadata={}B total={total}B", module.constants.len(), total - code_bytes - constant_bytes);
}
