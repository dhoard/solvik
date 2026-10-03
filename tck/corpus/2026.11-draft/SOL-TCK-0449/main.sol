// Solvik TCK SOL-TCK-0449
// A bare anonymous function in statement position is rejected because creating and discarding a function value is not a call; the function continues past it, so acceptance is observable
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function is an expression written with `func`, an optional capture list, a parenthesized parameter list, an optional return type, and a body:
//   - Its parameters must have explicit types.
//   - Its return type follows the same rule as a named function: omitting it declares `Unit`; a value-returning anonymous function must write its return type and must return a compatible value on every normally completing path.
//   - Function bodies never acquire an implicit tail result, and the ordinary return diagnostics apply inside an anonymous function exactly as they do in a declaration.
//   - A bare anonymous function or function reference used as an expression statement remains invalid, because creating and discarding a function value is not a call.
//
func use(): Unit {
    func(value: Integer): Integer {
        return value + 1
    }
    print("reached")
}

use()
print("EXECUTED-INVALID")
