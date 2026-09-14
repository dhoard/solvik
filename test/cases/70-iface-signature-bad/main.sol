package ifacesigbad

trait Greeter {

    func greet(self, name: String): String
}

struct BadGreeter implements Greeter {

    pub func new(): Self { return Self {} }
    pub func greet(self, name: Long): String {
        return "hi"
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let g: Greeter = BadGreeter.new()
        System.getOut().println(g.greet("world"))
        return 0
    }
}
