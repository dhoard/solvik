module ifacesigbad

interface Greeter {

    greet(name: String): String
}

class BadGreeter implements Greeter {

    public static new(): Self { return Self {} }
    public greet(name: Long): String {
        return "hi"
    }
}

class Main {

    public static run(args: String...): Long {
        let g: Greeter = BadGreeter.new()
        stdout.println(g.greet("world"))
        return 0
    }
}
