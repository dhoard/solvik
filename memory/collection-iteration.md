# Collection iteration: a declared `iterator()` protocol

Recorded 2026-11 while implementing `plans/collection-iteration.design.md`
(`plans/collection-iteration.impl.md`). Verify against current source before relying on it.

## Module name

The packaged namespace is `solvik::collection` (singular), declared by
`src/main/resources/solvik/collection/*.sol` and emitted as the Java package `solvik.collection`.
It was renamed from `solvik::collections` with no compatibility alias (the loader has no alias or
retired-spelling mechanism); decision records under `plans/` written before the rename keep the old
spelling because they report observations made under it.

## The protocol

`for ... in` iterates an array by its own type, or any value whose type declares
`pub func iterator()` with no parameters and a return type that substitutes to
`solvik::collection::Iterator<X>`. `TypeChecker.ITERATOR_TYPE` names the trait;
`TypeChecker.iterationProtocol` is the single rule. The lookup reads only the operand type's own
declared members: it must be `pub`, instance, zero-parameter, and not a constructor. `Map`,
built-ins, `Any`, a nullable operand, a private/static/parameterized `iterator()`, and a different
return type are all rejected — the first three by the reworded `SOL-T001`, a nullable operand by
`SOL-T015` from `requireNonNull`. The element type is the *substituted* argument of the declared
return: `returnTypes` is preferred; a type declared later resolves through
`declaredReturnType(member, moduleOf(owner), parameters)`, the same pattern `validateTraitContracts`
already uses.

`TypeChecker.Result.iterationProtocolOf(forStmt)` records the substituted iterator type; the loop
name's type lives in the recorded `Binding`, so the lowerer reads both decisions instead of
re-deriving them.

## Lowering

An array keeps `JStmt.ForEach`. A collection loop becomes one `JStmt.For` (D-05, no new IR node):

```java
$loop0: for (Iterator<Integer> $it1 = items.iterator(); $it1.hasNext(); ) {
    Integer value = $it1.next();
    ...
}
```

The operand is lowered into the loop header (evaluated once), the element declaration is the body's
first statement (fresh immutable binding per iteration), and the label stays the loop's own. `$it`
and `$loop` share the per-unit `tempCount`, so they never collide, and `$` is not a Solvik
identifier. There is no per-element allocation and no support member.

## Generic native traits (the one design deviation)

`Iterator<T>` cannot extend `java.util.Iterator`: the Solvik member is `Boolean hasNext()` while the
Java method is `boolean hasNext()`, and a Java iterator object never implements a Solvik interface
either way. So a **generic** native trait (`JavaLowerer.adaptsNativeTrait`: `isTrait` +
`nativeClass` + non-empty `typeParameters`) is emitted as a Solvik-owned interface with

- `static <T> Iterator<T> $of(java.util.Iterator<T> delegate)`, and
- a nested `public final class $Adapter<T> implements Iterator<T>` that delegates each bound member.

A non-generic native trait (`Task` over `Runnable`) keeps extending its Java interface — that is what
lets a Solvik `Task` be passed to a JDK API. `JavaLowerer.nativeMember` converts a native member
whose declared result is an adapted trait with `Trait.$of(result)`, so the conversion happens once
at the declaration, not at every call site. The adapter lives in the reached trait file, not in a
separate support file: `ProgramLoader` reachability is textual, so a program that mentions `List`
also emits `solvik/collection/Iterator.java` (accepted consequence; a program with no collection
emits neither).

## Native return types with arguments

`JavaBinding.mapped` gained a `ParameterizedType` branch: the raw class maps through
`resolveJavaClass` (only a declared native type has a Solvik name) and the arguments map by the same
rule; anything unmappable yields no match (`SOL-T013`). Two Solvik declarations for the same Java
interface are ambiguous — `resolveNativeJavaClass` returns the first declared, and packaged types are
declared first — so a program cannot re-bind `java.util.Iterator` for itself.

`checkedNativeResult` wraps a non-null native result with `Solvik$Program.checked(...)`, which
returns `Object`; casting a `ParameterizedType` back is unchecked and breaks `-Xlint:all -Werror`.
A result with type arguments therefore uses the type-preserving
`Solvik$Program.checkedResult(...)` (same `SOL-R008` contract, no cast). Every other carrier keeps
the `checked` call site byte for byte, which is why there are two members.

## Surface and tests

`Iterator.sol` declares the trait; `List.sol`/`Stack.sol` each gained
`pub func iterator() -> Iterator<T>`; `Map` is unchanged. Tests:
`check/CollectionIterationTest`, `transpile/CollectionIterationExecutionTest` (compiles with
`-Xlint:all -Werror`, covers empty iteration, nullable elements, `break`/`continue`/`raise`,
evaluate-once, a user type, a type-parameter element, and the JDK fail-fast propagation),
`GeneratedOutputOptimizationTest` (loop shape, adapter placement, array shape unchanged),
`bind/NativeBindingTest` (parameterized return), `load/EmbeddedBindingsTest` (resource order and
trait shape). `example.sol` iterates a `List`, a `Stack`, a nullable-element list, and a user
`Countdown`; `cli/ExampleCorpusTest` pins `iteration: 15 ab 1` and `protocol: 5`.
