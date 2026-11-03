# Type-body name resolution is indexed, not declaration ordered

Recorded 2026-11 while implementing `plans/type-body-name-visibility.design.md`.

## The invariant

A `type` body resolves names against the whole program. `TypeChecker.indexDeclarations`, called
from `run` between the type-name check and the per-type build loop, indexes every declared type's
syntax first: `methodOwners`, the methods grouped by name, the declared field names, and the type
parameters. It reads syntax only and reports no diagnostics, so it neither reorders nor duplicates
them. Each entry lands in the existing `userTypes` map, and `buildTypeInfo` overwrites it with the
same data plus inferred field types.

Consequences worth remembering before touching resolution:

- There is no second lookup table and no separate accessor. `userTypes` holds a provisional
  `TypeInfo` for every declared type from the index pass onward, so `userTypes.get(...)` never
  returns `null` for a declared type. Anyone adding a "built yet?" test must compare `info.fields()`
  against `info.declared()` (built view against syntax) rather than relying on a `null` entry.
- Provisional entries have an immutable empty `fields` map. Field types are inferred by the owning
  type's build; the index deliberately does not resolve `TypeRef`s, because that would emit
  diagnostics outside the owning type's build and duplicate them.
- Signatures resolve lazily from syntax (`parameterTypes`, `checkUserMethod`), which is why a field
  initializer may call a later type's function: only the *field type* table needs the owning type to
  be built.
- The one remaining order dependence is a field initializer reading another type's field. It is
  rejected by `TypeChecker.reportUnresolvedField` with a message naming the dependency, and
  `LANGUAGE.md` §8.1 records the limitation. Do not "fix" it by resolving field types at index time:
  see the design's D-03.
- A type's own static field stays readable from its own field initializer (`A.y` while `A` is
  built), because `TypeInfo.fields` is the live map during that build. Emitted Java keeps the
  qualified reference, so Java's own forward-reference rule decides the value.

## Tests and corpus

`check/DeclarationOrderTest` pins acceptance, the permuted-order equality of the inferred field
type, both negative diagnostics (`SOL-T009` for written arguments on a non-native type, `SOL-T001`
for a constructor-less type), the ordering message, and the preserved same-type read.
`transpile/TypeBodyVisibilityExecutionTest` runs the newly accepted shapes. `example.sol` holds a
top-level `Basket` with a `solvik::collection::List<Integer>` field, a `List<Integer>` parameter,
and field initializers for a module type and for the later `Seal`; `cli/ExampleCorpusTest` pins the
`type body: 3 2 root` line.

## Environment note

On this workstation `/usr/bin/java` (Ubuntu OpenJDK 25.0.4.1) cannot compile generated Java
(`internal error: ... release version 25 not supported`), while `/opt/java` (Corretto 25.0.3, the
`java` on `PATH` and what `mvnw` and `./solvik` use) works. Run probes through `./solvik`.
