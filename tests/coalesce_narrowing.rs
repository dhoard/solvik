//! The `??` operator is a compile-time coercion keyed off the *declared*
//! type of its left operand. When a null-narrowing reduces the left operand
//! to `null?` (the `else` branch of an `!= null` test, or the `then` branch
//! of an `== null` test) the coercion must still recover the declared
//! non-nullable base rather than collapsing the result to `Object`.

use solvik_rs::compile;
use solvik_rs::vm::Vm;

fn compile_ok(src: &str) {
    match compile("t.sol", src) {
        Ok(_) => {}
        Err(e) => panic!("expected OK, got: {e:?}"),
    }
}

#[test]
fn coalesce_in_else_of_not_null() {
    // `m` is narrowed to `null?` in the else branch; `m ?? 42` must still
    // be `Long`, not `Object`.
    compile_ok(
        "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let m: Long? = null\n\
            if m != null {}\n\
            else { let r: Long = m ?? 42; System.out().println(r) }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn coalesce_in_else_of_null() {
    // The mirrored case: the `then` branch of `== null`.
    compile_ok(
        "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let m: Long? = null\n\
            if m == null { let r: Long = m ?? 42; System.out().println(r) }\n\
            else {}\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn coalesce_result_is_coerced_to_non_null_at_run_time() {
    // The narrowing must not change runtime behaviour: the coalesced value
    // is still 42 and the program returns 0.
    let src = "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let m: Long? = null\n\
            if m != null {}\n\
            else { let r: Long = m ?? 42; System.out().println(r) }\n\
            return 0\n\
        }\n\
    }\n";
    let module = compile("t.sol", src).unwrap();
    assert_eq!(Vm::run_main(module, vec![]).unwrap(), 0);
}

#[test]
fn coalesce_still_rejects_mismatched_sides() {
    // `??` between incompatible sides must still fall back to `Object`, which
    // cannot be assigned to a `Long` slot.
    compile_err(
        "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let r: Long = (1 < 2) ?? \"x\"\n\
            return 0\n\
        }\n\
    }\n",
    );
}

fn compile_err(src: &str) {
    match compile("t.sol", src) {
        Err(_) => {}
        Ok(_) => panic!("expected error, got Ok"),
    }
}

// `Boolean.from` follows the Java shape: its only accepted argument is a
// `String` (the runtime parses the canonical `"true"`/`"false"`). A numeric
// argument must be a *compile-time* error, never a program that compiles and
// then crashes at runtime with a cryptic "cannot convert to Boolean".

#[test]
fn boolean_from_rejects_numeric_at_compile_time() {
    // An unsuffixed integer literal is `Integer`; numeric arguments must not
    // convert to `Boolean`.
    compile_err(
        "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let d: Boolean = Boolean.from(0)\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn boolean_from_accepts_string_literal() {
    // A string argument compiles and the runtime parses it, so this program
    // must run (and the fix must not regress the string path).
    let src = "package t\nclass Main {\n\
        public static run(args: String...): Long {\n\
            System.out().println(Boolean.from(\"true\"))\n\
            System.out().println(Boolean.from(\"false\"))\n\
            return 0\n\
        }\n\
    }\n";
    let module = compile("t.sol", src).expect("compile");
    Vm::run_main(module, vec![]).expect("run");
}
