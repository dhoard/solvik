var total = 0
for (i in 1...5) {
    if (i == 3) {
        continue
    }
    total = total + i
}
println(total)

var n = 0
while (n < 3) {
    n = n + 1
}
println(n)

var acc = ""
for (i in 5..>0) {
    acc = acc .. i
}
println(acc)
