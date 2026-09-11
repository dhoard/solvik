package genericenum

enum Verdict<T> {

    pass(T)
    fail(String)
}

class Main {

    public static run(args: String...): Long {
        // Type arguments at the use site; payload type is instantiated.
        let v: Verdict<Long> = Verdict<Long>.pass(7)
        match v {
            Verdict.pass(n) => System.out().println("pass " .. n)
            Verdict.fail(r) => System.out().println("fail " .. r)
        }

        let w: Verdict<String> = Verdict<String>.fail("bad")
        match w {
            Verdict.pass(n) => System.out().println("pass " .. n)
            Verdict.fail(r) => System.out().println("fail " .. r)
        }

        // Variant identity and payload equality.
        System.out().println(w == Verdict<String>.fail("bad"))
        System.out().println(w != Verdict<String>.fail("other"))
        return 0
    }
}
