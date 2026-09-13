package mapjavaapi

struct Main {

    public func run(args: String...): Long {
        let m: Map<String, Long> = Map<String, Long>.withCapacity(4)
        let absent: Long? = m.get("nope")
        System.getOut().println(absent == null)                 // true
        let prev: Long? = m.put("a", 1)
        System.getOut().println(prev == null)                   // true
        let prev2: Long? = m.put("a", 2)
        System.getOut().println(prev2 == 1)                     // true
        System.getOut().println(m.getOrDefault("a", 42))        // 2
        System.getOut().println(m.getOrDefault("zz", 42))       // 42
        System.getOut().println(m.putIfAbsent("a", 9) == null)  // true
        System.getOut().println(m.putIfAbsent("b", 9) == null)  // false
        System.getOut().println(m.replace("a", 3) == 2)         // true
        System.getOut().println(m.replace("zz", 3) == null)     // true
        System.getOut().println(m.containsValue(3))             // true
        System.getOut().println(m.removeMapping("a", 99))       // false
        System.getOut().println(m.removeMapping("a", 3))        // true
        let src: Map<String, Long> = { "p": 5, "q": 6 }
        m.putAll(src)
        System.getOut().println(m.size())                       // 3
        System.getOut().println(m.remove("p") == 5)             // true
        return 0
    }
}
