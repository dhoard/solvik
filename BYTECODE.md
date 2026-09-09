# Solvik Bytecode

This document describes the canonical Solvik bytecode format: the binary
artifact produced by the Rust compiler and consumed by the verifier and the
Rust VM. There is exactly one implementation; the format is internal to it.

## File layout

All integers are **little-endian**. The module begins with a magic tag and a
format version.

```
magic      "SOLV"            (4 bytes)
version    u32               (currently 2)
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

Version history:

- **v1** — original layout (no `max_stack`).
- **v2** — each function carries a verifier-computed `max_stack: u16`, the
  maximum operand depth above the local region on any accepted path. The VM
  uses it to reserve stack capacity per frame.

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
  max_stack  u16                    (verified max operand depth; v2+)
  returns_value u8                   (0/1)
  param_count u16
  params     param_count × (u16 len + utf8)
  line_map_len u32
  line_map   line_map_len × (u32 offset, u32 line)
  source_file u32
  name       (u16 len + utf8)        (debug name, e.g. `Main.run`)
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
set (127 opcodes, codes 0–126) covers:

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

The [complete opcode table](docs/OPCODES.md) lists every operand and stack effect.
The VM stores each decoded instruction in 16 bytes (previously 20); it increments
the frame instruction index instead of storing a redundant successor index.
The active function's instruction slice is cached across dispatch iterations.

## Verification contract

Before execution, the CLI and compiler library run the verifier. It checks
instruction decoding, constant/local/global indices, direct-call targets and
arities, class/interface dispatch references (including arity and return-shape
consistency across every possible virtual/interface dispatch target),
jump/handler boundaries, construction shape, entry-point requirements, and
module metadata such as hierarchy acyclicity.

Its stack analysis is exact: a worklist propagates a finite abstract state
(operand height, active try-region stack, pending-transfer flag) over basic
blocks and requires every join to receive one consistent state. Incompatible
joins, stack-growing loops, region violations (`TryEnd` without a region,
`FinallyEnd` height mismatches), terminator fallthroughs, and the obsolete
variable-expansion opcode `ListSpread` are rejected with deterministic,
source-located diagnostics. There is no maximum-height or unknown-height
acceptance fallback; analysis termination follows from the finite state
domain plus explicit rejecting resource limits. Optimized and unoptimized
compiler output each verify independently.

On success, `verify_with_max_stacks` records each function's maximum operand
height in `CodeFunction::max_stack`. The VM treats that value as a verified
upper bound: it reserves per-frame stack capacity from it and asserts the
bound in debug builds on every instruction. See
[docs/VERIFIER.md](docs/VERIFIER.md) for the full contract, guarantees, and
residual limitations.

Dynamic field bounds, receiver types, nulls, and other runtime invariants
remain checked by the VM: verification is a stack-shape and control-flow
proof, not abstract type interpretation. The raw `CodeModule`/VM APIs are not
a sandbox for hostile input; verification does not authorize unchecked
indexing or removal of dynamic validation.

## Execution model

The VM is a stack machine over a managed heap:

- Values are primitives (`Bool`, `Long`, `Double`, `Char`) or heap references
  (`GcRef`).
- Objects live in a vector-backed heap with free-slot reuse and tracing
  mark-and-sweep GC.
- Each call frame records its function id, instruction pointer, and stack
  base; arguments occupy the first local slots. Per-frame capacity is
  reserved up front from the verified `max_stack`, so hot loops do not pay
  for geometric vector growth of the operand stack.
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
- `cargo bench` runs the performance suite described in
  [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
