//! Diagnostic-code stability: distinct conditions must carry distinct
//! codes. Regression for the "missing return value" error sharing C141
//! with "'return' not allowed inside scope block".

fn compile_err(src: &str) -> String {
    match solvik_rs::compile("t.sol", src) {
        Ok(_) => panic!("expected compile failure, but it succeeded"),
        Err(e) => e.to_string(),
    }
}

#[test]
fn bare_return_in_value_method_uses_c149() {
    let src = "package m\nclass H {\npublic static f(): Long {\nreturn\n}\n}\nclass Main {\npublic static run(args: String...): Long { return H.f() }\n}\n";
    let err = compile_err(src);
    assert!(err.contains("C149"), "got: {err}");
    assert!(
        !err.contains("C141"),
        "C141 must stay scope-block-only: {err}"
    );
}

#[test]
fn return_in_scope_block_still_uses_c141() {
    let src = "package m\nclass Main {\npublic static run(args: String...): Long {\n{\nreturn 0\n}\nreturn 0\n}\n}\n";
    let err = compile_err(src);
    assert!(err.contains("C141"), "got: {err}");
}
