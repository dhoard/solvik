# Native Binary Name Plan: `solvik-native`

## Problem

The native build produces `standalone/target/solviknative`. That is the inherited SimpleLanguage
`slnative` naming pattern with the product prefix pasted on: one CamelCase word with no separator.
The JVM launcher is `solvik`, and the distribution uses hyphenated, lowercase names elsewhere
(`solvik-parent`, `solvik-launcher`, `solvik-standalone`, `application/x-solvik`). The native
executable is the only release artifact that breaks the convention, which makes it harder to
discover and inconsistent with the project identity.

## Goal

The native-image distribution is emitted as `standalone/target/solvik-native`, and every supported
document, CI job, and developer instruction refers to that name.

## Non-goals

- No change to the Solvik language, compiler, runtime, launcher behavior, or diagnostics.
- No change to the JVM launcher (`standalone/target/solvik`) or its launcher template
  (`standalone/solvik`).
- No compatibility alias, symlink, wrapper, or duplicate artifact for the old `solviknative` name.
  The old name is dropped outright (`AGENTS.md`, "Do not create unnecessary wrappers, aliases,
  compatibility layers").
- No rewrite of the historical phase log in `docs/STATUS.md`. Earlier phases truthfully recorded the
  name used at the time; the new change section records the rename that supersedes them.

## Decision

Pass `-o ${project.build.directory}/solvik-native` to `native-image` and update the user-facing
references. The hyphenated name matches the existing Maven coordinate and module convention, so the
binary can be read as the distribution form of the `solvik` language without a second naming rule.

`native-image` accepts a hyphen in the `-o` value and emits exactly that file, so the change is a
one-token configuration edit plus documentation and CI alignment.

## Changes

1. `standalone/pom.xml`
   - native profile `make_native` execution: `-o ${project.build.directory}/solvik-native`.
2. `standalone/README.md`
   - native output path and both smoke-test invocations use `standalone/target/solvik-native`.
3. `README.md`
   - the distribution build description and the native launcher invocation use
     `standalone/target/solvik-native`.
4. `ci.jsonnet`
   - the GraalVM native smoke command runs `./standalone/target/solvik-native`.
5. `docs/LAUNCHER_OUTPUT_PLAN.md`
   - the goal and validation references name `standalone/target/solvik-native`; the removal described
     there still applies, so the current-behavior document must not point at a file that no longer
     exists.
6. `docs/STATUS.md`
   - update the top "Last clean native build" summary to the new name and the new evidence;
   - add this plan to the files-changed list and record the rename in a dedicated section;
   - keep `NEXT` at `COMPLETE`.

## Tests

A binary filename is build configuration, so the acceptance checks are build and smoke tests rather
than unit tests. Unit tests must not assert a stale filename.

- Positive: `./build-native.sh` emits `standalone/target/solvik-native`, and
  `./standalone/target/solvik-native language/tests/Hello.sol` prints only `Hello, Solvik!` and
  exits 0.
- Positive: the renamed binary still runs the checked-in examples with matching golden output and
  empty stderr.
- Negative: no `standalone/target/solviknative` file is produced, and no supported document, CI job,
  or build input still names `solviknative`.
- Regression: `./build.sh` and the JVM launcher tests are unchanged and still pass.

## Validation

- `./build.sh`
- `./build-native.sh`
- native smoke test: `./standalone/target/solvik-native language/tests/Hello.sol` prints only
  `Hello, Solvik!`.
- native golden-output check over `language/tests/*.sol`.
- `git grep solviknative` returns only historical `docs/STATUS.md` phase entries (and the
  `slnative` baseline note in `docs/BASELINE.md`), with no build input or supported instruction.
