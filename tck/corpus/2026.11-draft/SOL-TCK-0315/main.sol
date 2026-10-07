// Solvik TCK SOL-TCK-0315
// An `Integer` operand in a position that does have a `Result` boundary is the negated operand rule; the specification names `SOLV-SEM-049` for it.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Its operand must have a `Result<T, E>` type;
//   - | `SEM_RESULT_PROPAGATION_INVALID_OPERAND` | `SOLV-SEM-049` | the `?` operand is not a `Result<T, E>` |
//
func use(): Result<Integer, String> {
    var v: Integer = 1?
    return Result.Ok(v)
}

print("EXECUTED-INVALID")
