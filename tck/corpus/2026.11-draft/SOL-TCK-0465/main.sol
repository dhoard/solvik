// Solvik TCK SOL-TCK-0465
// A closure body is a lexical scope inside a function boundary: a parameter may shadow an outer binding with no capture entry and a body local may be declared, while the outer value stays readable outside
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function introduces a function boundary and a lexical scope containing its parameters and body locals;
//   - its parameters follow the existing immutable-parameter rule, and a declaration inside its body may shadow an outer binding under the ordinary lexical-scope rules.
//   - Anonymous self-recursion through the binding being initialized is not supported: listing that binding in the capture list is an ordinary read-before-initialization error (`SOLV-TYPE-008`), because the value does not exist when its initializer is evaluated.
//
func run(): String {
    var width = 100
    var inner = func(width: Integer): String {
        var doubled = width * 2
        return doubled.toString()
    }
    var shadowed = func(): Integer {
        var width = 7
        return width
    }
    return inner(3) .. "|" .. shadowed().toString() .. "|" .. width.toString()
}

print(run())
