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

    mutable n: Integer
    mutable u: List<Double>
    mutable v: List<Double>

    public func new(n: Integer): Self {
        return Self { n: n, u: List<Double>.new(), v: List<Double>.new() }
    }

    // A[i][j] = 1 / (i + j + 1).
    public func a(self, i: Integer, j: Integer): Double {
        return 1.0 / (Double.from(i) + Double.from(j) + 1.0)
    }

    // new_v[i] = sum_j A[i][j] * u[j].
    public func mat_vec(self, mat: List<Double>, out: List<Double>) {
        let mutable i: Integer = 0
        while i < self.n {
            let mutable acc: Double = 0.0
            let mutable j: Integer = 0
            while j < self.n {
                acc = acc + self.a(i, j) * mat.get(j)
                j += 1
            }
            out.set(i, acc)
            i += 1
        }
    }

    // Iterative square root via Newton's method for non-negative doubles.
    public func sqrt(x: Double): Double {
        if x <= 0.0 {
            return 0.0
        }
        let mutable guess: Double = x
        let mutable k: Long = 0
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

    public func run_once(self): Double {
        let mutable i: Integer = 0
        while i < self.n {
            self.u.add(1.0)
            self.v.add(0.0)
            i += 1
        }
        self.mat_vec(self.u, self.v)
        let mutable iter: Long = 0
        while iter < 30 {
            let new_v: List<Double> = List<Double>.new()
            let mutable k: Integer = 0
            while k < self.n {
                new_v.add(0.0)
                k += 1
            }
            self.mat_vec(self.v, new_v)
            self.v = new_v
            let new_u: List<Double> = List<Double>.new()
            let mutable m: Integer = 0
            while m < self.n {
                new_u.add(0.0)
                m += 1
            }
            self.mat_vec(self.u, new_u)
            self.u = new_u
            iter += 1
        }
        let mutable at: Double = 0.0
        let mutable bt: Double = 0.0
        let mutable k: Integer = 0
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

    public func run(args: String...): Integer {
        let repeat_count: Long = 1
        let mutable total: Long = 0
        let mutable rep: Long = 0
        while rep < repeat_count {
            let s: Solver = Solver.new(48)
            let value: Double = s.run_once()
            total += Long.from(value * 100000.0)
            rep += 1
        }
        return Integer.from(total % 1000003)
    }
}
