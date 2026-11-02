# Bundled native bindings and standard I/O

Milestone: `plans/solvik-io.impl.md` implemented `plans/SOLVIK_IO_DESIGN.md` (2026).
`plans/io-module-and-eol.impl.md` later moved the packaged types into `solvik::io` and renamed
`LINE_ENDING` to `EOL`. These are the invariants a later change must preserve; verify against
current source/tests before relying on them.

## Trust and resolution

- `Ast.FuncDecl.isNative` marks a bodyless `native` member or constructor. Only a `TypeDecl` parsed
  from a compiler-packaged resource may declare one; trust is `ProgramLoader.Loaded.packagedTypes`
  identity, not a name allowlist. A program file gets `SOL-T001`; a `native "class"` member or a
  trait member uses the older forms instead.
- Every native signature must match one entry in
  `dev.solvik.compiler.bind.BuiltinBindings` (owner, member, static-ness, parameter spellings, and
  result spelling). Owner keys are the declaring type's simple name; a moved user type spells its
  parameters and result with the qualified name (`solvik::io::Channel`), matching
  `SolvikType.toString()`. A missing or mismatched entry is `SOL-T013`; never add a reflective
  fallback.
- A type with a native member is native-backed. Its generated class gains `$payload`, `$of(payload)`,
  and `$payload()`; native constructors compute the payload, native instance methods pass it.
  Solvik-bodied methods (`solvik::io::Channel.write`/`writeAll`, `solvik::io::TextWriter.writeLine`) compile normally.

## Generated adapter

- The adapter is `src/main/java/dev/solvik/compiler/transpile/IoSupport.java` (embedded source) and
  is emitted as `solvik/Solvik$Io.java` only when a binding is lowered. Generated programs must not
  depend on a compiler JAR, so the adapter cannot be a runtime class.
- The adapter uses the program's generated `$solvikException` and the documented `solvik::io::IoError` codes
  (5001 Io, 5002 Closed, 5003 Unsupported, 5004 Encoding, 5005 InvalidArgument). I/O failures are
  raised `Exception` values, catchable by `try`/`catch`; they are not `SOL-Rxxx` aborts.
- The adapter flushes after every successful write, never closes a borrowed channel on reader/writer
  close, and caps a read allocation at 1 MiB.

## Packaged I/O surface

- `Channel`, `Stdio`, `TextReader`, `TextWriter`, `ByteReader`, `ByteWriter`, `File`, and `IoError`
  are public packaged types in the `solvik::io` module (`src/main/resources/solvik/io/`), so a
  program names them as `solvik::io::Name`; an unqualified name does not resolve. Their generated
  Java classes live in package `solvik.io`. `Channel.bytes()` is the only in-memory channel factory
  (duplex: writes append, reads consume, EOF is `Null`).
- `TextWriter.lineSeparator()` is a private static native binding; `EOL` is its once-read public
  immutable value. `write(text)` encodes exactly the supplied text and adds no terminator;
  `writeLine` is Solvik source: `write(text .. EOL)`.
- `ProgramLoader.Loaded.reachedPackagedTypeNames()` filters unreached packaged types out of the
  generated files, transitively through the source of reached packaged types, so a program with no
  I/O emits neither the I/O classes nor the adapter.

## File and byte I/O

- `solvik::io::File` is ordinary Solvik that owns a single-direction channel. Its only native members are the
  static `openReadChannel`/`openWriteChannel`/`openAppendChannel` helpers plus `exists`/`delete`;
  the registry maps them to `Solvik$Io.openFileRead`/`openFileWrite`/`openFileAppend`/`fileExists`/
  `fileDelete`. The generated `solvik::io::File` class has no `$payload`; the payload rule is "any non-static
  native member", not "any constructor".
- `solvik::io::ByteReader` and `solvik::io::ByteWriter` are pure Solvik over `solvik::io::Channel` and add no binding. They borrow the
  channel; `solvik::io::File.close()` is the only close that closes a channel.
- solvik::io::File channels are single-direction: read handles are readable only; write/append handles are
  writable only. Invalid paths raise `solvik::io::IoError.InvalidArgument`; filesystem failures raise
  `solvik::io::IoError.Io`; wrapper/channel close state raises `solvik::io::IoError.Closed`.
- Reachability scans source for whole words outside comments and literals, so a comment that names a
  packaged type no longer pulls it into generated output.

## Removed builtins and migration convention

- `print` and `println` are gone from the checker and lowerer; both are ordinary identifiers now.
  Existing Solvik call sites were migrated to `solvik::io::TextWriter(solvik::io::Stdio.stdout()).write(...)`/
  `.writeLine(...)`, joining multiple arguments with `..` and rendering a single non-text argument
  with `.. ""`. Tests that parse a hand-built AST use `testsupport.Checking` so the packaged library
  is in scope, as it is for real programs.
- The design and implementation plan remain the decision record; `LANGUAGE.md` §11.6 and
  `JAVA_BACKEND.md` "Bundled native declarations" are the authoritative behavior and backend notes.
