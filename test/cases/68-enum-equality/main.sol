package enumeqtest

enum E {

    named(String)
    num(Long)
    plain
}

class Main {

    public static run(args: String...): Long {
        let a: E = E.named("hi")
        let b: E = E.named("hi")
        let c: E = E.named("bye")
        let n1: E = E.num(7)
        let n2: E = E.num(7)
        let p1: E = E.plain
        let p2: E = E.plain
        if a == b { System.out().println("named eq") } else { System.out().println("named NE") }
        if a == c { System.out().println("named2 eq") } else { System.out().println("named2 NE") }
        if n1 == n2 { System.out().println("num eq") } else { System.out().println("num NE") }
        if p1 == p2 { System.out().println("plain eq") } else { System.out().println("plain NE") }
        let l: List<E> = List<E>.new()
        l.add(n1)
        if l.contains(n2) { System.out().println("list contains") } else { System.out().println("list no") }
        let s: Set<E> = Set<E>.new()
        s.add(a)
        if s.contains(b) { System.out().println("set contains") } else { System.out().println("set no") }
        return 0
    }
}
