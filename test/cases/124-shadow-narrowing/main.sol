module shadow
class Box {
    public static new(): Self {
        return Self {}
    }
}
class Main {
    public static run(args: String...): Long {
        let b: Box? = Box.new()
        if b != null {
            let b: Box? = null
            let c: Box = b
        }
        return 0
    }
}
