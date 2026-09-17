# Launcher Output Plan: Remove Interpreter Information

## Problem

Running a Solvik program through the JVM or native launcher prints an engine banner before the
program output, for example:

```text
== running on Engine[id=71b1805a, isolate=NONE, state=OPEN, implementationName=Oracle GraalVM,
version=25.3.4.1, sandboxPolicy=TRUSTED, instantiatedLanguages=[], createdInstruments=[], options={}]
Hello, Solvik!
```

The banner is inherited launcher scaffolding. It exposes Truffle/GraalVM interpreter identity and
the polyglot option map, and it is not program output. Suppressing it currently requires the
`--disable-launcher-output` flag, so the default launcher behavior is wrong for a released language.

## Goal

The JVM (`standalone/target/solvik`) and native (`standalone/target/solvik-native`) launchers print
only the program's own output on standard output, with no interpreter/engine information.

## Non-goals

- No change to the Solvik language, compiler, or runtime.
- No change to diagnostics: compile and runtime errors still go to standard error.
- No retained opt-in/opt-out flag for the removed banner.

## Decision

Remove the banner and the `--disable-launcher-output` option entirely rather than flipping the
default. Once the banner is gone the flag has no meaning, and keeping a no-op flag would be an
unnecessary compatibility surface (`AGENTS.md`, "Do not create unnecessary wrappers, aliases,
compatibility layers").

## Changes

1. `launcher/src/main/java/org/solvik/launcher/SolvikMain.java`
   - delete the `launcherOutput` argument/flag parsing;
   - delete the `out.println("== running on " + context.getEngine())` block;
   - drop the `launcherOutput` parameter from `executeSource`.
2. `launcher/src/test/java/org/solvik/launcher/test/SolvikMainTest.java`
   - update the `run` helper for the new signature;
   - replace `launcherBannerIsOptional` with a test asserting the launcher emits exactly the
     program output and no engine line.
3. `docs/STATUS.md`
   - record the change and its validation evidence; keep `NEXT` at `COMPLETE`.

## Tests

- Positive: a program with no output produces empty stdout; a `println` program produces exactly
  its own line.
- Negative: stdout never contains `== running on` or `Engine[id=`.
- Existing compile-error and exit-code tests keep passing.

## Validation

- `./build.sh`
- `./build-native.sh`
- native smoke test: `./standalone/target/solvik-native language/tests/Hello.sol` prints only
  `Hello, Solvik!`.
