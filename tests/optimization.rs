use solvik_rs::{compile_with_optimization, vm::Vm};

#[test]
fn conformance_outputs_match_with_and_without_optimization() {
    use std::{
        fs,
        process::{Command, Stdio},
    };
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR"));
    let mut cases: Vec<_> = fs::read_dir(root.join("test/cases"))
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .filter(|path| path.is_dir())
        .collect();
    cases.sort();
    for case in cases {
        let run = |optimize: bool| {
            let mut command = Command::new(env!("CARGO_BIN_EXE_solvik"));
            command.current_dir(root).arg(case.join("main.sol"));
            for name in [
                "SOLVIK_NO_OPT",
                "SOLVIK_DUMP_IR",
                "SOLVIK_DUMP_BC",
                "SOLVIK_DEBUG",
            ] {
                command.env_remove(name);
            }
            if !optimize {
                command.env("SOLVIK_NO_OPT", "1");
            }
            if case.join("args.txt").exists() {
                command.args(fs::read_to_string(case.join("args.txt")).unwrap().lines());
            }
            if case.join("stdin.txt").exists() {
                command.stdin(fs::File::open(case.join("stdin.txt")).unwrap());
            } else {
                command.stdin(Stdio::null());
            }
            command.output().unwrap()
        };
        let plain = run(false);
        let optimized = run(true);
        assert_eq!(
            plain.status.code(),
            optimized.status.code(),
            "{}: exit",
            case.display()
        );
        assert_eq!(plain.stdout, optimized.stdout, "{}: stdout", case.display());
        assert_eq!(plain.stderr, optimized.stderr, "{}: stderr", case.display());
    }
}

#[test]
fn generated_constant_branches_preserve_values_and_overflow_errors() {
    for seed in 0..40 {
        let source = format!("package generated\nclass Main {{ public static run(args: String...): Long {{\nlet mutable n: Long = {seed}\nwhile n < 50 {{ if (2 + 3) * 4 == 20 {{ n += 1 }} else {{ n += 2 }} }}\nreturn n\n}} }}");
        for enabled in [false, true] {
            let module = compile_with_optimization("generated.sol", &source, enabled).unwrap();
            assert_eq!(Vm::run_main(module, vec![]).unwrap(), 50);
        }
    }
    for expression in ["9223372036854775807 + 1", "1 / 0", "1 % 0"] {
        let source = format!("package generated\nclass Main {{ public static run(args: String...): Long {{\nreturn {expression}\n}} }}");
        let run = |enabled| {
            let module = compile_with_optimization("generated.sol", &source, enabled).unwrap();
            let error = Vm::run_main(module, vec![]).unwrap_err();
            (error.message, error.location)
        };
        assert_eq!(run(false), run(true));
    }
}

#[test]
fn constant_control_flow_matches_unoptimized_execution() {
    for body in [
        "let mutable n: Long = 0\nif true { n = 3 } else { n = 9 }\nreturn n",
        "let mutable n: Long = 0\nwhile false { n += 1 }\nreturn n",
        "let mutable n: Long = 0\nwhile n < 5 { if true { n += 1 } }\nreturn n",
        "let a: Bool = false\nif a || true { return 7 }\nreturn 9",
        "let a: Bool = true\nif a && false { return 7 }\nreturn 9",
        "let x: Double = -0.0\nif 1.0 / x < 0.0 { return 1 }\nreturn 2",
    ] {
        let source = format!("package regression\nclass Main {{ public static run(args: String...): Long {{\n{body}\n}} }}");
        let run = |optimize| {
            let module = compile_with_optimization("regression.sol", &source, optimize)
                .unwrap_or_else(|e| panic!("optimize={optimize}: {body}\n{e}"));
            Vm::run_main(module, vec![]).unwrap()
        };
        assert_eq!(run(false), run(true), "{body}");
    }
}
