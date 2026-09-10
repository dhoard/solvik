module ifacesigok

interface Greeter {

    greet(name: String): String
    farewell(name: String): String {
        return "bye " .. name
    }
}

class Bot implements Greeter {

    public static new(): Self { return Self {} }
    // widening a parameter is sound (callers pass String, impl accepts String?)
    public greet(name: String?): String {
        return "hi " .. (name ?? "?")
    }
}

class Main {

    public static run(args: String...): Long {
        let g: Greeter = Bot.new()
        stdout.println(g.greet("world"))
        stdout.println(g.farewell("bob"))
        return 0
    }
}
