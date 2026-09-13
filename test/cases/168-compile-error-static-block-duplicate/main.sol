package staticblocks

struct A {

    static { System.getOut().println(1) }
    static { System.getOut().println(2) }
}

struct Main {

    public static func run(args: String...): Long {
        return 0
    }
}
