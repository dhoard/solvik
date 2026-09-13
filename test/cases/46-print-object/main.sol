package printobject

struct Point {

    x: Long
    y: Long

    public func new(x: Long, y: Long): Self {
        return Self { x: x, y: y, }
    }
}

struct Main {

    public func run(args: String...): Long {
        // print/println accept any value and apply toString() implicitly.
        System.getOut().println(42)
        System.getOut().println(-7)
        System.getOut().println(2.5)
        System.getOut().println(true)
        System.getOut().println(false)
        System.getOut().println(Char.from(65))
        System.getOut().println("hello")
        let n: Long? = null
        System.getOut().println(n)
        let l: List<Long> = [1, 2, 3]
        System.getOut().println(l)
        let m: Map<String, Long> = { "a": 1 }
        System.getOut().println(m)
        let p: Point = Point.new(3, 4)
        System.getOut().println(p)
        System.getOut().print(1)
        System.getOut().print(" ")
        System.getOut().print(2)
        System.getOut().println("")
        return 0
    }
}
