class Box<T> {
    var value: T
    Box(value: T) {
        this.value = value
    }
    func get(): T {
        return this.value
    }
}
func first<T>(a: T, b: T): T {
    return a
}
val ints: Box<Int> = Box(7)
val texts: Box<String> = Box("hi")
println(ints.get())
println(texts.get())
println(first(1, 2))
println(first("a", "b"))
