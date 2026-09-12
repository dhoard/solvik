//! Regression tests: list-pattern bindings must carry the subject list's
//! element type, consistent with `check_pattern_test` (which already tests
//! nested patterns against the element type) and with top-level binds /
//! variant payload binds (which use the subject/payload type).

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("t.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

#[test]
fn list_pattern_bindings_carry_element_type() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let l: List<Long> = [1, 2, 3]\n\
         let r: Long = match l {\n\
             [a, b, c] => a + b + c\n\
             _ => -1\n\
         }\n\
         return r\n\
         }\n}\n",
    );
    assert_eq!(code, 6);
}

#[test]
fn list_pattern_string_element_bindings() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let s: List<String> = [\"ab\", \"c\"]\n\
         let r: Long = match s {\n\
             [x, y] => x.length() + y.length()\n\
             _ => -1\n\
         }\n\
         return r\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn nested_list_pattern_bindings_carry_element_types() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: List<List<Long>> = [[1, 2], [3]]\n\
         let r: Long = match m {\n\
             [[a, b], [c]] => a + b + c\n\
             _ => -1\n\
         }\n\
         return r\n\
         }\n}\n",
    );
    assert_eq!(code, 6);
}

#[test]
fn list_pattern_literal_arms_still_work() {
    // Guards the pre-existing behavior: literal elements test against the
    // element type; bindings in mixed arms keep working.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let l: List<Long> = [1, 2]\n\
         let r: Long = match l {\n\
             [1, x] => x * 10\n\
             _ => -1\n\
         }\n\
         return r\n\
         }\n}\n",
    );
    assert_eq!(code, 20);
}
