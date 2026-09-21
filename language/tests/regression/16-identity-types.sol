class Marker {
}
interface Taggable {
    func tag(): Integer
}
class Tagged implements Taggable {
    func tag(): Integer {
        return 1
    }
}
val m1 = Marker()
val m2 = Marker()
println(m1 === m1)
println(m1 === m2)
val t1: Taggable = Tagged()
val t2: Taggable = t1
println(t1 === t2)
