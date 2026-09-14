package bench.classic.tak

// Takeuchi's function: the canonical stress test for recursive call and
// return machinery with very high call pressure and nested recursion.
//
// Problem size: tak(12, 6, 0), repeated `repeat_count` times.
// Expected result: 1 (validated in the harness; see benches/bench.rs).

struct Tak {

    pub func tak(x: Long, y: Long, z: Long): Long {
        if x <= y {
            return z
        }
        return Tak.tak(
            Tak.tak(x - 1, y, z),
            Tak.tak(y - 1, z, x),
            Tak.tak(z - 1, x, y)
        )
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let repeat_count: Long = 1
        var total: Long = 0
        var i: Long = 0
        while i < repeat_count {
            total += Tak.tak(12, 6, 0)
            i += 1
        }
        return Integer.from(total % 1000003)
    }
}
