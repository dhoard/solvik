package sets

struct Main {

    pub func run(args: String...): Integer {
        let s: Set<Long> = Set<Long>.new()
        System.getOut().println(s.isEmpty())
        s.add(1)
        s.add(2)
        s.add(1)
        System.getOut().println(s.size())
        System.getOut().println(s.contains(1))
        System.getOut().println(s.contains(3))
        s.remove(1)
        System.getOut().println(s.contains(1))
        System.getOut().println(s.size())
        s.add(3)
        s.clear()
        System.getOut().println(s.isEmpty())

        let w: Set<String> = Set<String>.new()
        w.add("a")
        w.add("b")
        w.add("a")
        System.getOut().println(w.size())
        System.getOut().println(w.contains("b"))
        w.remove("b")
        System.getOut().println(w.size())
        return 0
    }
}
