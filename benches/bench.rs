//! Solvik performance benchmark harness.
//!
//! Each workload is a complete Solvik program. It is compiled once (full
//! pipeline: lex/parse/resolve/check/IR/bytecode/verify) and then executed
//! repeatedly; the median and minimum execution times are reported.
//!
//! Run (release-quality build):
//!   cargo bench --release -- --filter <substring>
//!
//! Workloads are sized so a single run takes roughly 10-100 ms on a modern
//! CPU in release mode.

use std::time::Instant;

use solvik_rs::bytecode::decode::decode;
use solvik_rs::diagnostic::Diagnostics;
use solvik_rs::verifier;
use solvik_rs::vm::Vm;

#[path = "support/stages.rs"]
mod stages;

#[cfg(feature = "bench-alloc")]
#[path = "support/allocations.rs"]
mod allocations;
#[cfg(feature = "bench-alloc")]
#[global_allocator]
static ALLOCATOR: allocations::CountingAllocator = allocations::CountingAllocator;

struct Workload {
    name: &'static str,
    source: &'static str,
    iters: u32,
}

const INT_LOOP: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        mutable i: Long = 0
        mutable sum: Long = 0
        while i < 3000000 {
            sum += i * 3 - 1
            i += 1
        }
        return sum % 1000003
    }
}
"#;

const FLOAT_LOOP: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        mutable i: Long = 0
        mutable x: Double = 0.5
        while i < 3000000 {
            x = x * 1.0000001 + 0.0000001
            i += 1
        }
        return Long.from(x * 1000000.0)
    }
}
"#;

const LOCALS: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        mutable a: Long = 1
        mutable b: Long = 2
        mutable c: Long = 3
        mutable d: Long = 4
        mutable e: Long = 5
        mutable f: Long = 6
        mutable g: Long = 7
        mutable h: Long = 8
        mutable i: Long = 0
        while i < 3000000 {
            a = (b + c) % 1000003
            b = (c + d) % 1000003
            c = (d + e) % 1000003
            d = (e + f) % 1000003
            e = (f + g) % 1000003
            f = (g + h) % 1000003
            g = (h + a) % 1000003
            h = (a + i) % 1000003
            i += 1
        }
        return a + b + c + d + e + f + g + h
    }
}
"#;

const BRANCHING: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        mutable i: Long = 0
        mutable sum: Long = 0
        while i < 3000000 {
            if i % 3 == 0 {
                sum += i
            } else if i % 3 == 1 {
                sum -= i
            } else {
                sum += 1
            }
            i += 1
        }
        return sum % 1000003
    }
}
"#;

const CALLS: &str = r#"
module bench

class Calc {

    public static step(x: Long, y: Long): Long {
        return (x * 31 + y) % 1000003
    }
}

class Main {

    public static run(args: String...): Long {
        mutable s: Long = 1
        mutable i: Long = 0
        while i < 1000000 {
            s = Calc.step(s, i)
            i += 1
        }
        return s % 1000003
    }
}
"#;

const RECURSION: &str = r#"
module bench

class Rec {

    public static fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Rec.fib(n - 1) + Rec.fib(n - 2)
    }
}

class Main {

    public static run(args: String...): Long {
        mutable total: Long = 0
        mutable i: Long = 0
        while i < 200 {
            total += Rec.fib(20)
            i += 1
        }
        return total % 1000003
    }
}
"#;

const METHODS: &str = r#"
module bench

class Acc {

    mutable value: Long

    public static new(v: Long): Self {
        return Self { value: v, }
    }

    public bump(delta: Long): Long {
        self.value = (self.value * 31 + delta) % 1000003
        return self.value
    }
}

class Main {

    public static run(args: String...): Long {
        a: Acc = Acc.new(1)
        mutable i: Long = 0
        mutable s: Long = 0
        while i < 1000000 {
            s = a.bump(i)
            i += 1
        }
        return s % 1000003
    }
}
"#;

const INTERFACES: &str = r#"
module bench

interface Hasher {

    mix(x: Long): Long
}

class Mix implements Hasher {

    public static new(): Self {
        return Self {}
    }

    override public mix(x: Long): Long {
        return (x * 31 + 144269) % 1000003
    }
}

class Main {

    public static run(args: String...): Long {
        h: Hasher = Mix.new()
        mutable s: Long = 1
        mutable i: Long = 0
        while i < 1000000 {
            s = h.mix(s)
            i += 1
        }
        return s % 1000003
    }
}
"#;

const OBJECTS: &str = r#"
module bench

class Point {

    public mutable x: Long
    public mutable y: Long

