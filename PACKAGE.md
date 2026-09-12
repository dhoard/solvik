# Solvik executable package format (SOLVPKG)

A Solvik package is a self-contained native executable: a prebuilt
`solvik-runtime` image with an encoded Solvik bytecode payload and a
fixed-size footer appended at end of file.

```text
+--------------------------------+
| native Solvik runtime image    |
+--------------------------------+
| encoded Solvik bytecode        |
+--------------------------------+
| fixed-size package footer      |
+--------------------------------+
EOF
```

## Footer

The footer is 52 bytes, located at `EOF - 52`, all integers little-endian:

| Offset | Size | Field          | Value                                   |
| ------ | ---- | -------------- | --------------------------------------- |
| 0      | 8    | magic          | `SOLVPKG\0`                             |
| 8      | 4    | format_version | `1`                                     |
| 12     | 8    | payload_length | byte length of the bytecode payload     |
| 20     | 32   | payload_sha256 | SHA-256 digest of the payload bytes     |

The payload occupies the `payload_length` bytes immediately preceding the
footer. The payload is located exclusively through the footer; loaders must
not scan the binary for the bytecode magic (`SOLV`), because a valid native
executable may independently contain those bytes.

`format_version` is an independent version domain: it is not the Solvik
bytecode version.

## Loading

At start-up the runtime:

1. reads and validates the footer (magic, version, bounds);
2. verifies the payload's SHA-256 digest (integrity, not safety);
3. decodes and re-verifies the bytecode module with the standard verifier;
4. parses launch options from its own command line (below);
5. runs the module's entry point.

Any failure before step 5 exits with code 2 and a `runtime error:` message.

## Launch options

Packaged executables accept launch-time options that are parsed by the
runtime at start-up — never embedded in the package payload:

```sh
./program [-Pkey=value ...] [--] [program-arguments ...]
```

- Leading `-Pkey=value` tokens initialize the program property store
  observed by `System.getProperty`/`setProperty`/`clearProperty`. The token
  splits at the first `=` (values may contain further `=`), the key must be
  non-empty, the value may be empty, and repeated keys resolve left to
  right, last value winning.
- `--` ends option consumption explicitly and is not passed to the program;
  it lets a program receive a first argument that itself begins with `-P`.
- After the first ordinary argument, every remaining value — including
  strings beginning with `-P` — is a program argument delivered to
  `Main.run(args)`.
- Properties are installed before static initialization or entry dispatch,
  are fresh for each execution, never appear in `Main.run(args)`, and never
  alter the host environment or `System.getEnv()` results.
- A malformed leading `-P` token is a usage error (exit 2).

Because parsing happens at run time, the same executable can be launched
with different property values on different runs without repackaging.

## Building packages

`solvik --package <file.sol> [-o <output>]` composes the output from a
prebuilt `solvik-runtime` image (discovered next to the `solvik` executable
or via `SOLVIK_RUNTIME`) and the canonical encoding of the verified module
that `solvik <file.sol>` would execute. The output is written atomically and
refused if it already exists. Build-time options (`--package`, `-o`) are
packaging concerns only; program arguments and properties are supplied to
the generated executable at run time.
