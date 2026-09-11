package sets

class Main {

    public static run(args: String...): Long {
        let s: Set<Long> = Set<Long>.new()
        System.out().println(s.isEmpty())
        s.add(1)
        s.add(2)
        s.add(1)
        System.out().println(s.size())
        System.out().println(s.contains(1))
        System.out().println(s.contains(3))
        s.remove(1)
        System.out().println(s.contains(1))
        System.out().println(s.size())
        s.add(3)
        s.clear()
        System.out().println(s.isEmpty())

        let w: Set<String> = Set<String>.new()
        w.add("a")
        w.add("b")
        w.add("a")
        System.out().println(w.size())
        System.out().println(w.contains("b"))
        w.remove("b")
        System.out().println(w.size())
        return 0
    }
}
