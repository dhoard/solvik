# Opcode reference

All defined opcodes are listed below in encoding order. The opcode count is
90 (codes 0–89, contiguous). Operands are little-endian;
branch/handler operands are byte offsets in serialized code and instruction indexes
in the VM. Stack columns show the suffix above the current frame's locals; an
unchanged prefix is implicit. `result?` means zero or one return value according
to the signature. Calls never provide multiple return values. Errors may unwind
or terminate instead of producing the normal stack result shown here.

Arithmetic and comparison opcodes are kind-generic: the VM dispatches on the
runtime value kinds of the operands. Integral operands compute in i64 at the
wider operand's width with checked overflow; floating operands compute in the
wider precision; `BigInteger`/`BigDecimal` heap objects use exact math.
`Convert` carries a one-byte target tag (`Byte`, `Short`, `Integer`, `Long`,
`Float`, `Double`, `Char`, `String`, `Boolean`, `BigInteger`, `BigDecimal`).
`Conforms` tests a value against a class/interface id plus an optional
native-kind tag (threads, mutexes, semaphores, processes, regexes, streams,
exceptions).

Native methods sometimes have different return conventions from similarly named
collection opcodes; their signatures in `stdlib/builtins.rs` govern `CallNative`.

| Code | Opcode | Operands | Stack before | Stack after | Typical use |
| ---: | --- | --- | --- | --- | --- |
| 0 | `LoadConst` | constant:u32 | — | value | Constant load |
| 1 | `LoadLocal` | slot:u16 | — | value | Indexed binding load |
| 2 | `StoreLocal` | slot:u16 | value | — | Indexed binding store |
| 3 | `Add` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic; arbitrary precision for BigInteger/BigDecimal |
| 4 | `Sub` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic; arbitrary precision for BigInteger/BigDecimal |
| 5 | `Mul` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic; arbitrary precision for BigInteger/BigDecimal |
| 6 | `Div` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic; decimal division for BigDecimal |
| 7 | `Mod` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic; exact remainder for BigDecimal |
| 8 | `Neg` | — | number | number | Numeric negation |
| 9 | `Convert` | target:u8 | value | converted | Runtime conversion to the target numeric type (range checked) |
| 10 | `Not` | — | bool | bool | Boolean negation |
| 11 | `And` | — | bool, bool | bool | Boolean conjunction |
| 12 | `Pop` | — | value | — | Discard value |
| 13 | `IsNull` | — | value | bool | Null test |
| 14 | `NullCheck` | — | value | value | Fail on null |
| 15 | `Eq` | — | lhs, rhs | bool | Content equality across all value kinds |
| 16 | `Lt` | — | lhs, rhs | bool | Numeric ordering (integers and floats promote) |
| 17 | `Le` | — | lhs, rhs | bool | Numeric ordering (integers and floats promote) |
| 18 | `Gt` | — | lhs, rhs | bool | Numeric ordering (integers and floats promote) |
| 19 | `Ge` | — | lhs, rhs | bool | Numeric ordering (integers and floats promote) |
| 20 | `Jump` | target:u32 | — | — | Unconditional branch |
| 21 | `JumpIfFalse` | target:u32 | cond | — | Conditional branch |
| 22 | `JumpIfTrue` | target:u32 | cond | — | Conditional branch |
| 23 | `CallFn` | fn:u16 | args... | result? | Instance method call |
| 24 | `CallStatic` | fn:u16 | args... | result? | Static method call |
| 25 | `CallClass` | fn:u16 | args... | instance | Constructor call |
| 26 | `CallInterface` | fn:u16 | receiver, args... | result? | Interface dispatch |
| 27 | `CallNative` | fn:u16 | args... | result? | Built-in native call |
| 28 | `CallDynamic` | fn:u16 | receiver, args... | result? | Dynamic method call |
| 29 | `NewObject` | class:u16 | — | instance | Allocate instance |
| 30 | `LoadField` | field:u16 | instance | value | Read instance field |
| 31 | `StoreField` | field:u16 | instance, value | — | Write instance field |
| 32 | `LoadStatic` | class:u16, slot:u16 | — | value | Read the declaring class's static field slot |
| 33 | `StoreStatic` | class:u16, slot:u16 | value | — | Write the declaring class's static field slot |
| 34 | `Conforms` | id:u16, kind:u8 | value | bool | Type test: class/interface id plus native-kind tag |
| 35 | `NewList` | — | capacity:u32 | list | Allocate list |
| 36 | `NewMap` | — | capacity:u32 | map | Allocate map |
| 37 | `NewStack` | — | capacity:u32 | stack | Allocate stack |
| 38 | `ListSpread` | — | — | — | Reserved; rejected by the verifier (variadic spread compiles to `NewList`/`ListAdd`/`ListExtend`) |
| 39 | `ListAdd` | — | list, element | list | Append element |
| 40 | `ListGet` | — | list, index | value | Indexed read |
| 41 | `ListSet` | — | list, index, value | old | Indexed write; pushes the replaced element |
| 42 | `ListRemove` | — | list, index | removed | Remove at index |
| 43 | `ListLen` | — | list | Integer | Element count |
| 44 | `ListContains` | — | list, value | bool | Membership test |
| 45 | `ListIndexOf` | — | list, value | Integer | First index or -1 |
| 46 | `ListReverse` | — | list | list | In-place reversal |
| 47 | `ListSort` | — | list | list | In-place sort |
| 48 | `ListJoin` | — | list, separator | String | Join elements into text |
| 49 | `ListClear` | — | list | list | Remove all elements |
| 50 | `MapPut` | — | map, key, value | previous? | Insert or update entry |
| 51 | `MapGet` | — | map, key | value? | Entry lookup |
| 52 | `MapRemove` | — | map, key | removed? | Remove entry |
| 53 | `MapContainsKey` | — | map, key | bool | Key test |
| 54 | `MapLen` | — | map | Integer | Entry count |
| 55 | `MapKeys` | — | map | list | Key list |
| 56 | `MapValues` | — | map | list | Value list |
| 57 | `MapClear` | — | map | map | Remove all entries |
| 58 | `StackPush` | — | stack, value | stack | Push value |
| 59 | `StackPop` | — | stack | value | Pop value |
| 60 | `StackPeek` | — | stack | value | Top value |
| 61 | `StackGet` | — | stack, index | value | Indexed read |
| 62 | `StackLen` | — | stack | Integer | Element count |
| 63 | `StackEmpty` | — | stack | bool | Empty test |
| 64 | `StrLen` | — | string | Long | Unicode-scalar length |
| 65 | `StrConcat` | — | a, b | string | Text concatenation |
| 66 | `StrSubstr` | — | string, start, end | string | Slice by scalar range |
| 67 | `StrContains` | — | string, pattern | bool | Text matching |
| 68 | `StrStartsWith` | — | string, pattern | bool | Text matching |
| 69 | `StrEndsWith` | — | string, pattern | bool | Text matching |
| 70 | `StrSplit` | — | string, separator | list | Split text |
| 71 | `StrReplace` | — | string, from, to | string | Replace matches |
| 72 | `StrTrim` | — | string | string | Text transformation |
| 73 | `StrUpper` | — | string | string | Text transformation |
| 74 | `StrLower` | — | string | string | Text transformation |
| 75 | `StrIndex` | — | string, pattern | Long | Unicode-scalar index or -1 |
| 76 | `StrCharAt` | — | string, index | Char | Indexed Unicode scalar |
| 77 | `NewEnum` | enum:u16, variant:u8, payload:u8 | payload? | enum | Construct enum value |
| 78 | `EnumIndex` | — | enum | Long | Read variant |
| 79 | `EnumPayload` | — | enum | value? | Read payload |
| 80 | `Throw` | — | exception | — (terminator) | Unwind to catch/finally or fail; never falls through |
| 81 | `TryBegin` | catch:u32, finally:u32 | — | — | Register region; zero means absent handler |
| 82 | `TryEnd` | — | — | — | Remove region |
| 83 | `FinallyEnd` | — | region-entry height | continuation state | Consume a pending transfer: rethrow, finish deferred return, resume break/continue, or fall through when entered by normal completion |
| 84 | `FinallyDivert` | — | — | finally state | Divert to the innermost finally (if any) then resume at the next instruction |
| 85 | `ListExtend` | — | destination, source | destination | Append elements preserving source |
| 86 | `Return` | — | value | caller result | Return through pending finally blocks |
| 87 | `ReturnVoid` | — | — | caller state | Void return through finally |
| 88 | `Dup` | — | value | value, value | Copy value/handle |
| 89 | `GcHint` | — | — | — | Collect if due and only one active thread |
