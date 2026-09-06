# Phase 12 — Contract hardening and cross-implementation determinism

Status: complete

## Objective

Strengthen the candidate 1.0 implementation without introducing language
features or changing an unresolved semantic choice. This phase only applies
rules already established by `LANGUAGE.md`, `SEMANTICS.md`, and the Python
semantic reference.

## Scope

1. Add focused reference fixtures for behavior that crosses implementation
   runtime boundaries, especially Unicode string operations.
2. Make Go and Rust behavior match the Python oracle for those fixtures.
3. Keep diagnostics, exit codes, and observable output deterministic.
4. Preserve the existing value, nullability, generic, trait, and error-model
   decisions; unresolved design questions remain outside this phase.
5. Verify the shipped Go and Rust paths with unit, integration, conformance,
   and differential tests.

## Implementation

- `string.indexOf` returns a character position, matching the language's
  Unicode-aware string indexing and the Python reference.
- `string.padStart` and `string.padEnd` measure existing text in characters,
  matching Python's string length behavior rather than Go's UTF-8 byte length.
- A deterministic reference fixture covers multibyte positions, padding, and
  byte length together.
- The Rust semantic runtime already had the contract-correct behavior, so no
  Rust semantic change was necessary; the Go reference path was aligned with
  it and with the Python oracle.

## Verification

- `./build-go.sh test`: Go tests, race tests, vet, and formatting checks pass.
- `./build-rust.sh all`: Rust release build, 18 unit tests, 47 integration
  scripts, and 31 conformance fixtures pass.
- `python3 tools/parity.py --optimized-if-present`: Python reference fixtures
  and full Go/Rust differential checks pass, including the Phase 12 fixture.

## Exit criteria

- The focused fixture passes under Python, Go, and Rust.
- Go unit tests, Rust release tests, and the complete Python-first differential
  suite pass.
- No new language semantics are added and no unresolved design choice is
  silently fixed.

## Deferred design work

Container aliasing/mutability, nullable dereference policy, generic variance,
exception versus `Result` conventions, and runtime type identity remain
explicitly deferred because they require language-design input.
