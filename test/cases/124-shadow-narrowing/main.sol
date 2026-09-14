package shadow
struct Box {
    public func new(): Self {
        return Self {}
    }
}
struct Main {
    public func run(args: String...): Integer {
        let b: Box? = Box.new()
        if b != null {
            let b: Box? = null
            let c: Box = b
        }
        return 0
    }
}
