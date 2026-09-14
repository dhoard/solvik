package bench.classic.sieve

// Sieve of Eratosthenes using a mutable List<Boolean> as the working array.
// Exercises tight integer loops, indexed collection access, bounds-checked
// writes, and conditional branches.
//
// Problem size: limit = 40000, repeated `repeat_count` times.
// Expected result: 4203 (validated in the harness; see benches/bench.rs).

struct Main {

    pub func run(args: String...): Integer {
        let limit: Integer = 40000
        let repeat_count: Long = 1
        var total: Long = 0
        var i: Long = 0
        while i < repeat_count {
            let is_composite: List<Boolean> = List<Boolean>.new()
            var k: Integer = 0
            while k < limit {
                is_composite.add(false)
                k += 1
            }
            var count: Long = 0
            var p: Integer = 2
            while p < limit {
                if is_composite.get(p) {
                    p += 1
                    continue
                }
                var multiple: Integer = p * p
                while multiple < limit {
                    is_composite.set(multiple, true)
                    multiple += p
                }
                count += 1
                p += 1
            }
            total += count
            i += 1
        }
        return Integer.from(total % 1000003)
    }
}
