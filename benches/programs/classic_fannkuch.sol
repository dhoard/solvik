package bench.classic.fannkuch

// Fannkuch (Pfannkuchen) permutation benchmark. For every permutation of
// 1..N it counts the prefix reversals needed to sort the permutation and
// keeps the running maximum. Uses the lexicographic next-permutation
// algorithm plus in-place reversals over a List<Integer>.
//
// Problem size: N = 7 permutations, repeated `repeat_count` times.
// Expected result: 16 (validated in the harness; see benches/bench.rs).

struct Solver {

    mutable n: Integer
    mutable perm: List<Integer>
    mutable maxf: Long

    public func new(n: Integer): Self {
        return Self { n: n, perm: List<Integer>.new(), maxf: 0 }
    }

    // Reverse the leading segment [0 .. k] inclusive of the given list.
    public func reverse_prefix(self, list: List<Integer>, k: Integer): Void {
        let mutable lo: Integer = 0
        let mutable hi: Integer = k
        while lo < hi {
            let tmp: Integer = list.get(lo)
            list.set(lo, list.get(hi))
            list.set(hi, tmp)
            lo += 1
            hi -= 1
        }
    }

    // Count prefix reversals needed to sort this permutation. Works on a
    // fresh scratch copy; self.perm is left unchanged. The permutation and
    // its identity ordering are 1-based: identity is [1, 2, ..., n], and the
    // greedy flip is applied until the smallest element (1) reaches the
    // front.
    public func flip_count(self): Long {
        let p: List<Integer> = List<Integer>.new()
        let mutable i: Integer = 0
        while i < self.n {
            p.add(self.perm.get(i))
            i += 1
        }
        let mutable count: Long = 0
        let mutable first: Integer = p.get(0)
        while first != 1 {
            self.reverse_prefix(p, first - 1)
            count += 1
            first = p.get(0)
        }
        return count
    }

    // Advance self.perm to the next lexicographic permutation. Returns
    // false once the last (descending) permutation has been consumed.
    public func next_permutation(self): Boolean {
        let n: Integer = self.n
        // Find the largest i with perm[i] < perm[i+1].
        let mutable i: Integer = n - 1
        while i > 0 && !(self.perm.get(i - 1) < self.perm.get(i)) {
            i -= 1
        }
        if i <= 0 {
            return false
        }
        // Find the largest j with perm[i-1] < perm[j].
        let mutable j: Integer = n
        while !(self.perm.get(i - 1) < self.perm.get(j - 1)) {
            j -= 1
        }
        // Swap perm[i-1] and perm[j-1].
        let a: Integer = self.perm.get(i - 1)
        let b: Integer = self.perm.get(j - 1)
        self.perm.set(i - 1, b)
        self.perm.set(j - 1, a)
        // Reverse the suffix starting at i.
        let mutable lo: Integer = i
        let mutable hi: Integer = n - 1
        while lo < hi {
            let x: Integer = self.perm.get(lo)
            let y: Integer = self.perm.get(hi)
            self.perm.set(lo, y)
            self.perm.set(hi, x)
            lo += 1
            hi -= 1
        }
        return true
    }

    public func run_once(self): Long {
        self.maxf = 0
        let mutable i: Integer = 1
        while i <= self.n {
            self.perm.add(i)
            i += 1
        }
        let mutable going: Boolean = true
        while going {
            let c: Long = self.flip_count()
            if c > self.maxf {
                self.maxf = c
            }
            going = self.next_permutation()
        }
        return self.maxf
    }
}

struct Main {

    public func run(args: String...): Long {
        let repeat_count: Long = 1
        let mutable total: Long = 0
        let mutable i: Long = 0
        while i < repeat_count {
            let s: Solver = Solver.new(7)
            total += s.run_once()
            i += 1
        }
        return total % 1000003
    }
}
