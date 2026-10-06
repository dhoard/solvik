// Solvik range for-in loops: inclusive `...`, ascending-exclusive `..<`, descending-exclusive `..>`.
var mutable inclusive = ""
for (i in 1...5) {
    inclusive = inclusive .. i
}
println(inclusive)

var mutable ascending = ""
for (i in 0..<4) {
    ascending = ascending .. i
}
println(ascending)

var mutable descending = ""
for (i in 5..>0) {
    descending = descending .. i
}
println(descending)

var mutable total = 0
for (i in 1...10) {
    if (i == 3) {
        continue
    }
    if (i > 5) {
        break
    }
    total = total + i
}
println(total)
