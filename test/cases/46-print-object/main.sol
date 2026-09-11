package printobject

class Point {

    x: Long
    y: Long

    public static new(x: Long, y: Long): Self {
        return Self { x: x, y: y, }
    }
}

class Main {

    public static run(args: String...): Long {
        // print/println accept any value and apply toString() implicitly.
        System.out().println(42)
        System.out().println(-7)
        System.out().println(2.5)
        System.out().println(true)
        System.out().println(false)
        System.out().println(Char.from(65))
        System.out().println("hello")
        let n: Long? = null
        System.out().println(n)
        let l: List<Long> = [1, 2, 3]
        System.out().println(l)
        let m: Map<String, Long> = { "a": 1 }
        System.out().println(m)
        let p: Point = Point.new(3, 4)
        System.out().println(p)
        System.out().print(1)
        System.out().print(" ")
        System.out().print(2)
        System.out().println("")
        return 0
    }
}
