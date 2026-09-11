package maps

class Main {

    public static run(args: String...): Long {
        let m: Map<String, Long> = { "a": 1, "b": 2 }
        System.out().println(m.size())
        m.put("c", 3)
        System.out().println(m.size())
        System.out().println(m.get("a"))
        System.out().println(m.containsKey("b"))
        m.remove("a")
        System.out().println(m.containsKey("a"))
        let keys: List<String> = m.keys()
        System.out().println(keys.size())
        for k in m {
            System.out().print(k .. "=" .. m.get(k) .. " ")
        }
        System.out().println("")
        return 0
    }
}
