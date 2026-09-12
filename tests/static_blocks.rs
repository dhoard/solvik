//! End-to-end behavior of class static blocks: single-block-per-class rule,
//! execution after all static field initializers and before `Main.run`,
//! cross-class ordering, local variables, mutation of mutable statics, and
//! startup failure on a throwing block.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("static_blocks.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

#[test]
fn block_runs_after_field_initializers_and_before_entry() {
    // The field initializer sets n to 1; the block doubles it; Main.run
    // observes 2. If the block ran before the initializer it would double
    // 0, and if it never ran the value would stay 1.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 1\n\
             static {\n\
                 A.n *= 2\n\
             }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    assert_eq!(code, 2);
}

#[test]
fn block_supports_locals_and_control_flow() {
    let code = run("package m\n\
         class Sum {\n\
             static mutable total: Long = 0\n\
             static {\n\
                 let mutable i: Long = 1\n\
                 while i <= 4 {\n\
                     if i % 2 == 0 { Self.total += i }\n\
                     i += 1\n\
                 }\n\
             }\n\
             public static get(): Long { return Sum.total }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return Sum.get() }\n\
         }\n");
    // 2 + 4 = 6
    assert_eq!(code, 6);
}

#[test]
fn block_runs_in_class_declaration_order_across_classes() {
    // B's block reads A's fully-initialized state: only well-defined if
    // A's initializers (and A's block) already ran.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static { A.n += 10 }\n\
             public static bump(): Long { A.n += 1; return A.n }\n\
         }\n\
         class B {\n\
             static mutable v: Long = A.bump()\n\
             static { B.v += 100 }\n\
             public static get(): Long { return B.v }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return B.get() }\n\
         }\n");
    // A.n: 0 -> 10 (block) -> 11 (B's initializer calls bump)
    // B.v: 11 -> 111 (B's block)
    assert_eq!(code, 111);
}

#[test]
fn bare_return_exits_block_early() {
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static {\n\
                 if Self.n == 0 { return }\n\
                 A.n = 100\n\
             }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    assert_eq!(code, 0);
}

#[test]
fn block_can_adjust_a_static_field() {
    let code = run("package m\n\
         class Flag {\n\
             static mutable on: Boolean = false\n\
             static { Flag.on = true }\n\
             public static get(): Long { if Flag.on { return 1 } else { return 0 } }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return Flag.get() }\n\
         }\n");
    assert_eq!(code, 1);
}

#[test]
fn block_resolves_static_members_by_bare_name() {
    // Inside the block, static fields and static methods of the declaring
    // class resolve without a class or Self qualifier; locals shadow
    // static fields.
    let code = run("package m\n\
         class A {\n\
             static mutable x: Long = 1\n\
             static y: Long = 2\n\
             public static double(v: Long): Long { return v * 2 }\n\
             static {\n\
                 let sum: Long = x + y\n\
                 x = double(sum)\n\
                 x += 10\n\
             }\n\
             public static get(): Long { return A.x }
         }
         class Main {
             public static run(args: String...): Long { return A.get() }
         }
");
    // (1 + 2) * 2 + 10 = 16
    assert_eq!(code, 16);
}

#[test]
fn bare_names_do_not_alias_statics_outside_the_block() {
    // The bare-name rule is limited to the static block: inside ordinary
    // methods a bare static field name is still an unknown variable.
    let src = "package m\n\
        class A {\n\
            static mutable x: Long = 1\n\
            public static get(): Long { return x }\n\
        }\n\
        class Main { public static run(args: String...): Long { return 0 } }\n";
    assert!(solvik_rs::compile("t.sol", src).is_err());
}

#[test]
fn block_supports_try_catch_and_loop_jumps() {
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static {\n\
                 let mutable i: Long = 0\n\
                 while true {\n\
                     i += 1\n\
                     if i == 3 { continue }\n\
                     if i > 5 { break }\n\
                     try { A.n += i } catch (e: Exception) { A.n = -1 }\n\
                 }\n\
             }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    // i visits 1, 2, 4, 5 (3 skipped): n = 1 + 2 + 4 + 5
    assert_eq!(code, 12);
}

#[test]
fn block_may_call_other_classes_qualified() {
    // Bare names cover only the declaring class; other classes keep their
    // qualified form inside the block.
    let code = run("package m\n\
         class Helper {\n\
             public static triple(v: Long): Long { return v * 3 }\n\
         }\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static { A.n = Helper.triple(7) }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    assert_eq!(code, 21);
}

#[test]
fn duplicate_static_block_is_a_compile_error() {
    let src = "package m\n\
        class A {\n\
            static { System.out().println(1) }\n\
            static { System.out().println(2) }\n\
        }\n\
        class Main { public static run(args: String...): Long { return 0 } }\n";
    assert!(solvik_rs::compile("t.sol", src).is_err());
}

#[test]
fn self_and_return_value_rejected_in_block() {
    let d1 = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             f: Long\n\
             static { self.f = 1 }\n\
         }\n\
         class Main { public static run(args: String...): Long { return 0 } }\n",
    );
    assert!(d1.is_err());
    let d2 = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static { return 1 }\n\
         }\n\
         class Main { public static run(args: String...): Long { return 0 } }\n",
    );
    assert!(d2.is_err());
}

#[test]
fn failing_block_aborts_startup() {
    let module = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static { throw Exception.new(\"boom\") }\n\
         }\n\
         class Main { public static run(args: String...): Long { return 0 } }\n",
    )
    .expect("compile");
    assert!(solvik_rs::vm::Vm::run_main(module, vec![]).is_err());
}
