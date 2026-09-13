package enumeqtest

enum E {

    named(String)
    num(Long)
    plain
}

struct Main {

    public static func run(args: String...): Long {
        let a: E = E.named("hi")
        let b: E = E.named("hi")
        let c: E = E.named("bye")
        let n1: E = E.num(7)
        let n2: E = E.num(7)
        let p1: E = E.plain
        let p2: E = E.plain
        if a == b { System.getOut().println("named eq") } else { System.getOut().println("named NE") }
        if a == c { System.getOut().println("named2 eq") } else { System.getOut().println("named2 NE") }
        if n1 == n2 { System.getOut().println("num eq") } else { System.getOut().println("num NE") }
        if p1 == p2 { System.getOut().println("plain eq") } else { System.getOut().println("plain NE") }
        let l: List<E> = List<E>.new()
        l.add(n1)
        if l.contains(n2) { System.getOut().println("list contains") } else { System.getOut().println("list no") }
        let s: Set<E> = Set<E>.new()
        s.add(a)
        if s.contains(b) { System.getOut().println("set contains") } else { System.getOut().println("set no") }
        return 0
    }
}
