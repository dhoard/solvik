module concat
class Main {

    public static left(): Long {
        stdout.print("left ")
        return 7
    }
    public static right(): String {
        stdout.print("right ")
        return "!"
    }
    public static run(args: String...): Long {
        stdout.println("int=" .. 42)
        stdout.println(42 .. "=int")
        stdout.println("values=" .. true .. "," .. 2.5 .. "," .. Char.from(65))
        n: String? = null
        stdout.println("null=" .. n)
        stdout.println(n .. "=null")
        x: Object = 9
        stdout.println("object=" .. x)
        items: List<Long> = [1, 2]
        stdout.println("list=" .. items)
        try {
            throw "deep"
        } catch (e) {
            stdout.println("outer caught " .. e)
        }
        stdout.println(Main.left() .. Main.right())
        for i in 1..3 {
            stdout.print("i=" .. i .. " ")
        }
        stdout.println("")
        return 0
    }
}
