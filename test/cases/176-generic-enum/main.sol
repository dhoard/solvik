package genericenum

enum Verdict<T> {

    pass(T)
    fail(String)
}

struct Main {

    pub func run(args: String...): Integer {
        // Type arguments at the use site; payload type is instantiated.
        let v: Verdict<Long> = Verdict<Long>.pass(7)
        match v {
            Verdict.pass(n) => System.getOut().println("pass " .. n)
            Verdict.fail(r) => System.getOut().println("fail " .. r)
        }

        let w: Verdict<String> = Verdict<String>.fail("bad")
        match w {
            Verdict.pass(n) => System.getOut().println("pass " .. n)
            Verdict.fail(r) => System.getOut().println("fail " .. r)
        }

        // Variant identity and payload equality.
        System.getOut().println(w == Verdict<String>.fail("bad"))
        System.getOut().println(w != Verdict<String>.fail("other"))
        return 0
    }
}
