package ifacesigbad

interface Greeter {
    greet(name: String): String
}

class BadGreeter implements Greeter {
    pub static new(): BadGreeter { return Self {} }
    pub greet(name: Int): String {
        return "hi"
    }
}

class Main {
    pub static run(args: String...): Int {
        g: Greeter = BadGreeter::new()
        stdout.println(g.greet("world"))
        return 0
    }
}
