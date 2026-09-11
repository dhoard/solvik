package constdivzero

class Main {

    public static run(args: String...): Long {
        // A constant expression that divides by zero must still fail at
        // runtime; the optimizer must not fold it away.
        let x: Long = 1 / 0
        return x
    }
}
