package trycf

struct Foo {

    public static func guarded(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            return 2
        } finally {
            System.getOut().println("guarded finally")
        }
    }

    public static func fallthrough(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            System.getOut().println("caught")
        }
        System.getOut().println("after try")
        return 3
    }

    public static func passthrough(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("pass") }
            return 1
        } catch (e: Exception) {
            System.getOut().println("caught2")
        } finally {
            System.getOut().println("pt finally")
        }
        return 4
    }
}

struct Main {

    public static func run(args: String...): Long {
        System.getOut().println("g1=" .. Foo.guarded(false))
        System.getOut().println("g2=" .. Foo.guarded(true))
        System.getOut().println("f1=" .. Foo.fallthrough(true))
        System.getOut().println("f2=" .. Foo.fallthrough(false))
        System.getOut().println("p1=" .. Foo.passthrough(true))
        System.getOut().println("p2=" .. Foo.passthrough(false))
        return 0
    }
}
