# Built-in type naming consistency

Status: in progress. This is a separate change from Phase 14; threads,
mutexes, processes, and streams are not implemented by this work.

## Contract

All public type names use PascalCase: `Bool`, `Byte`, `Int`, `Float`, `Char`,
`String`, `List<T>`, `Map<K, V>`, `Stack<T>`, `Channel<T>`, `Any`, `Void`,
`Exception`, `Regex`, and `Func<..., ReturnType>`. `Void` remains restricted
to function return-type positions under existing rules. `Channel` remains
available until Phase 14 is implemented.

Declarations and literals remain lowercase (`func`, `mut`, `true`, `false`,
`null`). Functions, conversion functions, constructors, methods, and namespace
names remain unchanged (`int(value)`, `string(value)`, `string.split`,
`Peer.worker`). Built-in value types are constructed through a type-associated
`.new(...)` call rather than a bare lowercase function. No uppercase conversion aliases are added.
User-defined type spelling remains case-sensitive and is not rewritten.
Built-in names cannot be redeclared as types (C109) or shadowed by generic
type parameters (C099).

Old lowercase built-in type annotations are rejected with P123 and a suggested
replacement. Internal runtime type tags may remain lowercase; they are not
source-language aliases. Public type formatting and reflection use canonical
names; `typeOf` returns `Func` for callables and preserves declared user-type
case. `isType` compares these names exactly. `null` remains the name of the
null value, not a new instantiable type.

## Work

1. Update Python, Go, and Rust parsing, type formatting, and reflection while
   preserving execution semantics and lowercase callable names.
2. Migrate Solvik programs, bootstrap source handling, embedded test programs,
   documentation, and editor support. Update Phase 14 examples/signatures only.
3. Add cross-backend naming coverage and rejection checks. Run reference
   conformance, native build/test gates, and differential parity.
4. Record verification results and mark this plan complete only when passing.
