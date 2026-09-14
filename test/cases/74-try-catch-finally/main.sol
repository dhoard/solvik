package trycf

struct Foo {

    pub func guarded(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            return 2
        } finally {
            System.getOut().println("guarded finally")
        }
    }

    pub func fallthrough(x: Boolean): Long {
        try {
            if (x) { throw Exception.new("boom") }
            return 1
        } catch (e: Exception) {
            System.getOut().println("caught")
        }
        System.getOut().println("after try")
        return 3
    }

    pub func passthrough(x: Boolean): Long {
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

    pub func run(args: String...): Integer {
        System.getOut().println("g1=" .. Foo.guarded(false))
        System.getOut().println("g2=" .. Foo.guarded(true))
        System.getOut().println("f1=" .. Foo.fallthrough(true))
        System.getOut().println("f2=" .. Foo.fallthrough(false))
        System.getOut().println("p1=" .. Foo.passthrough(true))
        System.getOut().println("p2=" .. Foo.passthrough(false))
        return 0
    }
}
