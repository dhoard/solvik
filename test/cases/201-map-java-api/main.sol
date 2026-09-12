package mapjavaapi

class Main {

    public static run(args: String...): Long {
        let m: Map<String, Long> = Map<String, Long>.withCapacity(4)
        let absent: Long? = m.get("nope")
        System.out().println(absent == null)                 // true
        let prev: Long? = m.put("a", 1)
        System.out().println(prev == null)                   // true
        let prev2: Long? = m.put("a", 2)
        System.out().println(prev2 == 1)                     // true
        System.out().println(m.getOrDefault("a", 42))        // 2
        System.out().println(m.getOrDefault("zz", 42))       // 42
        System.out().println(m.putIfAbsent("a", 9) == null)  // true
        System.out().println(m.putIfAbsent("b", 9) == null)  // false
        System.out().println(m.replace("a", 3) == 2)         // true
        System.out().println(m.replace("zz", 3) == null)     // true
        System.out().println(m.containsValue(3))             // true
        System.out().println(m.removeMapping("a", 99))       // false
        System.out().println(m.removeMapping("a", 3))        // true
        let src: Map<String, Long> = { "p": 5, "q": 6 }
        m.putAll(src)
        System.out().println(m.size())                       // 3
        System.out().println(m.remove("p") == 5)             // true
        return 0
    }
}
