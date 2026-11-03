# Map enumeration (`keys()` / `values()`) and the trait-owner `$of` conversion

## The two packaged views

`solvik::collection::Map` is enumerated through two packaged native traits:
`keys() -> solvik::collection::Set<K> native "keySet"` and
`values() -> solvik::collection::Collection<V> native "values"`. Both publish the §7 iteration
protocol (`size`, `isEmpty`, `contains`, `iterator`), so `for key in map.keys()` binds `K` and
`for value in map.values()` binds `V`, nullable when the instantiation is.

They are **two** types, not one, because `JavaBinding.matches` requires the declared return type to
name the exact Java interface: `HashMap.keySet()` returns `java.util.Set` and `HashMap.values()` a
`java.util.Collection`. A single `Collection<T>` trait binds neither member (`SOL-T013` on the
one that returns the other interface). Both views are the map's live views, not copies, and a
`java.util.HashMap` has no defined order — tests must assert totals/membership, never an order.

Reachability is textual: `Map.sol` names `Set` and `Collection`, so any program that mentions `Map`
emits `solvik/collection/{Set,Collection}.java` (and `Iterator.java` through their `iterator()`
members). A program that mentions none of the three emits none of them.

## The defect this feature exposed

`JavaLowerer.traitAdapter` — the nested `$Adapter` of a generic native trait — emitted
`return this.delegate.call(...)` for every member, without the `$of` conversion that
`LANGUAGE.md` §11.1 requires of "a native member whose declared result is such a trait". Only the
native-*type* path (`nativeMember`) applied it. So a native trait member returning a generic native
trait (the way `Set.iterator()` returns `Iterator<T>`, or a user trait over `java.util.Set`) produced
Java that `javac` rejected: `java.util.Iterator<E> cannot be converted to
solvik.collection.Iterator<E>`. Nothing failed at check time, because the checker accepted the
shape; the failure surfaced as `generated Java did not compile` on both `run` and `check`.

The fix is one shared decision: `JavaLowerer.Unit.nativeResult(returned, javaReturn, invocation)`
returns the statement for both paths — `void` → statement, generic-native-trait result →
`Trait.$of(...)`, otherwise → plain return. Do not reintroduce a second copy of that choice; the
duplication is what let the two paths drift. `NativeBindingTest`
(`a_native_trait_member_converts_its_generic_trait_result`) pins the trait-owner case;
`MapEnumerationExecutionTest` compiles the shipped adapters under `-Xlint:all -Werror`.

## Lowering note

`for name in map.keys()` emits
`for (Iterator<String> $it1 = Solvik$Program.checkedResult(scores.keys()).iterator(); ...)`: the loop
operand is an ordinary native member call, so the §11.1 non-null guard (`checkedResult`, the
`SOL-R008` check for a parameterized result) wraps it. A `for value in items` over a `List` emits
`items.iterator()` with no guard, because the loop lowers the recorded `iterator()` call directly.
Both shapes are pinned in `GeneratedOutputOptimizationTest`.

## Deferred: entry-wise iteration

`entries()` and a `Map.Entry` value type are **not** implemented: the native signature matcher
rejects a return type with a nested generic argument (`entries() -> Entries<Entry<K,V>>
native "entrySet"` is `SOL-T013`), and `java.util.Map.Entry` is a nested interface
(`java.util.Map$Entry` is the only spelling that resolves). Entry-wise iteration needs generated
support code (a `$`-named adapter that pairs a key with its value) rather than a declaration; that
is its own design. `for entry in map` therefore stays `SOL-T001`, and a program reads a pair by
iterating `keys()` and calling `get(key)`.
