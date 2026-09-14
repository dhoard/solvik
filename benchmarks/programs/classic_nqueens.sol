package bench.classic.nqueens

// N-Queens backtracking. Places queens row by row, pruning on column and
// diagonal conflicts, and counts all solutions. Deterministic board size.
//
// Problem size: N = 8, repeated `repeat_count` times.
// Expected result: 92 (validated in the harness; see benches/bench.rs).

struct Solver {

    mutable n: Integer
    mutable count: Long
    mutable cols: List<Integer>

    public func new(): Self {
        return Self {
            n: 8,
            count: 0,
            cols: List<Integer>.new(),
        }
    }

    public func solutions(self): Long {
        return self.count
    }

    public func solve(self, row: Integer): Void {
        if row == self.n {
            self.count += 1
            return
        }
        let mutable col: Integer = 0
        while col < self.n {
            let mutable ok: Boolean = true
            let mutable r: Integer = 0
            while r < row {
                let other: Integer = self.cols.get(r)
                let d: Integer = row - r
                if other == col || d == (col - other) || d == (other - col) {
                    ok = false
                }
                r += 1
            }
            if ok {
                self.cols.add(col)
                self.solve(row + 1)
                self.cols.remove(self.cols.size() - 1)
            }
            col += 1
        }
    }
}

struct Main {

    public func run(args: String...): Integer {
        let repeat_count: Long = 1
        let mutable total: Long = 0
        let mutable i: Long = 0
        while i < repeat_count {
            let s: Solver = Solver.new()
            s.solve(0)
            total += s.solutions()
            i += 1
        }
        return Integer.from(total % 1000003)
    }
}
