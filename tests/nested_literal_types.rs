//! Regression tests: nested list/map literals must inherit the expected
//! *element* type of the enclosing annotated literal, not the enclosing
//! literal's own expected type. Previously `let m: List<List<Long>> =
//! [[1, 2], [3]]` failed with spurious C214 errors, and empty or deeply
//! nested literals lost their element type entirely (C184/C214).

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

#[test]
fn nested_list_literal_inherits_element_type() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: List<List<Long>> = [[1, 2], [3]]\n\
         return m.get(0).size() + m.get(1).size()\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn empty_nested_list_literal_keeps_element_type() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let l: List<List<Long>> = [[]]\n\
         return l.get(0).size()\n\
         }\n}\n",
    );
    assert_eq!(code, 0);
}

#[test]
fn map_value_list_literal_inherits_value_type() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: Map<String, List<Long>> = {\"a\": [1, 2]}\n\
         return m.get(\"a\").size()\n\
         }\n}\n",
    );
    assert_eq!(code, 2);
}

#[test]
fn map_value_empty_list_literal_keeps_value_type() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: Map<String, List<Long>> = {\"a\": []}\n\
         return m.get(\"a\").size()\n\
         }\n}\n",
    );
    assert_eq!(code, 0);
}

#[test]
fn map_of_maps_literal_inherits_nested_types() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: Map<String, Map<String, Long>> = {\"a\": {\"b\": 1}}\n\
         return m.get(\"a\").get(\"b\")\n\
         }\n}\n",
    );
    assert_eq!(code, 1);
}

#[test]
fn triple_nested_list_literal() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let m: List<List<List<Long>>> = [[[1], [2]], [[3]]]\n\
         return m.get(0).get(1).get(0) + m.get(1).get(0).get(0)\n\
         }\n}\n",
    );
    assert_eq!(code, 5);
}

#[test]
fn unannotated_nested_literals_still_infer() {
    // Without a declared literal type (return position), inference is
    // unchanged: element lists unify to List<Long>.
    let code = run("package m\nclass Main {\n\
         public static make(): List<List<Long>> { return [[1, 2], [3]] }\n\
         public static run(args: String...): Long { return Main.make().size() }\n\
         }\n");
    assert_eq!(code, 2);
}

#[test]
fn mismatched_nested_element_still_rejected() {
    // A String element where Long is expected must still be an error.
    let src = "package m\nclass Main {\npublic static run(args: String...): Long {\n\
               let m: List<List<Long>> = [[1], [\"x\"]]\n\
               return 0\n\
               }\n}\n";
    let err = compile_err(src);
    assert!(err.contains("C214"), "got: {err}");
}
