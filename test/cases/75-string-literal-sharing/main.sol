package literalsharing

enum Text {
    Item(String)
}

class Main {
    pub static literal(): String {
        return "héllo"
    }

    pub static same<T>(a: T, b: T): Bool {
        return a == b
    }

    pub static run(args: String...): Int {
        literal: String = Main::literal()
        runtime: String = "hé" .. "llo"
        stdout.println(literal == runtime)
        stdout.println(literal != "different")
        stdout.println(Main::same(literal, runtime))
        map: Map<String, Int> = { "héllo": 7 }
        stdout.println(map.get(runtime))
        map.put(runtime, 8)
        stdout.println(map.size())
        stdout.println(map.get(literal))
        set: Set<String> = Set<String>::new()
        set.add(literal)
        set.add(runtime)
        stdout.println(set.size())
        stdout.println(set.contains(runtime))
        list: List<String> = [literal, literal]
        stdout.println(list.contains(runtime))
        stdout.println(Text::Item(literal) == Text::Item(runtime))
        stdout.println(literal.substring(1, 2))
        stdout.println(literal .. "!")
        stdout.println(Main::literal())
        stdout.println("" == "x".substring(0, 0))
        return 0
    }
}
