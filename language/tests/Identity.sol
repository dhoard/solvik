// Solvik semantic equality `==` versus reference identity `===`.
//
// An ordinary class that does not override `equals` uses the root default: reference identity.
// `==` and `===` agree for such a class, but they are typed and compiled differently: `==` accepts
// any assignment-compatible operands and always routes through the shared semantic-equality
// service, while `===` is restricted to identity-bearing types and compares guest references only.

class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}

val first = Point(1, 2)
val second = Point(1, 2)
val aliasPoint = first

// Semantic equality for a class without an `equals` override is reference identity.
println(first == second)
println(first == aliasPoint)

// Reference identity never consults `equals`; `!==` is its exact negation.
println(first === second)
println(first === aliasPoint)
println(first !== aliasPoint)

// Scalars compare by value through `==`. They are not identity-bearing, so `===` is rejected for
// them during semantic analysis.
println(1 == 1)
println("solvik" == "solvik")

// Nullable identity operands, and the null-safe null rules shared with `==`.
val missing: Point? = null
val present: Point? = first
println(missing === null)
println(present !== null)
println(missing == null)
println(present === missing)

// Mutable built-in collections have allocation identity, never structural equality.
val left: List<Integer> = List(1, 2)
val right: List<Integer> = List(1, 2)
val same = left
println(left == right)
println(left === right)
println(left === same)
