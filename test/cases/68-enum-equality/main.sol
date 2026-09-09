package enumeqtest

enum E {
    Named(String)
    Num(Int)
    Plain
}

class Main {
    pub static run(args: String...): Int {
        a: E = E::Named("hi")
        b: E = E::Named("hi")
        c: E = E::Named("bye")
        n1: E = E::Num(7)
        n2: E = E::Num(7)
        p1: E = E::Plain
        p2: E = E::Plain
        if a == b { stdout.println("named eq") } else { stdout.println("named NE") }
        if a == c { stdout.println("named2 eq") } else { stdout.println("named2 NE") }
        if n1 == n2 { stdout.println("num eq") } else { stdout.println("num NE") }
        if p1 == p2 { stdout.println("plain eq") } else { stdout.println("plain NE") }
        l: List<E> = List<E>::new()
        l.add(n1)
        if l.contains(n2) { stdout.println("list contains") } else { stdout.println("list no") }
        s: Set<E> = Set<E>::new()
        s.add(a)
        if s.contains(b) { stdout.println("set contains") } else { stdout.println("set no") }
        return 0
    }
}
