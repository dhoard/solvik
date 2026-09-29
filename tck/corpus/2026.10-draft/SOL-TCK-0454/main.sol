// Solvik TCK SOL-TCK-0454
// A captured object reference shows mutation that happened after creation, a closure over a closure reaches the inner captured value, and each creation binds the value that existed at that moment
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A capture item is an identifier or `this`. It must resolve at the closure-creation site to one of: a `val` local declared in an enclosing function scope; an immutable parameter of an enclosing function; another function value held by an immutable binding; or `this` in an enclosing instance method or constructor.
//   - Each listed binding's value is captured when evaluation reaches the anonymous-function expression.
//   - Capturing an object copies the reference, not the reachable object graph, so later mutation of that object's `var` properties remains observable through the captured reference.
//   - Capture is transitive only through explicit values.
//   - A closure that captures another closure lists that function-valued binding and stores the function value; it does not duplicate or flatten the captured closure's environment.
//   - In nested closures, a name used in an inner capture list counts as a use by the enclosing closure, so every intervening closure must list and forward that value explicitly.
//   - Top-level and module-qualified function declarations are globally resolved declarations rather than local state and need no capture entry; there are no globals to capture.
//   - Recursion through named top-level functions needs no capture.
//
class Cell {
    var n: Integer = 0

    func bump() {
        this.n = this.n + 1
    }
}

func withFactor(factor: Integer): func(Integer): Integer {
    return func [factor](value: Integer): Integer {
        return value * factor
    }
}

func run(): String {
    val cell = Cell()
    val peek = func [cell](): Integer {
        return cell.n
    }
    cell.bump()
    cell.bump()

    val offset = 100
    val scale = func [offset](value: Integer): Integer {
        return value * offset
    }
    val composed = func [scale](value: Integer): Integer {
        return scale(value) + 1
    }

    val observed = peek().toString()
    val scaled = scale(2).toString()
    val throughStored = composed(3).toString()
    val firstCreation = withFactor(4)(5).toString()
    val secondCreation = withFactor(2)(5).toString()
    return observed .. "|" .. scaled .. "|" .. throughStored .. "|" .. firstCreation .. "|" .. secondCreation
}

print(run())
