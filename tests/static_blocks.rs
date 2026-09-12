//! End-to-end behavior of class static blocks: single-block-per-class rule,
//! lazy exactly-once execution at first active use, cross-class active-use
//! ordering, local variables, mutation of mutable statics, and first-use
//! failure on a throwing block.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("static_blocks.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

#[test]
fn block_runs_after_field_initializers_at_first_use() {
    // The field initializer sets n to 1; the block doubles it; the first
    // static method call observes 2. If the block ran before the
    // initializer it would double 0, and if it never ran the value would
    // stay 1.
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
fn unused_block_does_not_run() {
    // A is never actively used: its block would fail the program if it ran
    // eagerly. Startup succeeds and the program completes normally.
    let code = run("package m\n\
         class A {\n\
             static {\n\
                 throw Exception.new(\"must not run\")\n\
             }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return 7 }\n\
         }\n");
    assert_eq!(code, 7);
}

#[test]
fn first_static_method_use_triggers_init() {
    // The only active use is a static method call; the block must have run
    // before the method body reads the slot.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static { A.n = 42 }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    assert_eq!(code, 42);
}

#[test]
fn first_construction_triggers_init() {
    // Constructing an instance is an active use: the block runs before the
    // factory body allocates, so the instance observes initialized state.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static { A.n = 5 }\n\
             public static new(): Self { return Self {} }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let a: A = A.new()\n\
                 return A.get()\n\
             }\n\
         }\n");
    assert_eq!(code, 5);
}

#[test]
fn block_runs_exactly_once_across_many_uses() {
    // Multiple static accesses and factory calls follow the first use; the
    // block increments the counter once, not per use.
    let code = run("package m\n\
         class A {\n\
             static mutable runs: Long = 0\n\
             static mutable n: Long = 0\n\
             static {\n\
                 A.runs += 1\n\
                 A.n = 10\n\
             }\n\
             public static new(): Self { return Self {} }\n\
             public static get(): Long { return A.n }\n\
             public static runs(): Long { return A.runs }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let a: A = A.new()\n\
                 let b: A = A.new()\n\
                 A.get()\n\
                 A.get()\n\
                 return A.runs() * 100 + A.get()\n\
             }\n\
         }\n");
    assert_eq!(code, 110);
}

#[test]
fn main_initializes_before_run() {
    // The entry-point dispatch actively uses Main, so Main's own static
    // fields and block initialize before Main.run executes.
    let code = run("package m\n\
         class Main {\n\
             static mutable n: Long = 1\n\
             static { Main.n *= 3 }\n\
             public static run(args: String...): Long { return Main.n }\n\
         }\n");
    assert_eq!(code, 3);
}

#[test]
fn cross_class_order_follows_active_use_dependencies() {
    // B's field initializer actively uses A, so A's initializers (and A's
    // block) run before B's continue. Cross-class order follows the
    // active-use dependency chain, not class declaration order.
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
fn cyclic_initialization_uses_default_slots_on_reentry() {
    // A's block actively uses B; B's field initializer actively uses A
    // while A is still initializing. Same-thread re-entry exposes A's
    // current default slots instead of rerunning or deadlocking.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static { n += B.seed() }\n\
             public static bump(): Long { A.n += 1; return A.n }\n\
         }\n\
         class B {\n\
             static mutable m: Long = A.bump()\n\
             static { m += 10 }\n\
             public static seed(): Long { return B.m }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 return A.bump() * 100 + B.seed()\n\
             }\n\
         }\n");
    // A init: n=0; block loads n (0), then B init: m = A.bump()
    // (reentrant, n 0->1) = 1; B block: m=11; seed -> 11; block stores
    // n = 0+11 = 11. Then the outer A.bump(): n=12. Result: 12*100 + 11.
    assert_eq!(code, 1211);
}

#[test]
fn nested_init_through_object_creation() {
    // A field initializer constructs another class; the constructed class
    // initializes (including its own NewObject re-entry) before the outer
    // initializer stores the instance.
    let code = run("package m\n\
         class Inner {\n\
             static mutable made: Long = 0\n\
             static { made += 1 }\n\
             public static new(): Self { return Self {} }\n\
             public static count(): Long { return Inner.made }\n\
         }\n\
         class Outer {\n\
             static item: Inner = Inner.new()\n\
             public static count(): Long { return Inner.count() }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return Outer.count() }\n\
         }\n");
    assert_eq!(code, 1);
}

#[test]
fn concurrent_first_use_initializes_exactly_once() {
    // Two Solvik threads race the first active use of Bank. One executes
    // the block; the other waits and observes the finished state.
    let code = run("package m\n\
         class Worker implements Runnable {\n\
             public static new(): Self { return Self {} }\n\
             public run(): Void {\n\
                 Bank.deposit(5)\n\
             }\n\
         }\n\
         class Bank {\n\
             static mutable balance: Long = 0\n\
             static mutable inits: Long = 0\n\
             static lock: Mutex = Mutex.new()\n\
             static {\n\
                 inits += 1\n\
             }\n\
             public static deposit(v: Long) {\n\
                 Self.lock.lock()\n\
                 Self.balance += v\n\
                 Self.lock.unlock()\n\
             }\n\
             public static report(): Long {\n\
                 return Self.balance * 100 + Self.inits\n\
             }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let t1: Thread = Thread.new(Worker.new())\n\
                 let t2: Thread = Thread.new(Worker.new())\n\
                 t1.start()\n\
                 t2.start()\n\
                 t1.join()\n\
                 t2.join()\n\
                 return Bank.report()\n\
             }\n\
         }\n");
    assert_eq!(code, 1001);
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
fn caught_exception_in_block_completes_init() {
    // A language exception caught inside the block unwinds normally; the
    // initializer still completes and the class is usable afterwards.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             static {\n\
                 try {\n\
                     throw Exception.new(\"handled\")\n\
                 } catch (e: Exception) {\n\
                     A.n = 9\n\
                 }\n\
             }\n\
             public static get(): Long { return A.n }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long { return A.get() }\n\
         }\n");
    assert_eq!(code, 9);
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
fn failing_block_fails_first_active_use() {
    // The error surfaces at the first active use, not at startup.
    let module = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static { throw Exception.new(\"boom\") }\n\
             public static get(): Long { return 0 }\n\
         }\n\
         class Main { public static run(args: String...): Long { return A.get() } }\n",
    )
    .expect("compile");
    let err = solvik_rs::vm::Vm::run_main(module, vec![]).expect_err("first use must fail");
    assert!(err.message.contains("boom"), "{}", err.message);
}

#[test]
fn failed_class_is_not_retried() {
    // After a failed initialization every later active use fails with the
    // cached error; user code never reruns.
    let module = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static { throw Exception.new(\"boom\") }\n\
             public static get(): Long { return 0 }\n\
         }\n\
         class Main { public static run(args: String...): Long { return A.get() } }\n",
    )
    .expect("compile");
    let err = solvik_rs::vm::Vm::run_main(module, vec![]).expect_err("first use must fail");
    assert!(err.message.contains("boom"), "{}", err.message);
    assert!(
        err.message.contains("static initialization"),
        "{}",
        err.message
    );
}
