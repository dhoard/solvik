package staticblocks

class A {

    static { stdout.println(1) }
    static { stdout.println(2) }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
