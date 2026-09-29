// Solvik TCK SOL-TCK-0466
// A closure parameter follows the existing immutable-parameter rule, so assigning to it in the body is rejected
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function introduces a function boundary and a lexical scope containing its parameters and body locals;
//   - its parameters follow the existing immutable-parameter rule, and a declaration inside its body may shadow an outer binding under the ordinary lexical-scope rules.
//   - Anonymous self-recursion through the binding being initialized is not supported: listing that binding in the capture list is an ordinary read-before-initialization error (`SOLV-TYPE-008`), because the value does not exist when its initializer is evaluated.
//
func run(): func(Integer): Integer {
    return func(value: Integer): Integer {
        value = value + 1
        return value
    }
}

print("EXECUTED-INVALID")
