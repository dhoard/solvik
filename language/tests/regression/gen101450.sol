val xs1: List<String> = List("b2", "x9")

xs1.add("text0")

println(xs1.size)

println(xs1.get(0))

var total = 0

for (i in 2...4) {
    total = total + i
}

var n = 0

while (n < 3) {
    n = n + 1
}

println(total)

println(n)

func num2(flag: Boolean): Number {
    return if (flag) { 17 } else { 26L }
}

println(num2(true) is Number)

println(num2(true) is Any)
