package ifacesigok

interface Greeter {
    greet(name: String): String
    farewell(name: String): String {
        return "bye " .. name
    }
}

class Bot implements Greeter {
    pub static new(): Bot { return Self {} }
    // widening a parameter is sound (callers pass String, impl accepts String?)
    pub greet(name: String?): String {
        return "hi " .. (name ?? "?")
    }
}

class Main {
    pub static run(args: String...): Int {
        g: Greeter = Bot::new()
        stdout.println(g.greet("world"))
        stdout.println(g.farewell("bob"))
        return 0
    }
}
