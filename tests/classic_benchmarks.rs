//! Correctness coverage for the classic benchmark programs under
//! `benches/programs/`.
//!
//! Each program is compiled once and executed once, and its result is
//! compared with the value established independently from the algorithm
//! (also recorded in the benchmark harness's `CLASSIC_EXPECTED` table). This
//! ensures the classic workloads keep exercising VM behavior even when the
//! benchmark executable is not run, and that a semantic regression is caught
//! as a test failure rather than only as a suspicious benchmark number.
//!
//! The problem sizes here are the same as the published benchmark sizes, so
//! the values stay directly comparable.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("classic.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, solvik_rs::vm::RunConfig::default()).expect("run")
}

#[test]
fn classic_benchmarks_return_expected_results() {
    let cases: &[(&str, &str, i64)] = &[
        (
            "classic_fibonacci",
            include_str!("../benches/programs/classic_fibonacci.sol"),
            75025,
        ),
        (
            "classic_tak",
            include_str!("../benches/programs/classic_tak.sol"),
            1,
        ),
        (
            "classic_sieve",
            include_str!("../benches/programs/classic_sieve.sol"),
            4203,
        ),
        (
            "classic_nqueens",
            include_str!("../benches/programs/classic_nqueens.sol"),
            92,
        ),
        (
            "classic_fannkuch",
            include_str!("../benches/programs/classic_fannkuch.sol"),
            16,
        ),
        (
            "classic_mandelbrot",
            include_str!("../benches/programs/classic_mandelbrot.sol"),
            123735,
        ),
        (
            "classic_spectralnorm",
            include_str!("../benches/programs/classic_spectralnorm.sol"),
            206950,
        ),
        (
            "classic_binarytrees",
            include_str!("../benches/programs/classic_binarytrees.sol"),
            114681,
        ),
    ];
    for (name, source, expected) in cases {
        let got = run(source);
        assert_eq!(got, *expected, "{name}: {got} != {expected}");
    }
}
