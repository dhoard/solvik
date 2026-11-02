# Project Memory

This file indexes useful project knowledge retained across agent sessions. It is historical context,
not an authoritative source of requirements; verify details against `PROJECT.md` and current code.

## Entries

- [Writing conventions](memory/writing-conventions.md) — maintainer rules for USA English spelling
  across code and docs, and the requirement that documentation claims be verified against the current
  source.
- [Bundled native bindings and standard I/O](memory/io-native-bindings.md) —
  trusted `native` declarations and `BuiltinBindings`, the generated `Solvik$Io` adapter and
  `$payload`/`$of` shape, `IoError` codes, the packaged `Channel`/`Stdio`/`TextReader`/`TextWriter`
  surface, reachability filtering of unreached packaged types, the `File`/`ByteReader`/`ByteWriter`
  additions, and the removed `print`/`println` migration convention.
- [Language conformance invariants and naming rules](memory/language-conformance-invariants.md) —
  array element nullability invariance, reserved built-in names, `$`-suffixed generated names
  (`$function`, `$solvikError`/`$solvikExit`/`$solvikException`), the parser's delimiter/newline model,
  structural catchability of the one exception type, and recorded tensions between `LANGUAGE.md`
  examples and enforced rules.
