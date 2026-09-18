func num1(flag: Boolean): Number {
    return if (flag) { 33 } else { 59L }
}

println(num1(true) is Number)

println(num1(true) is Any)

println(0 == 5)

println(0 != 5)

println("b5" == "b5")

println('a' == 'a')

func logic2(a: Boolean, b: Boolean): Boolean {
    return (a && b) || !a
}

println(logic2(true, false))

println(logic2(false, true))
