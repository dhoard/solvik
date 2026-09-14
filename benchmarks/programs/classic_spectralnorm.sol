package bench.classic.spectralnorm

// Spectral-norm power-iteration kernel (Matrix Mult 1.2 style). The matrix
// A is never materialized; A[i][j] = 1/(i+j+1) is recomputed on demand.
// Each power iteration applies A then A^T via dense matrix-vector products
// over List<Double>, exercising floating-point arithmetic, nested loops,
// collection reads/writes, and function-call overhead inside numeric loops.
//
// Problem size: n = 48, 30 power iterations, repeated `repeat_count` times.
// Expected result: 206950 (validated in the harness; see benches/bench.rs).

struct Solver {

    var n: Integer
    var u: List<Double>
    var v: List<Double>

    pub func new(n: Integer): Self {
        return Self { n: n, u: List<Double>.new(), v: List<Double>.new() }
    }

    // A[i][j] = 1 / (i + j + 1).
    pub func a(self, i: Integer, j: Integer): Double {
        return 1.0 / (Double.from(i) + Double.from(j) + 1.0)
    }

    // new_v[i] = sum_j A[i][j] * u[j].
    pub func mat_vec(self, mat: List<Double>, out: List<Double>) {
        var i: Integer = 0
        while i < self.n {
            var acc: Double = 0.0
            var j: Integer = 0
            while j < self.n {
                acc = acc + self.a(i, j) * mat.get(j)
                j += 1
            }
            out.set(i, acc)
            i += 1
        }
    }

    // Iterative square root via Newton's method for non-negative doubles.
    pub func sqrt(x: Double): Double {
        if x <= 0.0 {
            return 0.0
        }
        var guess: Double = x
        var k: Long = 0
        while k < 100 {
            let next: Double = 0.5 * (guess + x / guess)
            if (next - guess) * (next - guess) <= 1.0e-30 {
                break
            }
            guess = next
            k += 1
        }
        return guess
    }

    pub func run_once(self): Double {
        var i: Integer = 0
        while i < self.n {
            self.u.add(1.0)
            self.v.add(0.0)
            i += 1
        }
        self.mat_vec(self.u, self.v)
        var iter: Long = 0
        while iter < 30 {
            let new_v: List<Double> = List<Double>.new()
            var k: Integer = 0
            while k < self.n {
                new_v.add(0.0)
                k += 1
            }
            self.mat_vec(self.v, new_v)
            self.v = new_v
            let new_u: List<Double> = List<Double>.new()
            var m: Integer = 0
            while m < self.n {
                new_u.add(0.0)
                m += 1
            }
            self.mat_vec(self.u, new_u)
            self.u = new_u
            iter += 1
        }
        var at: Double = 0.0
        var bt: Double = 0.0
        var k: Integer = 0
        while k < self.n {
            let vj: Double = self.v.get(k)
            let uj: Double = self.u.get(k)
            at = at + vj * vj
            bt = bt + uj * uj
            k += 1
        }
        return Solver.sqrt(at / bt)
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let repeat_count: Long = 1
        var total: Long = 0
        var rep: Long = 0
        while rep < repeat_count {
            let s: Solver = Solver.new(48)
            let value: Double = s.run_once()
            total += Long.from(value * 100000.0)
            rep += 1
        }
        return Integer.from(total % 1000003)
    }
}
