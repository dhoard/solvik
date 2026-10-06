// Any? and the corrected root hierarchy: a nullable Any narrows to non-null Any, unrelated values
// join to Any, a numeric join has static type Number while the executed value keeps its runtime
// type, and a checked cast to Any preserves the runtime value.

class Box {
    var value: Integer

    Box(value: Integer) {
        this.value = value
    }
}

func describe(value: Any?): String {
    if (value == null) {
        return "nothing"
    }
    return value.toString()
}

var boxed: Any = Box(7)
var joined: Any = if (true) {
    1
}
else {
    "text"
}
var numeric: Number = if (true) {
    1
}
else {
    2L
}
var cast: Any = 42 as Any

println(describe(boxed))
println(describe(null))
println(joined is Any)
println(numeric is Number)
println(numeric is Integer)
println(numeric is String)
println(cast is Integer)
println(cast is Any)
