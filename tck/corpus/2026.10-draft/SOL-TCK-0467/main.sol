// Solvik TCK SOL-TCK-0467
// Anonymous self-recursion through the binding being initialized is a read-before-initialization error, and the body never mentions the name so the report is about the capture item alone
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function introduces a function boundary and a lexical scope containing its parameters and body locals;
//   - its parameters follow the existing immutable-parameter rule, and a declaration inside its body may shadow an outer binding under the ordinary lexical-scope rules.
//   - Anonymous self-recursion through the binding being initialized is not supported: listing that binding in the capture list is an ordinary read-before-initialization error (`SOLV-TYPE-008`), because the value does not exist when its initializer is evaluated.
//
func run(): func(Integer): Integer {
    val selfRef = func [selfRef](value: Integer): Integer {
        return value
    }
    return selfRef
}

print("EXECUTED-INVALID")
