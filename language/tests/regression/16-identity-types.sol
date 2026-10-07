class Marker {
}
interface Taggable {
    method tag(): Integer
}
class Tagged implements Taggable {
    method tag(): Integer {
        return 1
    }
}
var m1: Marker = Marker()
var m2: Marker = Marker()
println(m1 === m1)
println(m1 === m2)
var t1: Taggable = Tagged()
var t2: Taggable = t1
println(t1 === t2)
