package ifacesigok

trait Greeter {

    func greet(self, name: String): String
    func farewell(self, name: String): String {
        return "bye " .. name
    }
}

struct Bot implements Greeter {

    pub func new(): Self { return Self {} }
    // widening a parameter is sound (callers pass String, impl accepts String?)
    pub func greet(self, name: String?): String {
        return "hi " .. (name ?? "?")
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let g: Greeter = Bot.new()
        System.getOut().println(g.greet("world"))
        System.getOut().println(g.farewell("bob"))
        return 0
    }
}
