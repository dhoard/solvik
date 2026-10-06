// Solvik TCK SOL-TCK-0456
// Top-level function declarations are globally resolved and need no capture entry, and a closure body may recurse through one, which no capture list could supply
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A capture item is an identifier or `this`. It must resolve at the closure-creation site to one of: a `var` local declared in an enclosing function scope; an immutable parameter of an enclosing function; another function value held by an immutable binding; or `this` in an enclosing instance method or constructor.
//   - Each listed binding's value is captured when evaluation reaches the anonymous-function expression.
//   - Capturing an object copies the reference, not the reachable object graph, so later mutation of that object's `var mutable` properties remains observable through the captured reference.
//   - Capture is transitive only through explicit values.
//   - A closure that captures another closure lists that function-valued binding and stores the function value; it does not duplicate or flatten the captured closure's environment.
//   - In nested closures, a name used in an inner capture list counts as a use by the enclosing closure, so every intervening closure must list and forward that value explicitly.
//   - Top-level and module-qualified function declarations are globally resolved declarations rather than local state and need no capture entry; there are no globals to capture.
//   - Recursion through named top-level functions needs no capture.
//
// A top-level declaration is globally resolved: not local state, so nothing to capture.
func twice(value: Integer): Integer {
    return value * 2
}

func fact(n: Integer): Integer {
    if (n <= 1) {
        return 1
    }
    return n * fact(n - 1)
}

func run(): String {
    var usesGlobal = func(value: Integer): Integer {
        return twice(value)
    }
    var recursive = func(n: Integer): Integer {
        return fact(n)
    }
    return usesGlobal(21).toString() .. "|" .. recursive(5).toString()
}

print(run())