    public static new(x: Long, y: Long): Self {
        return Self { x: x, y: y, }
    }
}

class Main {

    public static run(args: String...): Long {
        p: Point = Point.new(1, 2)
        mutable i: Long = 0
        while i < 3000000 {
            p.x = p.x + 1
            p.y = p.y + 2
            i += 1
        }
        return (p.x + p.y) % 1000003
    }
}
"#;

const STRINGS: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        mutable s: String = "hello"
        mutable t: String = "world"
        mutable i: Long = 0
        while i < 500000 {
            u: String = s .. t
            s = t
            t = u.substring(0, 10)
            if s.contains("ell") {
                i += 1
            } else {
                i += 2
            }
        }
        return s.length() % 1000003
    }
}
"#;

const COLLECTIONS: &str = r#"
module bench

class Main {

    public static run(args: String...): Long {
        xs: List<Long> = []
        mutable i: Long = 0
        while i < 200000 {
            xs.add(i)
            i += 1
        }
        mutable j: Long = 0
        mutable sum: Long = 0
        while j < 200000 {
            sum += xs.get(j)
            j += 1
        }
        return sum % 1000003
    }
}
"#;

const MIXED: &str = r#"
module bench

interface Worker {

    tick(state: Long): Long
}

class Engine implements Worker {

    mutable state: Long
    mutable label: String

    public static new(): Self {
        return Self { state: 1, label: "engine", }
    }

    override public tick(state: Long): Long {
        self.state = (state * 31 + self.state) % 1000003
        self.label = self.label.substring(0, 4) .. "x"
        return self.state
    }
}

class Main {

    public static run(args: String...): Long {
        w: Worker = Engine.new()
        log: List<Long> = []
        mutable s: Long = 7
        mutable i: Long = 0
        while i < 200000 {
            s = w.tick(s)
            if s % 2 == 0 {
                log.add(s)
            }
            i += 1
        }
        mutable total: Long = 0
        for v in log {
            total += v
        }
        return (total + log.size()) % 1000003
    }
}
"#;

const WORKLOADS: &[Workload] = &[
    Workload {
        name: "zero_calls",
        source: r#"
module bench
class Calc { public static one(): Long { return 1 } }
class Main {
    public static run(args: String...): Long {
        mutable n: Long = 0
        mutable total: Long = 0
        while n < 1000000 { total += Calc.one(); n += 1 }
        return total
    }
}
"#,
        iters: 15,
    },
    Workload {
        name: "maps",
        source: r#"
module bench
class Main {
    public static run(args: String...): Long {
        m: Map<Long, Long> = { 0: 1 }
        mutable n: Long = 1
        while n < 1000 { m.put(n, n); n += 1 }
        mutable total: Long = 0
        n = 0
        while n < 30000 {
            if m.containsKey(n % 1000) { total += 1 }
            n += 1
        }
        return total
    }
}
"#,
        iters: 15,
    },
    Workload {
        name: "int_loop",
        source: INT_LOOP,
        iters: 15,
    },
    Workload {
        name: "float_loop",
        source: FLOAT_LOOP,
        iters: 15,
    },
    Workload {
        name: "locals",
        source: LOCALS,
        iters: 15,
    },
    Workload {
        name: "branching",
        source: BRANCHING,
        iters: 15,
    },
    Workload {
        name: "calls",
        source: CALLS,
        iters: 15,
    },
    Workload {
        name: "recursion",
        source: RECURSION,
        iters: 10,
    },
    Workload {
        name: "methods",
        source: METHODS,
        iters: 15,
    },
    Workload {
        name: "interfaces",
        source: INTERFACES,
        iters: 15,
    },
    Workload {
        name: "objects",
        source: OBJECTS,
        iters: 15,
    },
    Workload {
        name: "strings",
        source: STRINGS,
        iters: 15,
    },
    Workload {
        name: "collections",
        source: COLLECTIONS,
        iters: 15,
    },
    Workload {
        name: "mixed",
        source: MIXED,
        iters: 15,
    },
];

/// Build a module whose single entry function is raw bytecode, for
/// instruction-level microbenchmarks.
fn micro_module(code: Vec<u8>, local_count: u16) -> solvik_rs::bytecode::CodeModule {
    solvik_rs::bytecode::CodeModule {
        version: solvik_rs::bytecode::CodeModule::FORMAT_VERSION,
        constants: vec![
            solvik_rs::bytecode::ConstVal::Long(0),
            solvik_rs::bytecode::ConstVal::Long(1),
            solvik_rs::bytecode::ConstVal::Long(3_000_000),
        ],
        functions: vec![solvik_rs::bytecode::CodeFunction {
            name: "Main.run".into(),
            params: vec!["args".into()],
            local_count,
            max_stack: 0,
            returns_value: false,
            code,
            line_map: vec![],
            source_file: 0,
        }],
        classes: vec![],
        interfaces: vec![],
        dyn_names: vec![],
        entry: Some(0),
        sources: vec!["micro.sol".into()],
    }
}

