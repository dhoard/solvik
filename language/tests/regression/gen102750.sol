var mutable total: Integer = 0

for (i in 1...4) {
    total = total + i
}

var mutable n: Integer = 0

while (n < 5) {
    n = n + 1
}

println(total)

println(n)

var xs1: List<Boolean> = List(true, true)

xs1.add(false)

println(xs1.size)

println(xs1.get(0))
