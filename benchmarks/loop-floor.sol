// Baseline: a counted loop with no collection work. Every other benchmark is read against this.
var total = 0
var i = 0
while (i < 60000000) {
    total = total + 1
    i = i + 1
}
println(total)
