// Generic function values: a generic function used as a value is instantiated to one monomorphic
// function type from the expected type in scope, and an unconstrained reference is refused before
// anything runs (LANGUAGE_SPEC.md section 6, "Generic function values").

func identity<T>(value: T): T {
    return value
}

func second<A, B>(first: A, other: B): B {
    return other
}

func wrap<T>(value: T): List<T> {
    return List<T>(value)
}

func apply<T>(f: func(T): T, value: T): T {
    return f(value)
}

func compose<T>(f: func(T): T): func(T): T {
    return f
}

func alsoIdentity<U>(value: U): U {
    return value
}

mutable class Middle {
    func through(transform: func(Integer): Integer, value: Integer): Integer {
        return transform(value)
    }
}

class Bottom extends Middle {
    func viaSuper(): Integer {
        return super.through(identity, 8)
    }
}

class Holder {
    var member: func(Integer): Integer = identity
    static var mutable shared: func(String): String = identity
}

// A declared local type is the expected function type, so each binding gets its own instantiation.
var integerIdentity: func(Integer): Integer = identity
var stringIdentity: func(String): String = identity
println(integerIdentity(41))
println(stringIdentity("ab"))

// A multi-parameter declaration, and one whose parameter appears only in a result position.
var pick: func(Integer, String): String = second
println(pick(1, "two"))
var boxed: func(Integer): List<Integer> = wrap
println(boxed(3).size)

// Positions whose expected types are themselves function types.
var applied: func(func(Integer): Integer, Integer): Integer = apply
println(applied(identity, 42))
var made: func(String): String = compose(identity)
println(made("cd"))

// Property, static property, and assignment positions all supply the expected type.
var holder = Holder()
println(holder.member(5))
Holder.shared = identity
var taken: func(String): String = Holder.shared
println(taken("ef"))

var mutable slot: func(Integer): Integer = identity
slot = identity
println(slot(6))

// An argument position whose callee must infer its own type parameter waits for the other arguments.
println(apply(identity, 43))

// A call the analyzer resolves from a declaration supplies the same expected type: a collection member's
// parameter type comes from the substituted element type, and a `super` call's from the declaration named
// in the qualifier, which section 3 fixes as non-virtual.
var callbacks = List<func(Integer): Integer>()
callbacks.add(identity)
println(callbacks.get(0)(8).toString())
println(Bottom().viaSuper())

// Instantiation changes static typing only: every instantiation of one declaration is one value, and
// a function value is identity-bearing, so two bindings of one declaration are the same value.
var again: func(Integer): Integer = identity
println(integerIdentity === again)
println(integerIdentity.equals(stringIdentity))
println(integerIdentity.hashCode() == stringIdentity.hashCode())

// A different declaration is a different value, whatever its type parameter is named.
var other: func(Integer): Integer = alsoIdentity
println(integerIdentity.equals(other))

// Direct calls keep their existing resolution, with and without written type arguments.
println(identity(9))
println(identity<String>("ten"))

// An already-instantiated value is monomorphic and displays as any other function value.
println(integerIdentity.toString())

// A nullable function type instantiates and remains refinable.
var mutable optional: (func(Integer): Integer)? = identity
if (optional != null) {
    println(optional(7))
}
