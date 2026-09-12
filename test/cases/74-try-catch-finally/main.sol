package trycf

class Foo {

    public static guarded(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            return 2
        } finally {
            System.out().println("guarded finally")
        }
    }

    public static fallthrough(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            System.out().println("caught")
        }
        System.out().println("after try")
        return 3
    }

    public static passthrough(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("pass") }
            return 1
        } catch (e: Exception) {
            System.out().println("caught2")
        } finally {
            System.out().println("pt finally")
        }
        return 4
    }
}

class Main {

    public static run(args: String...): Long {
        System.out().println("g1=" .. Foo.guarded(false))
        System.out().println("g2=" .. Foo.guarded(true))
        System.out().println("f1=" .. Foo.fallthrough(true))
        System.out().println("f2=" .. Foo.fallthrough(false))
        System.out().println("p1=" .. Foo.passthrough(true))
        System.out().println("p2=" .. Foo.passthrough(false))
        return 0
    }
}
