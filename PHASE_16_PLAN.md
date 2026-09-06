# Phase 16 — Type-associated functions (static methods)

Status: **complete.** Adds type-associated functions so values can be
created and obtained through the type name: `User.new("Doug")`,
`Point.zero()`, `File.open(path)`, `Duration.seconds(5)`,
`User.fromJson(json)`. Built-in type names follow
`NAMING_CONSISTENCY_PLAN.md`.

## 1. Design: one general mechanism, no constructor subsystem

Type-associated functions are **ordinary functions declared inside a struct
body with a `static` modifier**. They are not a separate constructor
subsystem: there is no `new Type(...)` operator, no implicit privileges for a
function named `new`, and no requirement that any type expose `new`. A struct
may expose `new()`, several factories, factories without `new`, or none at
all; a factory may return any type, including another type.

```solvik
struct User {
    pub name: String

    pub static func new(name: String) -> User {
        return User { name: name }
    }

    static func fromJson(text: String) -> User? {
        // private factory: same-package only
        return null
    }

    pub func greet() -> String {
        return "hi " .. name
    }
}

user: User = User.new("Doug")
origin: Point = Point.zero()
```

Why a modifier instead of an `impl Type { ... }` block: Solvik already has
one declaration site per type (the struct body), and method modifiers
(`pub`, `mut`) already stack before `func`. A marker of some kind is
unavoidable — the grammar must distinguish zero-receiver functions from
instance methods — and a modifier reuses the existing declaration grammar
rather than introducing a new syntactic category. This is the minimal
deviation from the current language shape.

### Resolved questions

1. **Syntax**: `pub static func name(params) -> Ret { body }` inside a
   struct body. `static` is a new keyword, accepted in the same modifier
   position as `mut`/`pub`. Order-insensitive among modifiers.
2. **Resolution of `Type.function(...)`**: the call is already parsed as an
   ordinary member call (`Name.member(args)`), including explicit type
   arguments (`Box<Int>.new(7)`). The validator resolves the object to a
   struct type, looks up the static method, checks arguments (C101), and
   infers the instantiated return type. At runtime, member access on a
   struct type value yields the associated function.
3. **Visibility**: identical to fields/methods — private statics are
   accessible within the declaring package; `pub` is required
   cross-package (C120 static, E070 runtime defense).
4. **Private member access**: yes. A static method of `T` may read/write
   `T`'s private fields and call its private methods, and may build
   literals with private fields — the same-type scope rule used by instance
   methods (the type's package equals the code's package).
5. **No `impl` block**: declarations live in the struct body, the existing
   construct.
6. **Generics**: `Box<Int>.new(7)` works through the existing explicit
   type-argument syntax; the static's signature is instantiated with the
   given arguments. Inside the body, the struct's type parameters are in
   scope.
7. **Primitives/built-ins**: unchanged. Built-in types keep their existing
   constructor conventions (`mutex()`, `stack()`, `Thread.start`,
   `int("456")`). They are not user structs, and adding `.new` aliases would
   create a second path to the same concept. Documented as intentional.
8. **Struct literals vs factories**: both remain. Literals are syntax for
   direct construction; factories are functions. No interaction or
   precedence between them.
9. **Traits**: trait names carry no functions; `Trait.name(...)` is a
   compile error (C102, value is not callable). Traits cannot declare
   static requirements (rejected, C125).
10. **Errors from creation functions**: ordinary catchable exceptions
    (`throw`/`try`/`catch`). Solvik has no `Result` type; a factory either
    returns a value or raises.
11. **Overloading**: not supported (Solvik has none). Two members of one
    struct sharing a name — static vs static or static vs instance — are a
    duplicate-name error (C109).
12. **Type/value name shadowing**: unchanged resolution order — a local
    binding named `User` shadows the type in expression position, exactly as
    today for enum/namespace names. Documented and tested.
13. **`new` stays an ordinary identifier**: no compiler or runtime special
    cases key off the name `new`.
14. **Inference**: Solvik declarations require annotations (`x = 5` is not
    a declaration). The validator infers the static call's return type so
    annotated assignments are checked (C119 on mismatch).

### Diagnostics

- **C125** (new): `static` misuse — a mutating static method
  (`mut static func` / `static mut func`) or a `static` method in a trait
  declaration.
- **C126** (new): unknown associated function on a known struct type
  (`Thing.new()` when `Thing` declares no `new`).
- Reused: C101 (argument count/type on the call), C102 (calling a non-callable
  type name, e.g. a trait), C109 (duplicate member names),
  C120/E070 (cross-package private access).

## 2. Implementation surface (per backend)

- Lexer: `static` keyword (Python `TK.STATIC`; Go `tkStatic`; Rust keyword
  match arm).
- Parser: struct-body modifier scan accepts `static`; trait bodies reject
  it (C125); `mut` + `static` together rejected (C125). AST: `static` flag
  on the function declaration.
- Validator: duplicate-member check counts statics; static method signature
  table; member-call resolution on struct type names (with explicit type
  arguments) returning the instantiated return type; unknown static member
  on a known struct type is C118; cross-package private static calls are
  C120.
- Runtime: member access on a struct type value returns the associated
  function (no receiver); private-field access from a static body passes the
  same-type check; calling a struct type value directly still raises the
  existing "structs use named-field literals" error.
- Backends: Python reference first, then Go `internal/reference` (parser,
  validator, bytecode VM, values), then Rust `semantic_*` modules.

## 3. Tests

- `test/reference/static_methods.sol` — `User.new("Doug")`, `Point.zero()`,
  `Duration.seconds(5)`, `User.fromJson(...)`, a factory returning another
  type, generic `Box<Int>.new(7)`, private static use within the package,
  shadowing (local binding hides the type), annotation mismatch stays a
  compile error via a separate invalid fixture.
- `test/reference/multipkg_*` extension or new fixture — `pub static`
  cross-package call; private static cross-package rejected (C120).
- `test/reference/invalid/`: static_mut (C125), trait_static (C125),
  static_duplicate (C109), static_missing_member (C126, `Thing.new()` with
  no `new`), static_bad_args (C101), static_private_cross_package (C120),
  trait_call (C102, `Trait.new()`).
- `example.sol` — new section demonstrating the convention.

## 4. Docs

`LANGUAGE.md` (new "Type-associated functions" section under structs),
`SEMANTICS.md` (semantics + C125 row), `PARITY.md` row, `README.md`,
Sublime grammar (keyword), `PHASE_STATUS.md`.

## 5. Validation gates

Same as Phases 14/15: `python3 tools/parity.py --reference-only`;
`./build.sh` full three-backend differential parity; Go race-detector sweep
of concurrency fixtures; `./benchmark.sh --runs 2`.

## 6. Deferred

Static requirements in traits, associated constants, overloading,
constructor-specific syntax (deriving/rejecting `new`), and built-in type
factory aliases.

## 7. Completion record

Implemented as specified, with two deliberate deviations from the draft test
list above:

- Duplicate member names use **C091** (the existing "duplicate struct field"
  code, extended to methods) rather than C109, which is reserved for
  top-level package names. The duplicate-method check now covers all struct
  methods, static or not.
- The `trait_call` (C102) fixture was dropped: trait names are not runtime
  values, so `Trait.new()` resolves through ordinary unknown-member rules
  rather than the static-call path.

Validation results (all green): `python3 tools/parity.py --reference-only`;
`./build.sh` full three-backend differential parity including the new
`test/reference/static_methods.sol`, extended `multipkg_app.sol`, six new
invalid fixtures, and the updated `example.sol`; Go race-detector sweep of
the concurrency fixtures; `./benchmark.sh --runs 2`.
