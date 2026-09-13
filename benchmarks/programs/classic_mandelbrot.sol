package bench.classic.mandelbrot

// Mandelbrot iteration checksum. For each pixel it iterates the canonical
// complex map z = z*z + c and counts how many iterations survive before
// escape. The timed loop produces only a checksum (no console output).
//
// Problem size: 100 x 100 pixel grid, max 50 iterations, repeated
// `repeat_count` times. Expected result: 123735 (validated in the harness;
// see benches/bench.rs).

struct Main {

    public func run(args: String...): Long {
        let repeat_count: Long = 1
        let width: Long = 100
        let height: Long = 100
        let max_it: Long = 50
        let mutable total: Long = 0
        let mutable rep: Long = 0
        while rep < repeat_count {
            let mutable iy: Long = 0
            while iy < height {
                let cy: Double = -1.5 + (Double.from(iy) * 3.0) / Double.from(height)
                let mutable ix: Long = 0
                while ix < width {
                    let cx: Double = -2.0 + (Double.from(ix) * 3.0) / Double.from(width)
                    let mutable zx: Double = 0.0
                    let mutable zy: Double = 0.0
                    let mutable it: Long = 0
                    while (zx * zx + zy * zy) < 4.0 && it < max_it {
                        let nzx: Double = zx * zx - zy * zy + cx
                        let nzy: Double = 2.0 * zx * zy + cy
                        zx = nzx
                        zy = nzy
                        it += 1
                    }
                    total += it
                    ix += 1
                }
                iy += 1
            }
            rep += 1
        }
        return total % 1000003
    }
}
