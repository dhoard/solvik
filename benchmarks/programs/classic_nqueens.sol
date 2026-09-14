package bench.classic.nqueens

// N-Queens backtracking. Places queens row by row, pruning on column and
// diagonal conflicts, and counts all solutions. Deterministic board size.
//
// Problem size: N = 8, repeated `repeat_count` times.
// Expected result: 92 (validated in the harness; see benches/bench.rs).

struct Solver {

    var n: Integer
    var count: Long
    var cols: List<Integer>

    pub func new(): Self {
        return Self {
            n: 8,
            count: 0,
            cols: List<Integer>.new(),
        }
    }

    pub func solutions(self): Long {
        return self.count
    }

    pub func solve(self, row: Integer) {
        if row == self.n {
            self.count += 1
            return
        }
        var col: Integer = 0
        while col < self.n {
            var ok: Boolean = true
            var r: Integer = 0
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

    pub func run(args: String...): Integer {
        let repeat_count: Long = 1
        var total: Long = 0
        var i: Long = 0
        while i < repeat_count {
            let s: Solver = Solver.new()
            s.solve(0)
            total += s.solutions()
            i += 1
        }
        return Integer.from(total % 1000003)
    }
}
