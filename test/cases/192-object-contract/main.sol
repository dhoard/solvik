package objectcontract

struct Point {
    x: Long
    y: Long
    public func new(x: Long, y: Long): Self {
        return Self { x: x, y: y }
    }
}

enum Color {
    red
    green
}

struct Main {
    public func run(args: String...): Long {
        // Ordinary objects: identity equality, identity hash.
        let a: Point = Point.new(1, 2)
        let b: Point = Point.new(1, 2)
        System.getOut().println(a.equals(b))
        System.getOut().println(a.equals(a))
        System.getOut().println(a.equals(null))
        System.getOut().println(a.hashCode() == a.hashCode())

        // Strings: content equality and content hash.
        let s1: String = "hello"
        let s2: String = "hello"
        System.getOut().println(s1.equals(s2))
        System.getOut().println(s1.hashCode() == s2.hashCode())

        // Collections: structural equality and consistent hashes.
        let l1: List<Long> = [1, 2, 3]
        let l2: List<Long> = [1, 2, 3]
        let l3: List<Long> = [3, 2, 1]
        System.getOut().println(l1.equals(l2))
        System.getOut().println(l1.hashCode() == l2.hashCode())
        System.getOut().println(l1.equals(l3))
        let m1: Map<String, Long> = { "a": 1 }
        let m2: Map<String, Long> = { "a": 1 }
        System.getOut().println(m1.equals(m2))
        System.getOut().println(m1.hashCode() == m2.hashCode())

        // Enums: variant plus payload.
        let e1: Color = Color.red
        let e2: Color = Color.red
        System.getOut().println(e1.equals(e2))
        System.getOut().println(e1.hashCode() == e2.hashCode())

        // Object-typed receivers dispatch through the same contract.
        let o: Object = "world"
        System.getOut().println(o.equals("world"))
        return 0
    }
}
