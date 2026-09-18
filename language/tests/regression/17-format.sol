println(if (true) { "yes" } else { "no" })
var x = 1
x = if (x == 1) { 10 } else { 20 }
println(x)
println(switch (x) {
    case 10:
        "ten"
    default:
        "other"
})
