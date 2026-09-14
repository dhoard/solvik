package constdivzero

struct Main {

    pub func run(args: String...): Integer {
        // A constant expression that divides by zero must still fail at
        // runtime; the optimizer must not fold it away.
        let x: Integer = 1 / 0
        return x
    }
}
