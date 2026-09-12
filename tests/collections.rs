//! End-to-end behavior of the Java-shaped collection runtime: List/Map/Set/
//! Stack API shape, generic type safety, nullability, equality/hash
//! consistency, Set iteration, mutable-key rejection, concurrency, GC with
//! nested collections, and large workloads.
//!
//! Assertions follow the suite convention: programs throw on mismatch, so a
//! successful run exits 0 and any fault (including an assertion throw) exits
//! 2. Compile-time rejections are asserted through `compile` failures.

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("collections.sol", src).expect("compile");
    // Runtime faults and uncaught exceptions terminate with code 2.
    solvik_rs::vm::Vm::run_main(module, vec![]).unwrap_or(2)
}

fn main_class(body: &str) -> String {
    format!(
        "package m\n\
         class Main {{\n\
             public static run(args: String...): Long {{\n\
                 {body}\n\
                 return 0\n\
             }}\n\
         }}\n"
    )
}

// ---------------------------------------------------------------------------
// List API
// ---------------------------------------------------------------------------

#[test]
fn list_java_shaped_api() {
    let code = run(&main_class(
        "let l: List<Long> = List<Long>.withCapacity(8)\n\
         l.addAt(0, 1)\n\
         l.addAt(1, 3)\n\
         l.addAt(1, 2)\n\
         if l.size() != 3 { throw Exception.new(\"size\") }\n\
         let old: Long = l.set(0, 10)\n\
         if old != 1 { throw Exception.new(\"set old\") }\n\
         let gone: Long = l.remove(0)\n\
         if gone != 10 { throw Exception.new(\"remove old\") }\n\
         if l.removeValue(99) { throw Exception.new(\"removeValue absent\") }\n\
         if !l.removeValue(3) { throw Exception.new(\"removeValue present\") }\n\
         let rev: List<Long> = l.reversed()\n\
         if rev.size() != 1 || rev.get(0) != 2 { throw Exception.new(\"reversed\") }\n\
         // reversed() is a copy: mutating it leaves the original intact.\n\
         rev.add(77)\n\
         if l.size() != 1 { throw Exception.new(\"reversed aliasing\") }\n\
         let more: List<Long> = [7, 8]\n\
         l.addAll(more)\n\
         if l.size() != 3 || l.get(1) != 7 || l.get(2) != 8 { throw Exception.new(\"addAll\") }\n\
         if l.join(\",\") != \"2,7,8\" { throw Exception.new(\"join\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn list_remove_out_of_range_is_a_runtime_error() {
    let code = run(&main_class("let l: List<Long> = [1]\nl.remove(5)"));
    assert_eq!(code, 2);
}

// ---------------------------------------------------------------------------
// Map API
// ---------------------------------------------------------------------------

#[test]
fn map_java_shaped_api() {
    let code = run(&main_class(
        "let m: Map<String, Long> = Map<String, Long>.withCapacity(4)\n\
         let absent: Long? = m.get(\"nope\")\n\
         if absent != null { throw Exception.new(\"absent\") }\n\
         let prev: Long? = m.put(\"a\", 1)\n\
         if prev != null { throw Exception.new(\"prev null\") }\n\
         let prev2: Long? = m.put(\"a\", 2)\n\
         if prev2 != 1 { throw Exception.new(\"prev old\") }\n\
         if m.getOrDefault(\"a\", 42) != 2 { throw Exception.new(\"gda\") }\n\
         if m.getOrDefault(\"zz\", 42) != 42 { throw Exception.new(\"gdd\") }\n\
         if m.putIfAbsent(\"a\", 9) != null { throw Exception.new(\"pia existing\") }\n\
         if m.putIfAbsent(\"b\", 9) != null { throw Exception.new(\"pia absent\") }\n\
         if m.get(\"b\") != 9 { throw Exception.new(\"pia stored\") }\n\
         if m.replace(\"a\", 3) != 2 { throw Exception.new(\"replace old\") }\n\
         if m.replace(\"zz\", 3) != null { throw Exception.new(\"replace missing\") }\n\
         if !m.containsValue(3) { throw Exception.new(\"cv yes\") }\n\
         if m.containsValue(99) { throw Exception.new(\"cv no\") }\n\
         if m.removeMapping(\"a\", 99) { throw Exception.new(\"rm wrong\") }\n\
         if !m.removeMapping(\"a\", 3) { throw Exception.new(\"rm right\") }\n\
         let src: Map<String, Long> = { \"p\": 5, \"q\": 6 }\n\
         m.putAll(src)\n\
         if m.size() != 3 { throw Exception.new(\"putAll size\") }\n\
         if m.remove(\"p\") != 5 { throw Exception.new(\"remove prev\") }\n\
         if m.remove(\"p\") != null { throw Exception.new(\"remove twice\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn map_null_values_are_rejected_and_containskey_distinguishes() {
    // Value slots are non-nullable: storing an explicit null is a compile
    // error, and containsKey is how absence is observed.
    let src = &main_class("let m: Map<String, Long> = {}\nm.put(\"a\", null)");
    assert!(solvik_rs::compile("t.sol", src).is_err());
    let code = run(&main_class(
        "let m: Map<String, Long> = Map.new()\n\
         if m.containsKey(\"a\") { throw Exception.new(\"absent key\") }\n\
         if m.get(\"a\") != null { throw Exception.new(\"absent get\") }\n\
         m.put(\"a\", 1)\n\
         if !m.containsKey(\"a\") { throw Exception.new(\"has key\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn map_get_result_is_nullable_typed() {
    // Assigning Map.get to a non-nullable reference is a compile error.
    let src = &main_class("let m: Map<String, Long> = {}\nlet v: Long = m.get(\"a\")");
    assert!(solvik_rs::compile("t.sol", src).is_err());
    // Coalescing recovers a non-nullable value.
    let code = run(&main_class(
        "let m: Map<String, Long> = {}\nlet v: Long = m.get(\"a\") ?? 7\n\
         if v != 7 { throw Exception.new(\"coalesce\") }",
    ));
    assert_eq!(code, 0);
}

// ---------------------------------------------------------------------------
// Set API and iteration
// ---------------------------------------------------------------------------

#[test]
fn set_java_shaped_api() {
    let code = run(&main_class(
        "let s: Set<Long> = Set<Long>.withCapacity(4)\n\
         if !s.add(1) { throw Exception.new(\"add new\") }\n\
         if s.add(1) { throw Exception.new(\"add dup\") }\n\
         let t: Set<Long> = Set<Long>.new()\n\
         t.add(2)\n\
         t.add(3)\n\
         if !s.addAll(t) { throw Exception.new(\"addAll\") }\n\
         if !s.containsAll(t) { throw Exception.new(\"containsAll yes\") }\n\
         if !s.remove(3) { throw Exception.new(\"remove present\") }\n\
         if s.remove(3) { throw Exception.new(\"remove absent\") }\n\
         if s.containsAll(t) { throw Exception.new(\"containsAll no\") }\n\
         let members: List<Long> = s.toList()\n\
         if members.size() != 2 { throw Exception.new(\"toList size\") }\n\
         if !members.contains(1) || !members.contains(2) {\n\
             throw Exception.new(\"toList contents\")\n\
         }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn set_for_in_visits_each_member_exactly_once() {
    // Order is unspecified; sum, visit count, and sorted join are not.
    let code = run(&main_class(
        "let s: Set<Long> = Set<Long>.new()\n\
         s.add(5)\n\
         s.add(1)\n\
         s.add(3)\n\
         s.add(1)\n\
         let mutable total: Long = 0\n\
         let mutable visits: Long = 0\n\
         let collected: List<Long> = List<Long>.new()\n\
         for v in s {\n\
             total += v\n\
             visits += 1\n\
             collected.add(v)\n\
         }\n\
         if total != 9 { throw Exception.new(\"sum\") }\n\
         if visits != 3 { throw Exception.new(\"visits\") }\n\
         collected.sort()\n\
         if collected.join(\",\") != \"1,3,5\" { throw Exception.new(\"members\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn set_for_in_over_strings_and_empty_sets() {
    let code = run(&main_class(
        "let words: Set<String> = Set<String>.new()\n\
         words.add(\"b\")\n\
         words.add(\"a\")\n\
         let mutable found: Long = 0\n\
         for w in words {\n\
             if w == \"a\" { found += 1 }\n\
         }\n\
         if found != 1 { throw Exception.new(\"string member\") }\n\
         let empty: Set<Long> = Set<Long>.new()\n\
         let mutable n: Long = 0\n\
         for v in empty {\n\
             n += 1\n\
         }\n\
         if n != 0 { throw Exception.new(\"empty iterates zero times\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn set_for_in_over_null_set_is_a_runtime_error() {
    let code = run(&main_class("let s: Set<Long>? = null\nfor v in s { }"));
    assert_eq!(code, 2);
}

// ---------------------------------------------------------------------------
// Stack API
// ---------------------------------------------------------------------------

#[test]
fn stack_deque_api() {
    let code = run(&main_class(
        "let dq: Stack<Long> = Stack<Long>.withCapacity(4)\n\
         dq.addFirst(1)\n\
         dq.addLast(3)\n\
         dq.push(2)\n\
         if dq.peekFirst() != 1 { throw Exception.new(\"peekFirst\") }\n\
         if dq.peekLast() != 2 { throw Exception.new(\"peekLast\") }\n\
         if dq.removeFirst() != 1 { throw Exception.new(\"removeFirst\") }\n\
         if dq.pop() != 2 { throw Exception.new(\"pop\") }\n\
         if dq.poll() != 3 { throw Exception.new(\"poll\") }\n\
         if dq.peek() != null { throw Exception.new(\"peek empty\") }\n\
         if dq.poll() != null { throw Exception.new(\"poll empty\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn stack_pop_on_empty_is_a_runtime_error() {
    let code = run(&main_class(
        "let s: Stack<Long> = Stack<Long>.new()\ns.pop()",
    ));
    assert_eq!(code, 2);
}

// ---------------------------------------------------------------------------
// Generic type safety
// ---------------------------------------------------------------------------

#[test]
fn collection_element_types_are_checked_at_call_sites() {
    let bad = |body: &str| {
        let src = main_class(body);
        assert!(
            solvik_rs::compile("t.sol", &src).is_err(),
            "expected compile error: {body}"
        );
    };
    bad("let l: List<String> = List<String>.new()\nl.add(42)");
    bad("let l: List<Long> = [1]\nlet s: String = l.get(0)");
    bad("let m: Map<String, Long> = {}\nm.put(\"a\", \"b\")");
    bad("let s: Set<Long> = Set<Long>.new()\ns.add(\"x\")");
    bad("let st: Stack<String> = Stack<String>.new()\nst.push(1)");
    bad("let l: List<Long> = [1]\nl.addAt(0, \"x\")");
    bad("let l: List<Long> = [1, 2]\nl.addAll([\"x\"])");
    bad("let s: Set<Long> = Set<Long>.new()\ns.addAll(Set<String>.new())");
    bad("let m: Map<String, Long> = {}\nm.putAll({\"a\": \"b\"})");
}

#[test]
fn nullable_type_arguments_are_rejected() {
    // The type model tracks element types without per-argument nullability,
    // so nullable type arguments are a compile error rather than silently
    // widening the element type.
    let bad = |body: &str| {
        let src = main_class(body);
        assert!(
            solvik_rs::compile("t.sol", &src).is_err(),
            "expected compile error: {body}"
        );
    };
    bad("let m: Map<String, Long?> = Map.new()");
    bad("let l: List<String?> = List.new()");
    bad("let s: Set<Long?> = Set.new()");
    bad("let st: Stack<String?> = Stack.new()");
}

#[test]
fn collection_constructor_inference_from_declared_type() {
    let code = run(&main_class(
        "let l: List<String> = List.new()\n\
         l.add(\"inferred\")\n\
         if l.get(0) != \"inferred\" { throw Exception.new(\"list infer\") }\n\
         let m: Map<String, Long> = Map.new()\n\
         m.put(\"k\", 1)\n\
         if m.get(\"k\") != 1 { throw Exception.new(\"map infer\") }\n\
         let s: Set<Long> = Set.new()\n\
         s.add(1)\n\
         if !s.contains(1) { throw Exception.new(\"set infer\") }\n\
         let st: Stack<Long> = Stack.new()\n\
         st.push(1)\n\
         if st.pop() != 1 { throw Exception.new(\"stack infer\") }",
    ));
    assert_eq!(code, 0);
}

// ---------------------------------------------------------------------------
// Equality and hashing
// ---------------------------------------------------------------------------

#[test]
fn cross_width_numeric_equality_has_compatible_hashes() {
    let code = run(&main_class(
        "let a: Object = 5\n\
         let b: Object = Long.from(5)\n\
         if !a.equals(b) { throw Exception.new(\"int long equals\") }\n\
         if a.hashCode() != b.hashCode() { throw Exception.new(\"int long hash\") }\n\
         let c: Object = 5.0\n\
         if !a.equals(c) { throw Exception.new(\"int double equals\") }\n\
         if a.hashCode() != c.hashCode() { throw Exception.new(\"int double hash\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn equal_collections_have_equal_hashes() {
    let code = run(&main_class(
        "let l1: List<Long> = [1, 2, 3]\n\
         let l2: List<Long> = [1, 2, 3]\n\
         if !l1.equals(l2) { throw Exception.new(\"list eq\") }\n\
         if l1.hashCode() != l2.hashCode() { throw Exception.new(\"list hash\") }\n\
         let s1: Set<Long> = Set<Long>.new()\n\
         s1.add(1)\n\
         s1.add(2)\n\
         let s2: Set<Long> = Set<Long>.new()\n\
         s2.add(2)\n\
         s2.add(1)\n\
         if !s1.equals(s2) { throw Exception.new(\"set eq\") }\n\
         if s1.hashCode() != s2.hashCode() { throw Exception.new(\"set hash\") }\n\
         let m1: Map<String, Long> = { \"a\": 1, \"b\": 2 }\n\
         let m2: Map<String, Long> = { \"b\": 2, \"a\": 1 }\n\
         if !m1.equals(m2) { throw Exception.new(\"map eq\") }\n\
         if m1.hashCode() != m2.hashCode() { throw Exception.new(\"map hash\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn cyclic_structures_do_not_hang_equality_or_hash() {
    // A list containing itself must not recurse unboundedly in equals or
    // hashCode.
    let code = run(&main_class(
        "let mutable l: List<Object> = List.new()\n\
         l.add(l)\n\
         let h: Long = l.hashCode()\n\
         let e: Boolean = l.equals(l)\n\
         if !e { throw Exception.new(\"self equals\") }\n\
         if h != l.hashCode() { throw Exception.new(\"stable hash\") }",
    ));
    assert_eq!(code, 0);
}

// ---------------------------------------------------------------------------
// Mutable keys
// ---------------------------------------------------------------------------

#[test]
fn mutable_values_are_rejected_as_map_keys_and_set_members() {
    // Collections carry identity-mutable state: using one as a key/member is
    // a runtime rejection, not a silent identity-based entry.
    let code = run(&main_class(
        "let k: List<Long> = [1]\n\
         let m: Map<List<Long>, Long> = Map.new()\n\
         m.put(k, 1)",
    ));
    assert_eq!(code, 2);
    let code = run(&main_class(
        "let k: List<Long> = [1]\n\
         let s: Set<List<Long>> = Set.new()\n\
         s.add(k)",
    ));
    assert_eq!(code, 2);
}

// ---------------------------------------------------------------------------
// Concurrency
// ---------------------------------------------------------------------------

#[test]
fn independent_collections_progress_concurrently() {
    let code = run("package m\n\
         class Worker implements Runnable {\n\
             list: List<Long>\n\
             map: Map<Long, Long>\n\
             public static new(list: List<Long>, map: Map<Long, Long>): Self {\n\
                 return Self { list: list, map: map, }\n\
             }\n\
             public run(): Void {\n\
                 let mutable i: Long = 0\n\
                 while i < 2000 {\n\
                     self.list.add(i)\n\
                     self.map.put(i, i * 2)\n\
                     i += 1\n\
                 }\n\
             }\n\
         }\n\
         class Main {\n\
             public static run(args: String...): Long {\n\
                 let la: List<Long> = List.new()\n\
                 let ma: Map<Long, Long> = Map.new()\n\
                 let lb: List<Long> = List.new()\n\
                 let mb: Map<Long, Long> = Map.new()\n\
                 let ta: Thread = Thread.new(Worker.new(la, ma))\n\
                 let tb: Thread = Thread.new(Worker.new(lb, mb))\n\
                 ta.start()\n\
                 tb.start()\n\
                 ta.join()\n\
                 tb.join()\n\
                 if la.size() != 2000 { throw Exception.new(\"la\") }\n\
                 if lb.size() != 2000 { throw Exception.new(\"lb\") }\n\
                 if ma.size() != 2000 { throw Exception.new(\"ma\") }\n\
                 if mb.size() != 2000 { throw Exception.new(\"mb\") }\n\
                 if ma.get(1999) != 3998 { throw Exception.new(\"ma value\") }\n\
                 return 0\n\
             }\n\
         }\n");
    assert_eq!(code, 0);
}

// ---------------------------------------------------------------------------
// GC and scale
// ---------------------------------------------------------------------------

#[test]
fn nested_collections_survive_gc() {
    // Build deep nesting, release intermediate roots, and verify the
    // retained structure is intact after allocation pressure.
    let code = run(&main_class(
        "let outer: Map<String, List<Map<String, Long>>> = Map.new()\n\
         let mutable i: Long = 0\n\
         while i < 50 {\n\
             let inner: Map<String, Long> = Map.new()\n\
             inner.put(\"v\", i)\n\
             let row: List<Map<String, Long>> = List.new()\n\
             row.add(inner)\n\
             outer.put(\"row\" .. i, row)\n\
             i += 1\n\
         }\n\
         // Allocation pressure so the collector runs.\n\
         let mutable j: Long = 0\n\
         while j < 20000 {\n\
             let tmp: List<Long> = [j]\n\
             j += 1\n\
         }\n\
         let last: List<Map<String, Long>>? = outer.get(\"row49\")\n\
         if last == null { throw Exception.new(\"row lost\") }\n\
         let cell: Map<String, Long> = last.get(0)\n\
         if (cell.get(\"v\") ?? -1) != 49 { throw Exception.new(\"nested lost\") }\n\
         if outer.size() != 50 { throw Exception.new(\"outer size\") }",
    ));
    assert_eq!(code, 0);
}

#[test]
fn large_collection_workloads_complete() {
    let code = run(&main_class(
        "let l: List<Long> = List.withCapacity(100000)\n\
         let mutable i: Long = 0\n\
         while i < 100000 {\n\
             l.add(i)\n\
             i += 1\n\
         }\n\
         let mutable sum: Long = 0\n\
         for v in l {\n\
             sum += v\n\
         }\n\
         if sum != 4999950000 { throw Exception.new(\"list sum\") }\n\
         let m: Map<Long, Long> = Map.withCapacity(10000)\n\
         let mutable k: Long = 0\n\
         while k < 10000 {\n\
             m.put(k, k)\n\
             k += 1\n\
         }\n\
         let mutable hits: Long = 0\n\
         k = 0\n\
         while k < 10000 {\n\
             if m.get(k) == k { hits += 1 }\n\
             k += 1\n\
         }\n\
         if hits != 10000 { throw Exception.new(\"map hits\") }\n\
         let s: Set<Long> = Set.withCapacity(10000)\n\
         k = 0\n\
         while k < 10000 {\n\
             s.add(k)\n\
             k += 1\n\
         }\n\
         if s.size() != 10000 { throw Exception.new(\"set size\") }\n\
         k = 0\n\
         while k < 10000 {\n\
             if !s.contains(k) { throw Exception.new(\"set contains\") }\n\
             k += 1\n\
         }",
    ));
    assert_eq!(code, 0);
}
