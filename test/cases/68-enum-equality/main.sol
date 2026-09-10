module enumeqtest

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
        if a == b { stdout.println("named eq") } else { stdout.println("named NE") }
        if a == c { stdout.println("named2 eq") } else { stdout.println("named2 NE") }
        if n1 == n2 { stdout.println("num eq") } else { stdout.println("num NE") }
        if p1 == p2 { stdout.println("plain eq") } else { stdout.println("plain NE") }
        let l: List<E> = List<E>.new()
        l.add(n1)
        if l.contains(n2) { stdout.println("list contains") } else { stdout.println("list no") }
        let s: Set<E> = Set<E>.new()
        s.add(a)
        if s.contains(b) { stdout.println("set contains") } else { stdout.println("set no") }
        return 0
    }
}
