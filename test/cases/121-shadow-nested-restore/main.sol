package shadow
struct Main {
    pub func run(args: String...): Integer {
        let x: Long = 1
        if true {
            let x: Long = 2
            System.getOut().println(x)
        }
        System.getOut().println(x)
        return 0
    }
}