fn push_u16(v: &mut Vec<u8>, n: u16) {
    v.extend_from_slice(&n.to_le_bytes());
}

fn push_u32(v: &mut Vec<u8>, n: u32) {
    v.extend_from_slice(&n.to_le_bytes());
}

/// Microbenchmarks: raw bytecode loops measuring per-instruction cost.
/// Each returns (name, code, local_count, instructions executed per run).
/// All use jumped loops, like real compiled Solvik code.
fn micro_workloads() -> Vec<(&'static str, Vec<u8>, u16, u64)> {
    use solvik_rs::ir::IrOp;
    let mut out = Vec::new();
    let k: u64 = 3_000_000;

    // Nine instructions per iteration, plus setup and the final condition.
    {
        let mut code = Vec::new();
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 0);
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        let top = code.len() as u32;
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 2);
        code.push(IrOp::LtLong.code());
        let jif_pos = code.len() as u32;
        code.push(IrOp::JumpIfFalse.code());
        push_u32(&mut code, 0); // patched below
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 1);
        code.push(IrOp::AddLong.code());
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::Jump.code());
        push_u32(&mut code, top);
        code.push(IrOp::ReturnVoid.code());
        let end = (code.len() - 1) as u32;
        code[(jif_pos + 1) as usize..(jif_pos + 5) as usize].copy_from_slice(&end.to_le_bytes());
        out.push(("micro_branch", code, 3, k * 9 + 7));
    }
    // Thirteen instructions per iteration, plus setup and final condition.
    {
        let mut code = Vec::new();
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 0);
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 0);
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 2);
        let top = code.len() as u32;
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 2);
        code.push(IrOp::LtLong.code());
        let jif_pos = code.len() as u32;
        code.push(IrOp::JumpIfFalse.code());
        push_u32(&mut code, 0);
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 2);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 0);
        code.push(IrOp::AddLong.code());
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 2);
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 1);
        code.push(IrOp::AddLong.code());
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::Jump.code());
        push_u32(&mut code, top);
        code.push(IrOp::ReturnVoid.code());
        let end = (code.len() - 1) as u32;
        code[(jif_pos + 1) as usize..(jif_pos + 5) as usize].copy_from_slice(&end.to_le_bytes());
        out.push(("micro_arith", code, 3, k * 13 + 9));
    }
    // Eleven instructions per iteration; compare local and global loads.
    for (load, slot, name) in [
        (IrOp::LoadLocal, 2, "micro_loadstore"),
        (IrOp::LoadGlobal, 0, "micro_globals"),
    ] {
        let mut code = Vec::new();
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 0);
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        let top = code.len() as u32;
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 2);
        code.push(IrOp::LtLong.code());
        let jif_pos = code.len() as u32;
        code.push(IrOp::JumpIfFalse.code());
        push_u32(&mut code, 0);
        code.push(load.code());
        push_u16(&mut code, slot);
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 3);
        code.push(IrOp::LoadLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::LoadConst.code());
        push_u32(&mut code, 1);
        code.push(IrOp::AddLong.code());
        code.push(IrOp::StoreLocal.code());
        push_u16(&mut code, 1);
        code.push(IrOp::Jump.code());
        push_u32(&mut code, top);
        code.push(IrOp::ReturnVoid.code());
        let end = (code.len() - 1) as u32;
        code[(jif_pos + 1) as usize..(jif_pos + 5) as usize].copy_from_slice(&end.to_le_bytes());
        out.push((name, code, 4, k * 11 + 7));
    }
    out
}

fn bench_micro(name: &str, code: Vec<u8>, local_count: u16, instrs_per_run: u64) {
    // Execute directly (no serialize/verify round trip): the point is
    // per-instruction VM cost. Modules are built up front so construction
    // cost is excluded from the measurement.
    let mut modules: Vec<solvik_rs::bytecode::CodeModule> = (0..9)
        .map(|_| micro_module(code.clone(), local_count))
        .collect();
    let mut diags = Diagnostics::default();
    assert!(
        verifier::verify_with_max_stacks(&mut modules[0], &mut diags),
        "{name}: {:?}",
        diags.items
    );
    for i in 0..2 {
        solvik_rs::vm::Vm::run_main(modules.remove(i), vec![]).unwrap();
    }
    let mut times: Vec<u128> = Vec::with_capacity(7);
    for m in modules {
        let start = Instant::now();
        solvik_rs::vm::Vm::run_main(m, vec![]).unwrap();
        times.push(start.elapsed().as_nanos());
    }
    times.sort_unstable();
    let median = times[times.len() / 2];
    println!(
        "{:<16} {:>14} {:>14}",
        name,
        median,
        format!("{:.2}ns/instr", median as f64 / instrs_per_run as f64)
    );
}

