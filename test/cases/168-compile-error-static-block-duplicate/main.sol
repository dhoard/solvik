package staticblocks

class A {

    static { System.out().println(1) }
    static { System.out().println(2) }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
