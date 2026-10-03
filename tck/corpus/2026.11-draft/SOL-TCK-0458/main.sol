// Solvik TCK SOL-TCK-0458
// An unlisted outer `mutable val` read by the body is an unlisted-capture diagnostic at the reference: the compiler never silently converts it into a capture
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A closure must not list or otherwise capture a `mutable val` local. Naming a `mutable val` in a capture list is `SEM_MUTABLE_CAPTURE` (`SOLV-SEM-057`), reported on that capture item, and a read or write of that captured name in the body is reported with the same code.
//   - Referencing the same outer `mutable val` without listing it remains `SEM_UNLISTED_CAPTURE` at the body reference; the compiler never silently converts it into a capture.
//   - An outer local or parameter referenced by the body but omitted from the capture list is `SEM_UNLISTED_CAPTURE` (`SOLV-SEM-058`), reported on the body reference. This applies to `this` as well: a closure body may use `this` only when `[this]` is written.
//   - The capture list uses source order as environment order.
//   - A duplicate capture item, and a capture item with the same name as one of the anonymous function's parameters, is `SOLV-RESOL-002`.
//   - An unknown name in a capture list remains `SOLV-RESOL-001`, and `this` where no instance receiver exists remains `SOLV-RESOL-005`.
//
func run(): func(Integer): Integer {
    mutable val offset = 100
    return func(value: Integer): Integer {
        return value + offset
    }
}

print("EXECUTED-INVALID")