/// Decode + verify + execute one module, like the CLI does.
fn run_once(bytes: &[u8]) -> i64 {
    let mut module = decode(bytes).expect("decode");
    let mut diags = Diagnostics::default();
    assert!(
        verifier::verify_with_max_stacks(&mut module, &mut diags),
        "verify failed"
    );
    Vm::run_main(module, vec![]).expect("run")
}

fn bench_workload(w: &Workload) -> (u128, u128, u128) {
    let bytes = match solvik_rs::compile("bench.sol", w.source) {
        Ok(m) => solvik_rs::bytecode::encode::encode(&m),
        Err(e) => panic!("{}: compile failed: {}", w.name, e),
    };
    // Warmup (also warms up OS page caches / branch predictors).
    let expected = run_once(&bytes);
    for _ in 0..2 {
        assert_eq!(run_once(&bytes), expected);
    }
    let mut times: Vec<u128> = Vec::with_capacity(w.iters as usize);
    for _ in 0..w.iters {
        let start = Instant::now();
        let code = run_once(&bytes);
        times.push(start.elapsed().as_nanos());
        std::hint::black_box(code);
        assert_eq!(code, expected, "{} changed result", w.name);
    }
    times.sort_unstable();
    (
        times[times.len() / 2],
        times[0],
        times.last().copied().unwrap(),
    )
}

/// Generate a Solvik program with `n_classes` helper classes plus a Main
/// that calls into a few of them. Used for compile-time benchmarking.
fn gen_compile_program(n_classes: usize) -> String {
    let mut s = String::from("module compilebench\n\n");
    for c in 0..n_classes {
        s.push_str(&format!("class C{} {{\n", c));
        for m in 0..4 {
            s.push_str(&format!(
                "    public static f{}(a: Long, b: Long): Long {{\n        return a + b + {}\n    }}\n",
                m,
                c * 10 + m
            ));
        }
        s.push_str("}\n\n");
    }
    s.push_str("class Main {\n    public static run(args: String...): Long {\n        mutable t: Long = 0\n");
    for c in 0..n_classes.min(64) {
        s.push_str(&format!("        t += C{}.f0(1, 2)\n", c));
    }
    s.push_str("        return t % 1000003\n    }\n}\n");
    s
}

