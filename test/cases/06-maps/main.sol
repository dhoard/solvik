module maps

class Main {

    public static run(args: String...): Long {
        let m: Map<String, Long> = { "a": 1, "b": 2 }
        stdout.println(m.size())
        m.put("c", 3)
        stdout.println(m.size())
        stdout.println(m.get("a"))
        stdout.println(m.containsKey("b"))
        m.remove("a")
        stdout.println(m.containsKey("a"))
        let keys: List<String> = m.keys()
        stdout.println(keys.size())
        for k in m {
            stdout.print(k .. "=" .. m.get(k) .. " ")
        }
        stdout.println("")
        return 0
    }
}
