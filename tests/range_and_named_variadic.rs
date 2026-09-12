//! Regression tests for two call/iteration semantics defects:
//!
//! 1. An integer range (`a..b`) used outside a for-in iterator position
//!    (statement, declaration initializer, argument, ...) must be rejected
//!    by the checker with a clean diagnostic instead of corrupting the
//!    checker's stack model and surfacing as a confusing verifier error.
//!
//! 2. A named argument targeting a variadic parameter (`f(xs: [1, 2])`)
//!    must supply the variadic list instead of being silently dropped.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("t.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

fn compile_err(src: &str) -> String {
    match solvik_rs::compile("t.sol", src) {
        Ok(_) => panic!("expected compile failure, but it succeeded"),
        Err(e) => e.to_string(),
    }
}

// --- 1. integer ranges outside for-in --------------------------------------

#[test]
fn range_as_statement_is_rejected_with_clean_diagnostic() {
    let src = "package m\nclass Main {\npublic static run(args: String...): Long {\n1..5\nreturn 0\n}\n}\n";
    let err = compile_err(src);
    assert!(
        err.contains("C147"),
        "expected C147 range diagnostic, got: {err}"
    );
    assert!(
        !err.contains("V005"),
        "verifier error leaked through checker: {err}"
    );
}

#[test]
fn range_as_declaration_initializer_is_rejected() {
    let src = "package m\nclass Main {\npublic static run(args: String...): Long {\nlet r: Object = 1..5\nreturn 0\n}\n}\n";
    let err = compile_err(src);
    assert!(err.contains("C147"), "got: {err}");
    assert!(!err.contains("V005"), "got: {err}");
}

#[test]
fn range_as_call_argument_is_rejected() {
    let src = "package m\nclass Box {\nv: Object\npublic static new(v: Object): Self { return Self { v: v } }\n}\nclass Main {\npublic static run(args: String...): Long {\nlet b: Box = Box.new(1..5)\nreturn 0\n}\n}\n";
    let err = compile_err(src);
    assert!(err.contains("C147"), "got: {err}");
    assert!(!err.contains("V005"), "got: {err}");
}

#[test]
fn range_in_for_in_still_works() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable total: Long = 0\n\
         for i in 1..4 { total = total + i }\n\
         return total\n\
         }\n}\n",
    );
    assert_eq!(code, 6); // 1 + 2 + 3
}

#[test]
fn range_with_expression_bounds_in_for_in_still_works() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let lo: Long = 10\n\
         let hi: Long = 13\n\
         let mutable total: Long = 0\n\
         for i in lo..hi { total = total + i }\n\
         return total\n\
         }\n}\n",
    );
    assert_eq!(code, 33); // 10 + 11 + 12
}

#[test]
fn string_concat_with_dotdot_is_unaffected() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let s: String = \"ab\" .. \"cd\"\n\
         return s.length()\n\
         }\n}\n",
    );
    assert_eq!(code, 4);
}

// --- 2. named arguments for variadic parameters ----------------------------

const SUM_SRC: &str = "package m\nclass Main {\n\
     public static sum(xs: Long...): Long {\n\
         let mutable total: Long = 0\n\
         for x in xs { total = total + x }\n\
         return total\n\
     }\n";

#[test]
fn named_variadic_argument_supplies_the_list() {
    let code = run(&format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(xs: [1, 2, 3]) }}\n}}\n"
    ));
    assert_eq!(code, 6);
}

#[test]
fn named_variadic_argument_from_list_variable() {
    let code = run(&format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{\n\
         let l: List<Long> = [4, 5]\n\
         return Main.sum(xs: l)\n\
         }}\n}}\n"
    ));
    assert_eq!(code, 9);
}

#[test]
fn named_variadic_empty_list_matches_spread_semantics() {
    // An untyped empty list literal infers as List<Object>, so it does not
    // match Long... — exactly like the pre-existing spread form `...[]`.
    let named = format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(xs: []) }}\n}}\n"
    );
    let err = compile_err(&named);
    assert!(err.contains("C181"), "got: {err}");
    let spread = format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(...[]) }}\n}}\n"
    );
    let err = compile_err(&spread);
    assert!(err.contains("C181"), "got: {err}");
}

#[test]
fn positional_and_spread_variadic_baselines_unchanged() {
    let pos = run(&format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(1, 2, 3) }}\n}}\n"
    ));
    assert_eq!(pos, 6);
    let spread = run(&format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{\n\
         let l: List<Long> = [1, 2, 3]\n\
         return Main.sum(...l)\n\
         }}\n}}\n"
    ));
    assert_eq!(spread, 6);
}

#[test]
fn named_variadic_conflicting_with_positional_tail_is_rejected() {
    let src = format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(1, xs: [2]) }}\n}}\n"
    );
    let err = compile_err(&src);
    assert!(err.contains("C148"), "got: {err}");
}

#[test]
fn named_variadic_non_list_value_is_rejected() {
    let src = format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(xs: 5) }}\n}}\n"
    );
    let err = compile_err(&src);
    assert!(err.contains("C183"), "got: {err}");
}

#[test]
fn named_variadic_element_type_mismatch_is_rejected() {
    let src = format!(
        "{SUM_SRC}\npublic static run(args: String...): Long {{ return Main.sum(xs: [\"a\"]) }}\n}}\n"
    );
    let err = compile_err(&src);
    assert!(err.contains("C181"), "got: {err}");
}

#[test]
fn named_arguments_for_regular_parameters_still_work() {
    let code = run("package m\nclass Main {\n\
         public static add(a: Long, b: Long): Long { return a + b }\n\
         public static run(args: String...): Long { return Main.add(b: 2, a: 1) }\n\
         }\n");
    assert_eq!(code, 3);
}
