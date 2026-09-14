package concat
struct Main {

    public func left(): Long {
        System.getOut().print("left ")
        return 7
    }
    public func right(): String {
        System.getOut().print("right ")
        return "!"
    }
    public func run(args: String...): Integer {
        System.getOut().println("int=" .. 42)
        System.getOut().println(42 .. "=int")
        System.getOut().println("values=" .. true .. "," .. 2.5 .. "," .. Char.from(65))
        let n: String? = null
        System.getOut().println("null=" .. n)
        System.getOut().println(n .. "=null")
        let x: Object = 9
        System.getOut().println("object=" .. x)
        let items: List<Long> = [1, 2]
        System.getOut().println("list=" .. items)
        try {
            throw Exception.new("deep")
        } catch (e: Exception) {
            System.getOut().println("outer caught " .. e)
        }
        System.getOut().println(Main.left() .. Main.right())
        for i in 1..3 {
            System.getOut().print("i=" .. i .. " ")
        }
        System.getOut().println("")
        return 0
    }
}
