# Opcode reference

All defined opcodes are listed below in encoding order. The opcode count is
127 (codes 0–126, contiguous). Operands are little-endian;
branch/handler operands are byte offsets in serialized code and instruction indexes
in the VM. Stack columns show the suffix above the current frame's locals; an
unchanged prefix is implicit. `result?` means zero or one return value according
to the signature. Calls never provide multiple return values. Errors may unwind
or terminate instead of producing the normal stack result shown here.

Native methods sometimes have different return conventions from similarly named
collection opcodes; their signatures in `stdlib/builtins.rs` govern `CallNative`.

| Code | Opcode | Operands | Stack before | Stack after | Typical use |
| ---: | --- | --- | --- | --- | --- |
| 0 | `LoadConst` | constant:u32 | — | value | Constant load |
| 1 | `LoadLocal` | slot:u16 | — | value | Indexed binding load |
| 2 | `StoreLocal` | slot:u16 | value | — | Indexed binding store |
| 3 | `LoadGlobal` | slot:u16 | — | value | Indexed binding load |
| 4 | `StoreGlobal` | slot:u16 | value | — | Indexed binding store |
| 5 | `AddLong` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 6 | `SubLong` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 7 | `MulLong` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 8 | `DivLong` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 9 | `ModLong` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 10 | `NegLong` | — | number | number | Numeric negation |
| 11 | `AddDouble` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 12 | `SubDouble` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 13 | `MulDouble` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 14 | `DivDouble` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 15 | `ModDouble` | — | lhs, rhs | number | Checked integer or IEEE floating arithmetic |
| 16 | `NegDouble` | — | number | number | Numeric negation |
| 17 | `ToLong` | — | value | converted | Native conversion rules |
| 18 | `ToDouble` | — | value | converted | Native conversion rules |
| 19 | `ToByte` | — | value | converted | Native conversion rules |
| 20 | `ToBool` | — | value | converted | Native conversion rules |
| 21 | `ToChar` | — | value | converted | Native conversion rules |
| 22 | `ToStringValue` | — | value | converted | Native conversion rules |
| 23 | `Not` | — | bool | bool | Boolean negation |
| 24 | `And` | — | bool, bool | bool | Boolean conjunction |
| 25 | `Pop` | — | value | — | Discard value |
| 26 | `IsNull` | — | value | bool | Null test |
| 27 | `NullCheck` | — | value | value | Reject null |
| 28 | `EqLong` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 29 | `EqDouble` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 30 | `EqBool` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 31 | `EqChar` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 32 | `EqString` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 33 | `EqObject` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 34 | `EqEnum` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 35 | `EqDyn` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 36 | `LtLong` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 37 | `LeLong` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 38 | `GtLong` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 39 | `GeLong` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 40 | `LtDouble` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 41 | `LeDouble` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 42 | `GtDouble` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 43 | `GeDouble` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 44 | `LtChar` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 45 | `LeChar` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 46 | `GtChar` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 47 | `GeChar` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 48 | `LtString` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 49 | `LeString` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 50 | `GtString` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 51 | `GeString` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 52 | `LtDyn` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 53 | `LeDyn` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 54 | `GtDyn` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 55 | `GeDyn` | — | lhs, rhs | bool | Typed or dynamic comparison |
| 56 | `Jump` | target:u32 | — | — | Unconditional branch |
| 57 | `JumpIfFalse` | target:u32 | bool | — | Conditional branch |
| 58 | `JumpIfTrue` | target:u32 | bool | — | Conditional branch |
| 59 | `CallFn` | function:u32, arity:u16 | args… | result? | Direct function |
| 60 | `CallStatic` | function:u32, arity:u16, class:u16 | args… | result? | Static call; target class for `Self` construction |
| 61 | `CallClass` | class:u16, slot:u16, arity:u16 | receiver, args… | result? | Class-local method dispatch |
| 62 | `CallInterface` | interface:u16, slot:u16, arity:u16 | receiver, args… | result? | Interface dispatch |
| 63 | `CallNative` | native:u16, arity:u16 | receiver?, args… | result? | Native signature controls receiver and return |
| 64 | `CallDynamic` | name:u16, arity:u16 | receiver, args… | result | Resolve interned method name against the class's public dynamic table |
| 65 | `NewObject` | class:u16, fields:u16 | — | object | Instance with null fields |
| 66 | `LoadField` | slot:u16 | object | value | Indexed field read |
| 67 | `StoreField` | slot:u16 | object, value | object | Indexed field write |
| 68 | `IdentityEq` | — | lhs, rhs | bool | Reference identity |
| 69 | `IdentityNe` | — | lhs, rhs | bool | Reference identity |
| 70 | `NewList` | capacity:u16 | — | collection | Allocate empty collection; capacity operand currently unused |
| 71 | `NewMap` | capacity:u16 | — | collection | Allocate empty collection; capacity operand currently unused |
| 72 | `NewStack` | — | — | stack | Allocate empty stack |
| 73 | `ListSpread` | — | list | — | **Reserved/rejected**: variable expansion is not in the accepted contract; the verifier rejects it (`V015`) and the VM faults. Variadic spread compiles to `NewList`/`ListAdd`/`ListExtend` |
| 74 | `ListAdd` | — | list, value | list | Append element |
| 75 | `ListGet` | — | list, index | value | Indexed read |
| 76 | `ListSet` | — | list, index, value | list | Indexed write |
| 77 | `ListRemove` | — | list, index | list | Remove indexed element |
| 78 | `ListLen` | — | receiver | Long | Length; strings count Unicode scalars |
| 79 | `ListContains` | — | list, value | bool | Content membership |
| 80 | `ListIndexOf` | — | list, value | Long | First matching index or -1 |
| 81 | `ListReverse` | — | list | list | In-place change; sort uses display keys |
| 82 | `ListSort` | — | list | list | In-place change; sort uses display keys |
| 83 | `ListJoin` | — | list, separator | string | Join displayed elements |
| 84 | `ListClear` | — | list | list | In-place change; sort uses display keys |
| 85 | `MapPut` | — | map, key, value | map | Insert or replace value |
| 86 | `MapGet` | — | map, key | value? | Lookup or null |
| 87 | `MapRemove` | — | map, key | map | Remove matching entries |
| 88 | `MapContainsKey` | — | map, key | bool | Content-key membership |
| 89 | `MapLen` | — | receiver | Long | Length; strings count Unicode scalars |
| 90 | `MapKeys` | — | map | list | Materialize entries in stored order |
| 91 | `MapValues` | — | map | list | Materialize entries in stored order |
| 92 | `MapClear` | — | map | map | Clear entries |
| 93 | `StackPush` | — | stack, value | stack | Push element |
| 94 | `StackPop` | — | stack | value? | Top element or null |
| 95 | `StackPeek` | — | stack | value? | Top element or null |
| 96 | `StackGet` | — | stack, index | value | Indexed read |
| 97 | `StackLen` | — | receiver | Long | Length; strings count Unicode scalars |
| 98 | `StackEmpty` | — | stack | bool | Empty test |
| 99 | `StrLen` | — | receiver | Long | Length; strings count Unicode scalars |
| 100 | `StrConcat` | — | lhs, rhs | string | Concatenate displayed operands |
| 101 | `StrSubstr` | — | string, start, end | string | Clamped Unicode-scalar range |
| 102 | `StrContains` | — | string, pattern | bool | Text matching |
| 103 | `StrStartsWith` | — | string, pattern | bool | Text matching |
| 104 | `StrEndsWith` | — | string, pattern | bool | Text matching |
| 105 | `StrSplit` | — | string, separator | list | Split text |
| 106 | `StrReplace` | — | string, from, to | string | Replace matches |
| 107 | `StrTrim` | — | string | string | Text transformation |
| 108 | `StrUpper` | — | string | string | Text transformation |
| 109 | `StrLower` | — | string | string | Text transformation |
| 110 | `StrIndex` | — | string, pattern | Long | Unicode-scalar index or -1 |
| 111 | `StrCharAt` | — | string, index | Char | Indexed Unicode scalar |
| 112 | `NewEnum` | enum:u16, variant:u8, payload:u8 | payload? | enum | Construct enum value |
| 113 | `EnumIndex` | — | enum | Long | Read variant |
| 114 | `EnumPayload` | — | enum | value? | Read payload |
| 115 | `Throw` | — | exception | — (terminator) | Unwind to catch/finally or fail; never falls through |
| 116 | `TryBegin` | catch:u32, finally:u32 | — | — | Register region; zero means absent handler |
| 117 | `TryEnd` | — | — | — | Remove region |
| 118 | `Return` | — | value | caller result | Return through pending finally blocks |
| 119 | `ReturnVoid` | — | — | caller state | Void return through finally |
| 120 | `Dup` | — | value | value, value | Copy value/handle |
| 121 | `GcHint` | — | — | — | Collect if due and only one active thread |
| 122 | `FinallyEnd` | — | region-entry height | continuation state | Consume a pending transfer: rethrow, finish deferred return, resume break/continue, or fall through when entered by normal completion |
| 123 | `FinallyDivert` | — | — | finally state | Divert to the innermost finally (if any) then resume at the next instruction |
| 124 | `ListExtend` | — | destination, source | destination | Append elements preserving source |
| 125 | `LoadStatic` | class:u16, slot:u16 | — | value | Read the declaring class's static field slot |
| 126 | `StoreStatic` | class:u16, slot:u16 | value | — | Write the declaring class's static field slot |
