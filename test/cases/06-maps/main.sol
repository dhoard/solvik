package maps

struct Main {

    public func run(args: String...): Long {
        let m: Map<String, Long> = { "a": 1, "b": 2 }
        System.getOut().println(m.size())
        m.put("c", 3)
        System.getOut().println(m.size())
        System.getOut().println(m.get("a"))
        System.getOut().println(m.containsKey("b"))
        m.remove("a")
        System.getOut().println(m.containsKey("a"))
        let keys: List<String> = m.keys()
        System.getOut().println(keys.size())
        for k in m {
            System.getOut().print(k .. "=" .. m.get(k) .. " ")
        }
        System.getOut().println("")
        return 0
    }
}
