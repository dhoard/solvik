# Solvik Bytecode

This document describes the canonical Solvik bytecode format: the binary
artifact produced by the Rust compiler and consumed by the verifier and the
Rust VM. There is exactly one implementation; the format is internal to it.

## File layout

All integers are **little-endian**. The module begins with a magic tag and a
format version.

```
magic      "SOLV"            (4 bytes)
version    u32               (currently 1)
constants  <constant pool>
functions  <function table>
classes    <class table>
interfaces <interface table>
dyn_names  <dynamic name table>
entry      u32               (0xFFFFFFFF = none)
sources    <source table>
```

A decoder rejects any module whose magic is not `SOLV` or whose version does
not match `CodeModule::FORMAT_VERSION`.

## Constant pool

```
count      u32
per entry:
  tag      u8
  payload  (tag-dependent)
```

| Tag | Value | Payload |
| --- | ----- | ------- |
| 0 | null | — |
| 1 | bool | u8 (0/1) |
| 2 | long | i64 |
| 3 | double | f64 (IEEE-754 bits) |
| 4 | char | u32 code point |
| 5 | string | u32 byte length + UTF-8 bytes |

Constants are interned; instructions reference them by index (`LoadConst`).

## Function table

```
count      u32
per function:
  code_len   u32
  code       code_len bytes          (machine code, see opcodes)
  local_count u16                    (params occupy the first slots)
  returns_value u8                   (0/1)
  param_count u16
  params     param_count × (u16 len + utf8)
  line_map_len u32
  line_map   line_map_len × (u32 offset, u32 line)
  source_file u32
  name       u16 len + utf8          (e.g. "Main.run")
```

`local_count` is the number of stack slots reserved for the frame; arguments
are copied into the first `param_count` slots on call.

## Class table

```
count      u32
per class:
  name        u16 len + utf8
  parent      i32                     (-1 = none)
  field_count u16
  vtable_len  u16
  vtable      vtable_len × u32        (function id per slot)
  statics_len u16
  statics     statics_len × (name, u32 fid)
  ifaces_len  u16
  ifaces      ifaces_len × (u32 iface_id, u16 n_fids, n_fids × u32)
```

- The **vtable** maps virtual-method slots to concrete function ids. Slot
  order is parent-first and fixed at resolution time, so dispatch is a direct
  index — no runtime name lookup.
- **Statics** are resolved by name to a function id for `Type.method(...)`.
- **Interface tables** map each implemented interface to the function id per
  slot, enabling nominal, metadata-driven interface dispatch.

## Interface table

```
count      u32
per interface:
  name         u16 len + utf8
  slot_count   u16
  slots        slot_count × (u16 len + utf8)
  default_count u16
  defaults     default_count × (u32 fid | 0xFFFFFFFF)   (0xFFFFFFFF = abstract)
```

## Dynamic name table

Names used by dynamic dispatch on `Object`-typed receivers
(`CallDynamic`). A small, separate table so the common static path stays
name-free.

```
count      u16
names      count × (u16 len + utf8)
```

## Entry point and sources

```
entry      u32              (function id of Main.run, or 0xFFFFFFFF)
sources    u32 count + count × (u16 len + utf8)
```

The source table backs runtime stack traces: each function records its
`source_file` index and a `(offset, line)` line map.

## Opcodes

Instructions are a one-byte opcode followed by fixed-size operands. The full
set (126 opcodes, codes 0–125) covers:

- **Constants/locals/globals**: `LoadConst`, `LoadLocal`, `StoreLocal`,
  `LoadGlobal`, `StoreGlobal`.
- **Control flow**: `Jump`, `JumpIfFalse`, `JumpIfTrue`, `Return`,
  `ReturnVoid`.
- **Calls**: `CallStatic(fid, arity, target_class)`, `CallVirtual(class,
  slot, arity)`, `CallInterface(iface, slot, arity)`, `CallSuper(parent,
  slot, arity)`, `CallNative(id, arity)`, `CallDynamic(name_id, arity)`.
- **Objects**: `NewObject(class, field_count)`, `LoadField(slot)`,
  `StoreField(slot)`, `CopyFields(count)`, `IdentityEq`, `IdentityNe`.
- **Collections**: list/map/stack constructors and element operations.
- **Arithmetic/comparison/logic**: typed long/double/char/string operators,
  `IsNull`, `Not`, etc.
- **Strings**: length, substring, contains, split, case conversion, concat.
- **Enums**: `NewEnum`, `EnumIndex`, `EnumPayload`.
- **Exceptions**: `Throw`, `TryBegin(catch_ip, finally_ip)`, `TryEnd`,
  `FinallyEnd`, `FinallyDivert`.
- **Misc**: `Dup`, `GcHint`.

`CopyFields(count)` copies the first `count` fields from the source instance
(below) into the destination instance (top), then drops the source. It is
emitted by `super:` construction to transfer the parent's initialized fields
into the freshly allocated subclass.

## Verification contract

Before execution, the verifier runs fixed-point dataflow analysis over basic
blocks and rejects modules that violate any of:

- Stack underflow at any instruction.
- Inconsistent stack height at join points or at return.
- Falling off the end of a value-returning function without `Return`.
- Jump targets that are not instruction boundaries.
- Out-of-range constant, local, global, function, class, interface, field,
  or vtable indices.
- Call arity inconsistent with the callee signature.
- Invalid exception-region metadata.

Malformed or hostile bytecode must fail safely and deterministically — never
cause undefined behavior, memory unsafety, arbitrary host access, or an
uncontrolled panic.

## Execution model

The VM is a stack machine over a managed heap:

- Values are primitives (`Bool`, `Long`, `Double`, `Char`) or heap references
  (`GcRef`).
- Objects live in a bump-allocated heap with tracing mark-and-sweep GC.
- Each call frame records its function id, instruction pointer, and stack
  base; arguments occupy the first local slots.
- Dispatch uses resolved slots/ids (vtable index, interface table, native id)
  rather than runtime strings, except for `Object`-typed dynamic calls.
- Object identity and aliasing are preserved: two references to the same heap
  object compare equal with `==`/`!=` by identity.

## Tooling

- `SOLVIK_DUMP_BC=1 solvik prog.sol` prints a disassembly of every function:
  byte offsets, instruction indices, source lines, and operands resolved to
  constant values, function/class/interface names, vtable slots, and jump
  targets (see `src/disasm.rs`).
- `SOLVIK_DUMP_IR=1` prints the IR (post-optimization; combine with
  `SOLVIK_NO_OPT=1` to see the pre-optimization IR); `SOLVIK_NO_OPT=1`
  disables the peephole optimizer (constant folding, jump-to-next removal)
  for differential testing.
- `cargo bench` runs the performance suite described in PERFORMANCE.md.
