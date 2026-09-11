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
        stdout.println(literal == runtime)
        stdout.println(literal != "different")
        stdout.println(Main.same(literal, runtime))
        let map: Map<String, Long> = { "héllo": 7 }
        stdout.println(map.get(runtime))
        map.put(runtime, 8)
        stdout.println(map.size())
        stdout.println(map.get(literal))
        let set: Set<String> = Set<String>.new()
        set.add(literal)
        set.add(runtime)
        stdout.println(set.size())
        stdout.println(set.contains(runtime))
        let list: List<String> = [literal, literal]
        stdout.println(list.contains(runtime))
        stdout.println(Text.item(literal) == Text.item(runtime))
        stdout.println(literal.substring(1, 2))
        stdout.println(literal .. "!")
        stdout.println(Main.literal())
        stdout.println("" == "x".substring(0, 0))
        return 0
    }
}
