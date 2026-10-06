// Solvik TCK SOL-TCK-0464
// A `this` capture item where no instance receiver exists is rejected, because a closure has no receiver of its own to capture
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A closure must not list or otherwise capture a `var mutable` local. Naming a `var mutable` in a capture list is `SEM_MUTABLE_CAPTURE` (`SOLV-SEM-057`), reported on that capture item, and a read or write of that captured name in the body is reported with the same code.
//   - Referencing the same outer `var mutable` without listing it remains `SEM_UNLISTED_CAPTURE` at the body reference; the compiler never silently converts it into a capture.
//   - An outer local or parameter referenced by the body but omitted from the capture list is `SEM_UNLISTED_CAPTURE` (`SOLV-SEM-058`), reported on the body reference. This applies to `this` as well: a closure body may use `this` only when `[this]` is written.
//   - The capture list uses source order as environment order.
//   - A duplicate capture item, and a capture item with the same name as one of the anonymous function's parameters, is `SOLV-RESOL-002`.
//   - An unknown name in a capture list remains `SOLV-RESOL-001`, and `this` where no instance receiver exists remains `SOLV-RESOL-005`.
//
func run(): func(): Integer {
    return func [this](): Integer {
        return 1
    }
}

print("EXECUTED-INVALID")
