module printobject

class Point {

    public x: Long
    public y: Long

    public static new(x: Long, y: Long): Self {
        return Self { x: x, y: y, }
    }
}

class Main {

    public static run(args: String...): Long {
        // print/println accept any value and apply toString() implicitly.
        stdout.println(42)
        stdout.println(-7)
        stdout.println(2.5)
        stdout.println(true)
        stdout.println(false)
        stdout.println(Char.from(65))
        stdout.println("hello")
        let n: Long? = null
        stdout.println(n)
        let l: List<Long> = [1, 2, 3]
        stdout.println(l)
        let m: Map<String, Long> = { "a": 1 }
        stdout.println(m)
        let p: Point = Point.new(3, 4)
        stdout.println(p)
        stdout.print(1)
        stdout.print(" ")
        stdout.print(2)
        stdout.println("")
        return 0
    }
}
