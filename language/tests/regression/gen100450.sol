println(5 == 8)

println(5 != 8)

println("b6" == "b6")

println('a' == 'a')

func arith1(a: Float, b: Float): Float {
    return a * b * a
}

println(arith1(6.0f, 58.0f))

println(arith1(64.0f, 6.0f))

func num2(flag: Boolean): Number {
    return if (flag) { 90 } else { 97L }
}

println(num2(true) is Number)

println(num2(true) is Any)
