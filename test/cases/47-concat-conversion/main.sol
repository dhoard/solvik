package concat
class Main {

    public static left(): Long {
        System.out().print("left ")
        return 7
    }
    public static right(): String {
        System.out().print("right ")
        return "!"
    }
    public static run(args: String...): Long {
        System.out().println("int=" .. 42)
        System.out().println(42 .. "=int")
        System.out().println("values=" .. true .. "," .. 2.5 .. "," .. Char.from(65))
        let n: String? = null
        System.out().println("null=" .. n)
        System.out().println(n .. "=null")
        let x: Object = 9
        System.out().println("object=" .. x)
        let items: List<Long> = [1, 2]
        System.out().println("list=" .. items)
        try {
            throw Exception.new("deep")
        } catch (e: Exception) {
            System.out().println("outer caught " .. e)
        }
        System.out().println(Main.left() .. Main.right())
        for i in 1..3 {
            System.out().print("i=" .. i .. " ")
        }
        System.out().println("")
        return 0
    }
}
