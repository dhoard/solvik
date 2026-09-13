package ifacesigbad

interface Greeter {

    func greet(self, name: String): String
}

struct BadGreeter implements Greeter {

    public func new(): Self { return Self {} }
    public func greet(self, name: Long): String {
        return "hi"
    }
}

struct Main {

    public func run(args: String...): Long {
        let g: Greeter = BadGreeter.new()
        System.getOut().println(g.greet("world"))
        return 0
    }
}