/// Time the full compile pipeline (lex -> parse -> resolve -> check -> IR ->
/// optimize -> bytecode -> decode -> verify) and report module size.
fn bench_compile(name: &str, source: &str, iters: u32) {
    // Warmup.
    for _ in 0..2 {
        if solvik_rs::compile("cb.sol", source).is_err() {
            panic!("{}: compile failed", name);
        }
    }
    let mut times: Vec<u128> = Vec::with_capacity(iters as usize);
    let mut module = None;
    for _ in 0..iters {
        let start = Instant::now();
        let m = solvik_rs::compile("cb.sol", source).expect("compile");
        times.push(start.elapsed().as_nanos());
        module = Some(m);
    }
    times.sort_unstable();
    let m = module.unwrap();
    let code_bytes: usize = m.functions.iter().map(|f| f.code.len()).sum();
    println!(
        "{:<16} {:>14} {:>14}  code={}B consts={} fns={}",
        name,
        times[times.len() / 2],
        times[0],
        code_bytes,
        m.constants.len(),
        m.functions.len()
    );
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    // Accept either `bench <substring>` or `bench --filter <substring>`.
    // (cargo passes a bare `--bench` flag when invoking the target.)
    let filter = if args.get(1).is_some_and(|a| a == "--filter") {
        args.get(2).map(String::as_str)
    } else {
        args.iter()
            .skip(1)
            .find(|a| a.as_str() != "--bench")
            .map(String::as_str)
    };
    let micro_only = filter.is_some_and(|f| f.contains("micro"));
    if filter == Some("stages") {
        for (name, n) in [("tiny", 2), ("medium", 100), ("large", 1000)] {
            stages::report(name, &gen_compile_program(n));
        }
        return;
    }
    if filter == Some("sizes") {
        println!(
            "Value={}B CallFrame={}B",
            std::mem::size_of::<solvik_rs::vm::value::Value>(),
            std::mem::size_of::<solvik_rs::vm::frames::CallFrame>()
        );
        for w in WORKLOADS {
            stages::sizes(
                w.name,
                &solvik_rs::compile("bench.sol", w.source).expect("compile"),
            );
        }
        for (name, n) in [("tiny", 2), ("medium", 100), ("large", 1000)] {
            stages::sizes(
                name,
                &solvik_rs::compile("cb.sol", &gen_compile_program(n)).expect("compile"),
            );
        }
        return;
    }
    if filter == Some("verify") {
        // Verifier only: fresh verification and diagnostics over prepared,
        // already-decoded modules. Excludes module generation, parsing,
        // serialization, decoding, and execution.
        println!(
            "{:<16} {:>14} {:>14}  {:>20}",
            "module", "median(ns)", "min(ns)", "fns"
        );
        let mut prepared: Vec<(&'static str, solvik_rs::bytecode::CodeModule)> = vec![];
        for w in WORKLOADS {
            let m = solvik_rs::compile("bench.sol", w.source).expect("compile");
            prepared.push((w.name, m));
        }
        for (name, n) in [("tiny", 2), ("medium", 100), ("large", 1000)] {
            let m = solvik_rs::compile("cb.sol", &gen_compile_program(n)).expect("compile");
            prepared.push((name, m));
        }
        for (name, module) in prepared {
            let mut diags = Diagnostics::default();
            assert!(
                verifier::verify(&module, &mut diags),
                "{}: {:?}",
                name,
                diags.items
            );
            let mut times: Vec<u128> = Vec::with_capacity(21);
            for _ in 0..21 {
                let start = Instant::now();
                let mut diags = Diagnostics::default();
                assert!(verifier::verify(&module, &mut diags));
                times.push(start.elapsed().as_nanos());
            }
            std::hint::black_box(&module);
            times.sort_unstable();
            println!(
                "{:<16} {:>14} {:>14}  fns={}",
                name,
                times[times.len() / 2],
                times[0],
                module.functions.len()
            );
        }
        return;
    }
    if cfg!(feature = "bench-alloc") {
        #[cfg(feature = "bench-alloc")]
        {
            println!("workload allocations requested_bytes (includes realloc; not peak memory)");
            for w in WORKLOADS {
                if filter.is_some_and(|f| !w.name.contains(f)) {
                    continue;
                }
                let module = solvik_rs::compile("bench.sol", w.source).expect("compile");
                let bytes = solvik_rs::bytecode::encode::encode(&module);
                let (result, calls, bytes) = allocations::measure(|| run_once(&bytes));
                std::hint::black_box(result);
                println!("{} {} {}", w.name, calls, bytes);
            }
            if filter.is_none_or(|f| f.contains("compile")) {
                for (name, classes) in [
                    ("compile_tiny", 2),
                    ("compile_medium", 100),
                    ("compile_large", 1000),
                ] {
                    let source = gen_compile_program(classes);
                    let (result, calls, bytes) = allocations::measure(|| {
                        solvik_rs::compile("cb.sol", &source).expect("compile")
                    });
                    std::hint::black_box(result);
                    println!("{} {} {}", name, calls, bytes);
                }
            }
        }
        return;
    }
    if !micro_only && filter.is_none_or(|f| f.contains("compile")) {
        println!(
            "{:<16} {:>14} {:>14}  {:>28}",
            "workload", "median(ns)", "min(ns)", "module size"
        );
        bench_compile("compile_tiny", &gen_compile_program(2), 50);
        bench_compile("compile_medium", &gen_compile_program(100), 20);
        bench_compile("compile_large", &gen_compile_program(1000), 5);
    }
    if !micro_only {
        println!(
            "{:<16} {:>14} {:>14} {:>14}",
            "workload", "median(ns)", "min(ns)", "max(ns)"
        );
        for w in WORKLOADS {
            if filter.is_some_and(|f| !w.name.contains(f)) {
                continue;
            }
            let (median, min, max) = bench_workload(w);
            println!("{:<16} {:>14} {:>14} {:>14}", w.name, median, min, max);
        }
    }
    if filter.is_none_or(|f| f.contains("micro")) {
        println!("{:<16} {:>14} {:>14}", "microbench", "median(ns)", "rate");
        for (name, code, lc, n) in micro_workloads() {
            if filter.is_some_and(|f| f != "micro" && !name.contains(f)) {
                continue;
            }
            bench_micro(name, code, lc, n);
        }
    }
}
