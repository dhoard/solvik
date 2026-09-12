//! End-to-end behavior of class-level static fields: per-class shared
//! storage, lazy initialization at first active use, GC-root retention,
//! and first-use failure on a failing initializer.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("static_fields.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

#[test]
fn statics_are_shared_across_instances_and_persist() {
    let code = run("package m\n\
         class Counter {\n\
             static mutable total: Long = 0\n\
             static limit: Long = 10\n\
             public static new(): Self { return Self {} }\n\
             public static tick(): Long {\n\
                 Self.total += 1\n\
                 if Self.total > Counter.limit { Counter.total = Counter.limit }\n\
                 return Self.total\n\
             }\n\
             public current(): Long { return Counter.total }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let a: Counter = Counter.new()\n\
                 let b: Counter = Counter.new()\n\
                 Counter.tick()\n\
                 Counter.tick()\n\
                 // Both instances observe the same shared slot.\n\
                 if a.current() != b.current() { throw Exception.new(\"not shared\") }\n\
                 // A fresh instance still sees the persisted value.\n\
                 return Counter.new().current()\n\
             }\n\
         }\n");
    assert_eq!(code, 2);
}

#[test]
fn immutable_statics_reject_runtime_mutation_at_compile_time() {
    // `Counter.limit` is immutable; assigning to it must not compile.
    let src = "package m\n\
        class Counter {\n\
            static limit: Long = 10\n\
            public static new(): Self { return Self {} }\n\
            public static bump(): Long { Counter.limit = 1; return 0 }\n\
        }\n\
        class Main { public static run(args: String...): Long { return 0 } }\n";
    assert!(solvik_rs::compile("t.sol", src).is_err());
}

#[test]
fn initializers_follow_active_use_dependency_chain() {
    // B's initializer calls A.bump(), which reads and writes A.n. That is
    // only well-defined if A's own initializer already ran (A.n == 0):
    // B's first active use initializes B, whose field initializer actively
    // uses A and therefore initializes A first.
    let code = run("package m\n\
         class A {\n\
             static mutable n: Long = 0\n\
             public static bump(): Long { A.n += 1; return A.n }\n\
         }\n\
         class B {\n\
             static v: Long = A.bump()\n\
             public static get(): Long { return B.v }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 // B.v == 1 proves A initialized first; A.n == 1 proves the\n\
                 // mutation happened during B's initialization.\n\
                 return B.get() * 100 + A.bump() - 1\n\
             }\n\
         }\n");
    assert_eq!(code, 101);
}

#[test]
fn unused_failing_initializer_does_not_abort_startup() {
    // A is never actively used, so its failing initializer never runs and
    // startup succeeds.
    let module = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static bad: Long = A.boom()\n\
             public static boom(): Long { throw Exception.new(\"init failed\") }\n\
         }\n\
         class Main { public static run(args: String...): Long { return 0 } }\n",
    )
    .expect("compile");
    assert_eq!(
        solvik_rs::vm::Vm::run_main(module, vec![]).expect("unused class must not fail"),
        0
    );
}

#[test]
fn failing_initializer_fails_first_active_use() {
    // The error surfaces at the first active use of A, not at startup.
    let module = solvik_rs::compile(
        "t.sol",
        "package m\n\
         class A {\n\
             static bad: Long = A.boom()\n\
             public static boom(): Long { throw Exception.new(\"init failed\") }\n\
             public static get(): Long { return A.bad }\n\
         }\n\
         class Main { public static run(args: String...): Long { return A.get() } }\n",
    )
    .expect("compile");
    let err = solvik_rs::vm::Vm::run_main(module, vec![]).expect_err("first use must fail");
    assert!(err.message.contains("init failed"), "{}", err.message);
}

#[test]
fn statics_are_gc_roots() {
    // The list is reachable only from a static field. Holder initializes
    // lazily at its first active use; the allocation loop that follows
    // crosses the GC threshold, so an unrooted list would be collected and
    // the final read would fault or report a wrong size.
    let code = run(
        "package m\n\
         class Holder {\n\
             static items: List<String> = [\"a\", \"b\", \"c\"]\n\
             public static size(): Long { let items: List<String> = Holder.items; return items.size() }\n\
             public static first(): String { let items: List<String> = Holder.items; return items.get(0) }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 // First active use: initializes Holder before the loop.\n\
                 if Holder.size() != 3 { throw Exception.new(\"early read wrong\") }\n\
                 let mutable i: Long = 0\n\
                 while i < 20000 {\n\
                     let junk: List<Long> = [i, i + 1]\n\
                     i += 1\n\
                 }\n\
                 if Holder.first() != \"a\" { throw Exception.new(\"static was collected\") }\n\
                 return Holder.size()\n\
             }\n\
         }\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn store_static_value_survives_initializer_gc() {
    // The stored value is created inline and only becomes reachable from
    // the static slot once the store completes. Target initializes lazily
    // at its first active use, and its block allocates enough to cross the
    // GC threshold; the value and the stored object must survive because
    // static slots are GC roots.
    let code = run("package m\n\
         class Box {\n\
             v: Long\n\
             public static new(v: Long): Self { return Self { v: v } }\n\
             public v(): Long { return self.v }\n\
         }\n\
         class Target {\n\
             static mutable junk: List<List<Long>> = []\n\
             static mutable item: Box = Box.new(0)\n\
             static {\n\
                 let mutable i: Long = 0\n\
                 while i < 20000 {\n\
                     junk.add([i, i + 1])\n\
                     i += 1\n\
                 }\n\
             }\n\
             public static set(b: Box) { Self.item = b }\n\
             public static get(): Box { return Self.item }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 Target.set(Box.new(42))\n\
                 return Target.get().v()\n\
             }\n\
         }\n");
    assert_eq!(code, 42);
}

#[test]
fn statics_are_shared_across_threads() {
    let code = run("package m\n\
         class Worker implements Runnable {\n\
             public static new(): Self { return Self {} }\n\
             public run(): Void {\n\
                 Bank.deposit(5)\n\
             }\n\
         }\n\
         class Bank {\n\
             static mutable balance: Long = 0\n\
             static lock: Mutex = Mutex.new()\n\
             public static deposit(v: Long) {\n\
                 Self.lock.lock()\n\
                 Self.balance += v\n\
                 Self.lock.unlock()\n\
             }\n\
             public static total(): Long { return Self.balance }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let t1: Thread = Thread.new(Worker.new())\n\
                 let t2: Thread = Thread.new(Worker.new())\n\
                 t1.start()\n\
                 t2.start()\n\
                 t1.join()\n\
                 t2.join()\n\
                 return Bank.total()\n\
             }\n\
         }\n");
    assert_eq!(code, 10);
}
