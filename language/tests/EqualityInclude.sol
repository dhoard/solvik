// Multi-file semantic-equality program: include a helper that declares a class with an `equals`
// override and a constant `switch`, then exercise `==`, `===`, `Set`, `Map`, and `switch`.
include "EqualityIncludeLibrary.sol"

val a = Coordinate(1, 2)
val b = Coordinate(1, 2)
val c = a

// `==` and an explicit `equals` call dispatch the override; `===` ignores it.
println(a == b)
println(a.equals(b))
println(a === b)
println(a === c)

// `Set` deduplicates through the shared equality service.
val unique: Set<Coordinate> = Set(a, b)
println(unique.size)

// `Map` uses the same service for key lookup.
val names: Map<Coordinate, String> = Map(a: "origin")
println(names.get(Coordinate(1, 2)))

// Constant `switch` stays consistent with `==`.
println(label("one"))
println(label("two"))
println(label("three"))
