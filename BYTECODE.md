# Solvik Bytecode

This document describes the canonical Solvik bytecode format: the binary
artifact produced by the Rust compiler and consumed by the verifier and the
Rust VM. There is exactly one implementation; the format is internal to it.

The bytecode *version* in this document is independent from the
self-contained executable **package format version** used by
`solvik --package`; see [PACKAGE.md](PACKAGE.md) for that container format.

## File layout

All integers are **little-endian**. The module begins with a magic tag and a
format version.

```
magic      "SOLV"            (4 bytes)
version    u32               (currently 5)
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
- **v3** — composition-first class metadata. The class `parent` field, the
  inheritance method_table, `CallSuper`, and `CopyFields` are removed. Each class
  now carries a class-local method table (for direct concrete calls) and a
  public dynamic-method table (for `Object` dispatch); `CallVirtual` is
  renamed `CallClass`.
- **v4** — static fields. Each class gains a `static_fields` table
  (`(name, slot)` pairs in a slot namespace separate from instance fields)
  and a `static_init` function id (the synthetic initializer, absent when the
  class has no static fields). Two new opcodes, `LoadStatic(class, slot)`
  and `StoreStatic(class, slot)`, read and write the per-class static slot
  vectors.
- **v5** — Java-aligned numeric lattice. The constant pool gains typed
  numeric entries (`Byte`, `Short`, `Integer`, `Float`, `BigInteger`,
  `BigDecimal`). Arithmetic and comparison opcodes become kind-generic
  (`Add`, `Sub`, `Mul`, `Div`, `Mod`, `Neg`, `Eq`, `Lt`, `Le`, `Gt`, `Ge`):
  the VM dispatches on runtime value kinds instead of per-type opcode
  families. New opcodes: `Convert(target:u8)` for runtime narrowing/widening
  conversions and `Conforms(id:u16, kind:u8)` for typed catch dispatch.
  Legacy per-type opcodes (`AddLong`, `ToBool`, `IdentityEq`, ...) and the
  unused `LoadGlobal`/`StoreGlobal` pair are removed; the opcode count drops
  to 90.

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
| 2 | byte | i8 |
| 3 | short | i16 |
| 4 | integer | i32 |
| 5 | long | i64 |
| 6 | float | f32 (IEEE-754 bits) |
| 7 | double | f64 (IEEE-754 bits) |
| 8 | char | u32 code point |
| 9 | string | u32 byte length + UTF-8 bytes |
| 10 | big integer | u32 byte length + decimal text |
| 11 | big decimal | u32 byte length + decimal text |

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
  name         u16 len + utf8
  field_count  u16
  method_names_len u16
  method_names method_names_len × (u16 len + utf8)
  method_table_len   u16
  method_table       method_table_len × u32        (function id per class-local slot)
  dyn_len      u16
  dyn          dyn_len × (name, u32 fid)  (public effective methods)
  statics_len  u16
  statics      statics_len × (name, u32 fid)
  ifaces_len   u16
  ifaces       ifaces_len × (u32 iface_id, u16 n_fids, n_fids × u32)
  static_fields_len u16
  static_fields  static_fields_len × (name, u16 slot)   (v4+)
  static_init  u32              (init function id, or 0xFFFFFFFF)   (v4+)
```

- The **class-local method table** maps this class's own instance-method
  names (explicit methods plus compiler-generated delegation wrappers) to
  concrete function ids. There are no subclasses, so concrete calls are a
direct index — no runtime name lookup and no inheritance prefix.
- **Statics** are resolved by name to a function id for `Type.method(...)`.
- The **dynamic method table** holds the class's public effective methods
  (name, function id). `Object`-typed dynamic calls use it; private methods
  are excluded, and there is no parent-chain walk.
- **Interface tables** map each implemented interface to the function id per
  slot, enabling nominal, metadata-driven interface dispatch. Delegated and
  default implementations appear in these tables exactly like explicit ones.
