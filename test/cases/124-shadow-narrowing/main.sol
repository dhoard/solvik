package shadow
struct Box {
    public static func new(): Self {
        return Self {}
    }
}
struct Main {
    public static func run(args: String...): Long {
        let b: Box? = Box.new()
        if b != null {
            let b: Box? = null
            let c: Box = b
        }
        return 0
    }
}
