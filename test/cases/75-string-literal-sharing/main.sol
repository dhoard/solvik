package literalsharing

enum Text {

    item(String)
}

class Main {

    public static literal(): String {
        return "héllo"
    }

    public static same<T>(a: T, b: T): Boolean {
        return a == b
    }

    public static run(args: String...): Long {
        let literal: String = Main.literal()
        let runtime: String = "hé" .. "llo"
        System.getOut().println(literal == runtime)
        System.getOut().println(literal != "different")
        System.getOut().println(Main.same(literal, runtime))
        let map: Map<String, Long> = { "héllo": 7 }
        System.getOut().println(map.get(runtime))
        map.put(runtime, 8)
        System.getOut().println(map.size())
        System.getOut().println(map.get(literal))
        let set: Set<String> = Set<String>.new()
        set.add(literal)
        set.add(runtime)
        System.getOut().println(set.size())
        System.getOut().println(set.contains(runtime))
        let list: List<String> = [literal, literal]
        System.getOut().println(list.contains(runtime))
        System.getOut().println(Text.item(literal) == Text.item(runtime))
        System.getOut().println(literal.substring(1, 2))
        System.getOut().println(literal .. "!")
        System.getOut().println(Main.literal())
        System.getOut().println("" == "x".substring(0, 0))
        return 0
    }
}
