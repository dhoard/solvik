//! Scope block unit tests.

use solvik_rs::compile;

fn compile_ok(src: &str) {
    match compile("t.sol", src) {
        Ok(_) => {}
        Err(e) => panic!("expected OK, got: {e:?}"),
    }
}

fn compile_err(src: &str) {
    match compile("t.sol", src) {
        Err(_) => {}
        Ok(_) => panic!("expected error, got Ok"),
    }
}

#[test]
fn limits_lifetime() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { let x: Long = 5 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn shadowing() {
    // Shadowing a visible name is a compile error (C240), Java-style.
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let x: Long = 10\n\
            { let x: Long = 20 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn shadowing_different_type() {
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let x: Long = 10\n\
            { let x: Object = null }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn nested() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { { let z: Long = 3 } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn empty() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn multiple_vars() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            {\n\
                let x: Long = 1\n\
                let y: Long = 2\n\
                let z: Long = x + y\n\
            }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn mutable() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { let mutable x: Long = 0; x = x + 1 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn in_if() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            if (true) { { let x: Long = 5 } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn in_while() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            while (true) { { let x: Long = 5 } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn in_for() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            for i in 0..5 { { let x: Long = i } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn return_rejected() {
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { return 0 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn break_no_enclosing() {
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { break }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn continue_no_enclosing() {
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { continue }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn break_in_loop() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            while (true) { { break } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn continue_in_loop() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            while (true) { { continue } }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn after_expression() {
    compile_err(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            let x: Long = 5\n\
            { let y: Long = 10 }\n\
            return y\n\
        }\n\
    }\n",
    );
}

#[test]
fn only_decl() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { let x: Long = 5 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn expression_statement() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            { let x: Long = 5; x + 1 }\n\
            return 0\n\
        }\n\
    }\n",
    );
}

#[test]
fn mixed_content() {
    compile_ok(
        "package test\nclass Main {\n\
        public static run(args: String...): Long {\n\
            {\n\
                let x: Long = 5\n\
                let y: Long = 10\n\
                let mutable z: Long = x + y\n\
                z = z + 1\n\
                if (z > 0) {}\n\
                while (z > 0) { break }\n\
            }\n\
            return 0\n\
        }\n\
    }\n",
    );
}
