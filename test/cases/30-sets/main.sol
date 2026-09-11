package sets

class Main {

    public static run(args: String...): Long {
        let s: Set<Long> = Set<Long>.new()
        stdout.println(s.isEmpty())
        s.add(1)
        s.add(2)
        s.add(1)
        stdout.println(s.size())
        stdout.println(s.contains(1))
        stdout.println(s.contains(3))
        s.remove(1)
        stdout.println(s.contains(1))
        stdout.println(s.size())
        s.add(3)
        s.clear()
        stdout.println(s.isEmpty())

        let w: Set<String> = Set<String>.new()
        w.add("a")
        w.add("b")
        w.add("a")
        stdout.println(w.size())
        stdout.println(w.contains("b"))
        w.remove("b")
        stdout.println(w.size())
        return 0
    }
}
