// Explicit immutable closure capture: a `func [a, b](params) { body }` expression binds the named
// imvar mutableues into the closure at creation, and the body may reach no other enclosing-function
// state. `this` is captured the same way, written `func [this](params) { body }`.
//
// Captures bind values rather than storage locations, so a captured object reference observes later
// mutation while a captured Integer keeps the value it had when the closure was created. A closure
// outlives the function that created it. A closure that captures another closure stores that function
// value and does not flatten its environment, and in nested closures every intervening closure has to
// list and forward a name explicitly.

class Cell {
    var mutable n: Integer = 0
}

class Adder {
    var mutable base: Integer = 0

    func set(value: Integer) {
        this.base = value
    }

    // A closure inside a method captures the receiver to call back into the object.
    func adder(): func(Integer): Integer {
        return func [this](value: Integer): Integer {
            return this.base + value
        }
    }

    // A nested closure re-lists `this` to carry the receiver one level deeper.
    func nested(): func(): func(): Integer {
        return func [this](): func(): Integer {
            return func [this](): Integer {
                return this.base
            }
        }
    }
}

// A parameter of the enclosing function is capturable, exactly like a local.
func addTo(base: Integer): func(Integer): Integer {
    return func [base](value: Integer): Integer {
        return base + value
    }
}

println(addTo(40)(2))

// A captured object reference copies the reference, so mutation after creation is observed.
var cell = Cell()
var readCell: func(): Integer = func [cell](): Integer {
    return cell.n
}
cell.n = 7
println(readCell())

// A captured Integer keeps the value it had at creation, so the two closures disagree by design.
var seed: Integer = 1
var before: func(): Integer = func [seed](): Integer {
    return seed
}
var seed2: Integer = 2
var after: func(): Integer = func [seed, seed2](): Integer {
    return seed + seed2
}
println(before())
println(after())

// The closure stays callable after the function that created it returns.
func make(seed: Integer): func(): Integer {
    return func [seed](): Integer {
        return seed + 1
    }
}
var made = make(41)
println(made())

// Two closures built by one creator carry separate captured values.
println(make(1)() + make(2)())

// A closure that captures a closure stores that function value; the inner closure still uses its own
// captures, which the outer closure never mentions.
func chained(): Integer {
    var start: Integer = 3
    var inner: func(): Integer = func [start](): Integer {
        return start * 2
    }
    var middle: func(): Integer = func [inner](): Integer {
        return inner() + 1
    }
    var outer: func(): Integer = func [middle](): Integer {
        return middle() + 100
    }
    return outer()
}
println(chained())

// The captured closure is stored as the same value, so it can be handed back unchanged.
func identityHolds(): Boolean {
    var inner: func(): Integer = func(): Integer {
        return 1
    }
    var forward: func(): func(): Integer = func [inner](): func(): Integer {
        return inner
    }
    return forward() === inner
}
println(identityHolds())

// A name used in an inner capture list counts as a use by the enclosing closure, so the enclosing
// closure must list it too even though its own body never names it.
func forward(factor: Integer): func(): func(): Integer {
    return func [factor](): func(): Integer {
        return func [factor](): Integer {
            return factor
        }
    }
}
println(forward(9)()())

// A closure body may use `this` only when `[this]` is written, and the receiver it sees is the
// instance that created it.
var adder = Adder()
adder.set(10)
var add = adder.adder()
println(add(5))

// A captured `this` obeys reference semantics, so the same closure reports the new state.
adder.set(100)
println(add(5))
println(adder.nested()()())

// A closure body calls a captured function value: an indirect call made from inside another one.
func applied(): Integer {
    var twice: func(Integer): Integer = func(value: Integer): Integer {
        return value * 2
    }
    var offset: Integer = 7
    var caller: func(Integer): Integer = func [twice, offset](value: Integer): Integer {
        return twice(value) + offset
    }
    return caller(5)
}
println(applied())

// Top-level declarations resolve globally and need no capture entry, so recursion works from a body
// with an empty list.
func factorial(n: Integer): Integer {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}
var viaClosure: func(Integer): Integer = func(n: Integer): Integer {
    return factorial(n)
}
println(viaClosure(5))

// Each evaluation of an expression produces a new value, capture list or not.
func distinct(): Boolean {
    var held: Integer = 1
    var first: func(): Integer = func [held](): Integer {
        return held
    }
    var second: func(): Integer = func [held](): Integer {
        return held
    }
    return first !== second
}
println(distinct())
