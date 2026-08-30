# Python-first semantic parity

`LANGUAGE.md` is the normative language contract and `solvik.py` is the
executable semantic reference. Go and Rust are optimized implementations.

The parity workflow is:

```text
LANGUAGE.md
    |
    v
solvik.py  (semantic oracle)
    |
    +--> test/conformance/*
    +--> test/reference/*
    |
    v
tools/parity.py
    |
    +--> Go observable behavior
    +--> Rust observable behavior
```

## This patch implements in the Python reference

- Python as the executable behavior reference.
- Uniform programmer-facing value types: built-ins expose intrinsic method
  signatures and can structurally satisfy traits.
- Predefined core traits: `Stringable`, `Equatable`, `Comparable`, `Hashable`,
  `Countable`, `Iterable<T>`, and `Collection<T>`.
- Generic structs, functions, and traits.
- Type-parameter constraints such as `T: Stringable & Hashable`.
- Generic inference from function arguments and struct fields.
- Structural one-binding iteration through `iterator() -> list<T>`.
- A deterministic Python-first parity runner.

## Deliberately not claimed by this patch

These are still language-evolution work and must not be presented as complete
until the Python semantic reference and conformance fixtures define them:

- closures / anonymous function syntax and complete function types;
- payload/algebraic enums and pattern-binding switch cases;
- cross-package type identities and qualified struct construction;
- concurrency;
- self-hosting/bootstrap compiler support;
- Go/Rust implementation of the new generic/core-trait semantics.

The optimized implementations continue to build and are compared against Python
on the shared deterministic legacy corpus. Port each new semantic feature to Go
and Rust, add its reference fixture to the shared parity set, and only then
consider that feature parity-complete.
