func asNumber(v: Any): Number {
    return v as Number
}
val joined: Number = if (true) { 1 } else { 2L }
println(joined is Number)
println(asNumber(5) is Int)
println(asNumber(5.5f) is Float)
println(1 is Any)
println(1 is Number)
println("x" is Number)
