package trycf

class Foo {
    pub static guarded(x: Bool): Int {
        try {
            if (x) { throw "boom" }
            return 1
        } catch (e) {
            return 2
        } finally {
            stdout.println("guarded finally")
        }
    }

    pub static fallthrough(x: Bool): Int {
        try {
            if (x) { throw "boom" }
            return 1
        } catch (e) {
            stdout.println("caught")
        }
        stdout.println("after try")
        return 3
    }

    pub static passthrough(x: Bool): Int {
        try {
            if (x) { throw "pass" }
            return 1
        } catch (e) {
            stdout.println("caught2")
        } finally {
            stdout.println("pt finally")
        }
        return 4
    }
}

class Main {
    pub static run(args: String...): Int {
        stdout.println("g1=" .. Foo::guarded(false))
        stdout.println("g2=" .. Foo::guarded(true))
        stdout.println("f1=" .. Foo::fallthrough(true))
        stdout.println("f2=" .. Foo::fallthrough(false))
        stdout.println("p1=" .. Foo::passthrough(true))
        stdout.println("p2=" .. Foo::passthrough(false))
        return 0
    }
}
