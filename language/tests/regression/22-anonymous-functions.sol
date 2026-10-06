// Anonymous function expressions: a `func(params): Return { body }` expression whose value is a
// function value with a new identity on every evaluation, its own function boundary, and its own
// lexical scope.
//
// An anonymous function introduces a boundary, so it has no implicit access to an enclosing
// function's locals (that needs the capture list of a later revision), globals resolve with no entry,
// `return` returns from the body, and `break`/`continue` cannot cross the boundary.

mutable class Animal {
    var name: String = "animal"
}

class Dog extends Animal {
}

func triple(value: Integer): Integer {
    return value * 3
}

func label(value: Integer): String {
    return "v" .. value.toString()
}

// A body may reach a global declaration with no capture entry, and its parameters are its own scope.
var combine: func(Integer): Integer = func(value: Integer): Integer {
    return triple(value) + value
}
println(combine(4))

// An omitted return type declares `Unit`, exactly as in a declaration, and the body runs for effect.
var shout: func(String) = func(value: String) {
    label(1)
}
shout("a")

// Two evaluations of one anonymous-function expression are two distinct values: `==` on function
// values is reference identity, and each fresh value is still callable. The expression is reached
// twice through `maker()`.
var maker: func(): func(): Integer = func(): func(): Integer {
    return func(): Integer {
        return 7
    }
}
println(maker() == maker())
var first = maker()
var again = maker()
println(first() .. again())

// Re-reading a binding that already holds one of those values returns the value it stored, so the
// identity survives a read and an assignment.
var stored: func(): Integer = func(): Integer {
    return 1
}
var copy: func(): Integer = stored
println(stored == copy)

// Every rendering of a function value is the fixed string `func`, whether printed, concatenated, or
// rendered through `toString()`.
println(stored)
print(stored .. "|")
print(stored.toString())
println("")

// A `return` inside a body returns from the body and not from the function that created the closure,
// so the enclosing function still reaches its own `return`.
func created(): String {
    var give: func(): Integer = func(): Integer {
        return 7
    }
    print(give())
    return "after"
}
println(created())

// A `break` in a loop written inside the body targets that loop, since the boundary stops the search
// outward and not the search inside.
var sumTo: func(Integer): Integer = func(limit: Integer): Integer {
    var mutable seen: Integer = 0
    var mutable i: Integer = 0
    while (true) {
        i = i + 1
        if (i > limit) {
            break
        }
        seen = seen + i
    }
    return seen
}
println(sumTo(4))

// A body may shadow an outer binding under the ordinary lexical-scope rules: the inner `base` is the
// body's own local, so the body reads 10 and the outer binding is left alone.
var base: Integer = 3
var compute: func(): Integer = func(): Integer {
    var base: Integer = 10
    return base
}
println(compute() * 100 + base)

// A nested anonymous function is produced and invoked through the outer value, and its body reaches a
// global. Each outer call evaluates the inner expression afresh, so the two values differ.
var outer: func(): func(): String = func(): func(): String {
    return func(): String {
        return label(3)
    }
}
println(outer()())
println(outer() == outer())

// A function-typed property may be initialized with an anonymous function and invoked through the
// receiver.
class Holder {
    var step: func(Integer): Integer = func(value: Integer): Integer {
        return triple(value) + 1
    }
}
var holder = Holder()
println(holder.step(3))

// A guest exception thrown inside a body propagates out of the call untranslated.
class Boom extends RuntimeException {
}
try {
    var raiser: func(): Integer = func(): Integer {
        throw Boom("exploded")
    }
    raiser()
    println("no exception")
}

catch (error: Boom) {
    println("caught " .. error.getMessage())
}

// Function-type assignability applies to an anonymous value as it does to a named one: contravariant
// in parameters, so a value accepting the supertype satisfies the subtype-typed binding.
var asDog: func(Dog): String = func(animal: Animal): String {
    return animal.name
}
println(asDog(Dog()))