- **Static fields** (v4+) name the class's static slots; the slot namespace
  is separate from instance fields (`field_count`). `static_init` is the id
  of the compiler-synthesized void, parameterless initializer that stores
  every static field's initializer value into its slot; the VM runs these
  functions in class declaration order before the entry point.

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
set (90 opcodes, codes 0–89) covers:

- **Constants/locals**: `LoadConst`, `LoadLocal`, `StoreLocal`.
- **Control flow**: `Jump`, `JumpIfFalse`, `JumpIfTrue`, `Return`,
  `ReturnVoid`.
- **Calls**: `CallStatic(fid)`, `CallClass(class, slot)`,
  `CallInterface(iface, slot)`, `CallNative(id)`, `CallDynamic(name_id)`.
- **Objects**: `NewObject(class)`, `LoadField(slot)`, `StoreField(slot)`,
  `LoadStatic(class, slot)`, `StoreStatic(class, slot)`, `Conforms(id, kind)`.
- **Collections**: list/map/stack/set constructors and element operations.
- **Arithmetic/comparison/logic**: kind-generic `Add`, `Sub`, `Mul`, `Div`,
  `Mod`, `Neg`, `Eq`, `Lt`, `Le`, `Gt`, `Ge`, plus `Convert(target)`,
  `IsNull`, `Not`, `And`.
- **Strings**: length, substring, contains, split, case conversion, concat.
- **Enums**: `NewEnum`, `EnumIndex`, `EnumPayload`.
- **Exceptions**: `Throw`, `TryBegin(catch_ip, finally_ip)`, `TryEnd`,
  `FinallyEnd`, `FinallyDivert`.
- **Misc**: `Dup`, `GcHint`.

`CallClass` indexes the receiver's class-local method table. Because classes
cannot be subclassed, a call on a statically known concrete type always has
exactly one target, so no virtual dispatch is involved.

The [complete opcode table](docs/OPCODES.md) lists every operand and stack effect.
The VM stores each decoded instruction in 16 bytes (previously 20); it increments
the frame instruction index instead of storing a redundant successor index.
The active function's instruction slice is cached across dispatch iterations.

## Verification contract

Before execution, the CLI and compiler library run the verifier. It checks
instruction decoding, constant/local/global indices, direct-call targets and
arities, class/interface dispatch references (including arity and return-shape
consistency across every possible dispatch target), jump/handler boundaries,
construction shape, static slot/class bounds for `LoadStatic`/`StoreStatic`,
static-initializer shape (void, parameterless), entry-point requirements, and
module metadata.

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

- Values are primitives (`Boolean`, `Byte`, `Short`, `Integer`, `Long`,
  `Float`, `Double`, `Char`) or heap references (`GcRef`).
- Objects live in a vector-backed heap with free-slot reuse and tracing
  mark-and-sweep GC.
- Each call frame records its function id, instruction pointer, and stack
  base; arguments occupy the first local slots. Per-frame capacity is
  reserved up front from the verified `max_stack`, so hot loops do not pay
  for geometric vector growth of the operand stack.
- Dispatch uses resolved slots/ids (class method table index, interface
  table, native id) rather than runtime strings, except for `Object`-typed
  dynamic calls.
- Object identity and aliasing are preserved: two references to the same heap
  object compare equal with `==`/`!=` by identity.

## Tooling

- `SOLVIK_DUMP_BC=1 solvik prog.sol` prints a disassembly of every function:
  byte offsets, instruction indices, source lines, and operands resolved to
  constant values, function/class/interface names, method slots, and jump
  targets (see `src/disasm.rs`).
- `SOLVIK_DUMP_IR=1` prints the IR (post-optimization; combine with
  `SOLVIK_NO_OPT=1` to see the pre-optimization IR); `SOLVIK_NO_OPT=1`
  disables the peephole optimizer (constant folding, jump-to-next removal)
  for differential testing.
- `cargo bench` runs the performance suite described in
  [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
