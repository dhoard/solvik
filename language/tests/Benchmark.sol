// Solvik performance smoke program: a tight checked-integer loop used to sanity-check that the
// primitive specializations stay on the fast path. SolvikProgramTest runs it against Benchmark.output.
// Collection performance benchmarks live in the top-level benchmarks/ directory; see benchmarks/README.md.
func sumTo(limit: Integer): Integer {
    var mutable total: Integer = 0
    {
        var mutable i: Integer = 1
        while (i <= limit) {
            total = total + i
            i = i + 1
        }
    }
    return total
}

println(sumTo(60000))
