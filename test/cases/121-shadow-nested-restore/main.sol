package shadow
struct Main {
    public func run(args: String...): Long {
        let x: Long = 1
        if true {
            let x: Long = 2
            System.getOut().println(x)
        }
        System.getOut().println(x)
        return 0
    }
}
