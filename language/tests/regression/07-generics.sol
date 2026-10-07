class Box<T> {
    var mutable value: T
    Box(value: T) {
        this.value = value
    }
    method get(): T {
        return this.value
    }
}
func first<T>(a: T, b: T): T {
    return a
}
var ints: Box<Integer> = Box(7)
var texts: Box<String> = Box("hi")
println(ints.get())
println(texts.get())
println(first(1, 2))
println(first("a", "b"))
