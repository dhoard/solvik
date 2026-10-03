// Baseline: a counted loop with no collection work. Every other benchmark is read against this.
mutable val total = 0
mutable val i = 0
while (i < 60000000) {
    total = total + 1
    i = i + 1
}
println(total)
