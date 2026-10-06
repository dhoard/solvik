// First-class functions in one program: function types, named references, anonymous functions,
// explicit capture, generic instantiation, and bound method references, each stored, passed, returned,
// compared, rendered, and invoked (docs/LANGUAGE_SPEC.md section 6, "Function values").

include "FirstClassFunctionsLibrary.sol" alias scaling

func identity<T>(value: T): T {
    return value
}

func apply<T>(operation: func(T): T, value: T): T {
    return operation(value)
}

func thrice(operation: func(Integer): Integer, value: Integer): Integer {
    return operation(operation(operation(value)))
}

func withBoth(operation: func(Integer): Integer): String {
    return operation(1).toString() .. "/" .. operation(2).toString()
}

class Tagger {
    var tag: String = "tag"
    var bonus: Integer = 100

    func attach(value: Integer): String {
        return this.tag .. value.toString()
    }

    // A closure written in a method body captures the receiver to read its state when it runs.
    func bonusBy(): func(Integer): Integer {
        return func [this](value: Integer): Integer {
            return value + this.bonus
        }
    }
}

func makeOffset(base: Integer): func(Integer): Integer {
    return func [base](value: Integer): Integer {
        return value + base
    }
}

// A module in another file exports its named functions as values: a qualified reference is an
// ordinary value, so it initializes a binding whose declared type is a function type.
var twice: func(Integer): Integer = scaling::doubled
println(twice(21))

// A value flows into a parameter of function type and out again as a function result.
println(withBoth(twice))

// One declaration has one value, so a second reference to it is the same value and any other
// declaration is a different one.
var again: func(Integer): Integer = scaling::doubled
var thriceValue: func(Integer): Integer = scaling::tripled
println(again === twice)
println(twice.equals(thriceValue))

// An anonymous function is written in the position a value is expected, and invoking it through a
// parameter is an ordinary call.
var plusOne: func(Integer): Integer = func (value: Integer): Integer {
    return value + 1
}
println(thrice(plusOne, 10))

// A capture list names the values the closure binds when it is created; each call to the factory
// creates a closure over the value that call supplied.
var offsetByTen: func(Integer): Integer = makeOffset(10)
var offsetByTwo: func(Integer): Integer = makeOffset(2)
println(offsetByTen(5))
println(offsetByTwo(5))

// A bound method reference carries its receiver, and invoking one can hand back a closure that
// captured that same receiver.
var bonus: func(Integer): Integer = Tagger().bonusBy()
println(bonus(5))

var attach: func(Integer): String = Tagger().attach
println(attach(7))

// Every function value renders the same way, whatever kind of value it is and however fresh.
println(Tagger().attach.toString())

// A generic function used as a value is instantiated to the type its position expects, and all of its
// instantiations are one value.
var integerIdentity: func(Integer): Integer = identity
var stringIdentity: func(String): String = identity
println(integerIdentity(40))
println(stringIdentity("x"))
println(integerIdentity.equals(stringIdentity))
println(apply(identity, 41))

println(twice.toString())
println(plusOne.toString())
println(offsetByTen.toString())
println(attach.toString())

// A nullable function type holds a function value and refines like any other nullable value.
var mutable optional: (func(Integer): Integer)? = plusOne
if (optional != null) {
    println(optional(4))
}
