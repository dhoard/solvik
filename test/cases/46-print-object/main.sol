package printobject

class Point {
    pub x: Int
    pub y: Int

    pub static new(x: Int, y: Int): Self {
        return Self { x: x, y: y }
    }
}

class Main {
    pub static run(args: String...): Int {
        // print/println accept any value and apply toString() implicitly.
        stdout.println(42)
        stdout.println(-7)
        stdout.println(2.5)
        stdout.println(true)
        stdout.println(false)
        stdout.println(Char::from(65))
        stdout.println("hello")
        n: Int? = null
        stdout.println(n)
        l: List<Int> = [1, 2, 3]
        stdout.println(l)
        m: Map<String, Int> = { "a": 1 }
        stdout.println(m)
        p: Point = Point::new(3, 4)
        stdout.println(p)
        stdout.print(1)
        stdout.print(" ")
        stdout.print(2)
        stdout.println("")
        return 0
    }
}
