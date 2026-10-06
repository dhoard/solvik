// Named functions as values, indirect invocation, and the fixed function-value operations.
//
// A read of a function declaration in a value position yields a canonical function value: one value
// per declaration, so every reference to one declaration is `===` to every other. `===` on function
// values is reference identity, `hashCode()` is that identity's hash, and every rendering is `func`.
// Calling a function value is an indirect call: the callee is evaluated first, then the arguments
// left to right, and the result is the declaration's result.

func scaled(value: Integer): Integer {
    return value * 3
}

func scale(factor: Integer): func(Integer): Integer {
    return scaled
}

func combine(first: Integer, second: Integer): Integer {
    return first * 10 + second
}

func compose(first: Integer, second: Integer): Integer {
    return first - second
}

func apply(operation: func(Integer, Integer): Integer, left: Integer, right: Integer): Integer {
    return operation(left, right)
}

func shout(): String {
    print("|shout")
    return "shouted"
}

func trace(name: String): Integer {
    print(name)
    return 0
}

// A property may itself hold a function value, and a call through it invokes the stored value.
class Pipeline {
    var mutable stage: func(Integer): Integer = scaled
}

var direct: func(Integer, Integer): Integer = combine
var same: func(Integer, Integer): Integer = combine
var other: func(Integer, Integer): Integer = compose

// A nullable function value — the parenthesized form, since `func(T): U?` instead means a non-null
// function returning a nullable result — is invocable only after refinement.
var mutable optional: (func(Integer): Integer)? = scaled
if (optional != null) {
    println(optional(5))
}

// Identity is per declaration: two bindings of one declaration match, a different declaration does
// not, and equal values carry equal hashes. The spec fixes only that equal values share a hash, so no
// assertion is made that two distinct declarations' hashes differ.
println(direct === same)
println(direct === other)
println(direct.hashCode() == same.hashCode())

// Every rendering of a function value is the fixed string `func`, including through `toString()` and
// the concatenation operator.
println(direct)
print(direct .. "\n")

// Indirect invocation returns the declaration's result, including through a returned value.
println(apply(combine, 2, 4))
println(scale(1)(9))

// Storing a function value in a property and calling through it invokes the stored value.
var pipeline = Pipeline()
println(pipeline.stage(4))

// The callee is evaluated before the arguments, and the arguments left to right.
print("|order")
println(shout() .. apply(combine, trace("a"), trace("b")))
