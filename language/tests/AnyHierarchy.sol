// Solvik's Any root hierarchy: every non-null value is assignable to Any, explicit `extends Any`
// is direct root derivation, numeric leaves reach Any through Number, and unrelated values join to
// Any. This example is the published behavior for the corrected root hierarchy.

open class Animal {
    open func speak(): String {
        return "..."
    }
}

class Dog extends Animal {
    override func speak(): String {
        return "woof"
    }
}

class Plain extends Any {
}

enum Color {
    Red
    Green
}

func identity(value: Any): Any {
    return value
}

func noop() {
}

val dog: Any = Dog()
val animal: Animal = Dog()
val color: Any = Color.Red
val joined: Any = if (true) { 1 } else { "text" }
val numeric: Number = if (true) { 1 } else { 2L }
val pattern: Any = Regex("a")
val values: Any = List<Int>(1, 2)
val unit: Any = noop()

println(dog is Any)
println(dog is Animal)
println(dog is Dog)
println(color is Any)
println(color is Color)
println(color is Dog)
println(numeric is Number)
println(numeric is Any)
println("text" is Any)
println("text" is Number)
println(1 is String)
println(pattern is Any)
println(values is Any)
println(unit is Any)
println(unit is Unit)
println(identity(Plain()) is Any)
println(joined is Any)
println(animal.speak())
