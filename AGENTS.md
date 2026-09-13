# Repository Guidelines

## Project Structure & Module Organization

This repository is a single Rust crate implementing the Solvik compiler and
bytecode VM. The compiler pipeline is under `src/` (`lexer.rs`, `parser.rs`,
`resolve.rs`, `check.rs`, `compiler.rs`); runtime code is under `src/vm/`,
bytecode encoding and verification are in `src/bytecode/` and
`src/verifier.rs`, and the CLI/runtime binaries are `src/main.rs` and
`src/bin/solvik-runtime.rs`. Rust integration tests live in `tests/`.
Language conformance fixtures are individual directories under
`test/cases/`, with files such as `main.sol`, `expected.out`, and
`expected.code`. Benchmarks are in `benches/`; design and reference material
is in the root Markdown files and `docs/`; editor support is in `sublime/`.
Dependencies are vendored under `vendor/`; avoid editing them directly.

## Build, Test, and Development Commands

- A passing `./build.sh` is the final quality gate: it checks formatting,
  runs tests and clippy, builds release binaries, and runs conformance
  integration tests.
- `cargo test` runs Rust unit and integration tests.
- `./test/run.sh target/debug/solvik` runs all `test/cases/` fixtures after a
  debug build.
- `cargo run --bin solvik -- example.sol` compiles and runs the sample program.
- `./build.sh build` builds both `solvik` and `solvik-runtime` for packaging.

## Coding Style & Naming Conventions

Run `cargo fmt` before committing; CI checks formatting and denies clippy
warnings. Follow idiomatic Rust 2021 naming: `snake_case` for functions and
modules, `UpperCamelCase` for types, and explicit error handling. Solvik
examples use four-space indentation, lowercase dotted package names, and
`UpperCamelCase` type names.

## Testing Guidelines

Name Rust tests descriptively in `snake_case`; place focused unit tests beside
the implementation and cross-module behavior in `tests/`. New language
behavior should include a conformance fixture when appropriate. Keep golden
outputs deterministic and run the narrowest relevant test while iterating,
then finish with a passing `./build.sh`.

## Commit & Pull Request Guidelines

Recent commits use short categorized subjects such as `fix: ...` and
`chore: ...`. Use the same concise convention and describe the user-visible
behavior when applicable. Pull requests should summarize the change, identify
any compatibility or diagnostic impact, link related issues, list validation
commands (especially `./build.sh`), and update relevant language or package
documentation. Screenshots are unnecessary unless documentation presentation
is affected.
