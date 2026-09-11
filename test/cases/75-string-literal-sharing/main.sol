package literalsharing

enum Text {

    item(String)
}

class Main {

    public static literal(): String {
        return "héllo"
    }

    public static same<T>(a: T, b: T): Bool {
        return a == b
    }

    public static run(args: String...): Long {
        let literal: String = Main.literal()
        let runtime: String = "hé" .. "llo"
        System.out().println(literal == runtime)
        System.out().println(literal != "different")
        System.out().println(Main.same(literal, runtime))
        let map: Map<String, Long> = { "héllo": 7 }
        System.out().println(map.get(runtime))
        map.put(runtime, 8)
        System.out().println(map.size())
        System.out().println(map.get(literal))
        let set: Set<String> = Set<String>.new()
        set.add(literal)
        set.add(runtime)
        System.out().println(set.size())
        System.out().println(set.contains(runtime))
        let list: List<String> = [literal, literal]
        System.out().println(list.contains(runtime))
        System.out().println(Text.item(literal) == Text.item(runtime))
        System.out().println(literal.substring(1, 2))
        System.out().println(literal .. "!")
        System.out().println(Main.literal())
        System.out().println("" == "x".substring(0, 0))
        return 0
    }
}
