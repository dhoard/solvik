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
