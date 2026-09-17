// Solvik performance smoke program: a tight checked-integer loop used to sanity-check that the
// primitive specializations stay on the fast path. It is also run by SolvikExamplesTest.
fun sumTo(limit: Int): Int {
    var total = 0
    for (var i = 1; i <= limit; i = i + 1) {
        total = total + i
    }
    return total
}

fun main(): Unit {
    println(sumTo(60000))
}
