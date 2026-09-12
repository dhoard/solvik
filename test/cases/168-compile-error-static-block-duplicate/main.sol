package staticblocks

class A {

    static { System.getOut().println(1) }
    static { System.getOut().println(2) }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
