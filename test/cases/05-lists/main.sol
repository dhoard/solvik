package lists

struct Main {

    public func run(args: String...): Long {
        let x: List<Long> = [1, 2, 3]
        System.getOut().println(x.size())
        x.add(4)
        System.getOut().println(x.get(0))
        System.getOut().println(x.get(3))
        x.set(0, 10)
        System.getOut().println(x.get(0))
        System.getOut().println(x.contains(3))
        System.getOut().println(x.indexOf(4))
        x.remove(1)
        System.getOut().println(x.size())
        System.getOut().println(x.join(","))
        let y: List<Long> = List<Long>.new()
        System.getOut().println(y.isEmpty())
        for v in x {
            System.getOut().print(v .. " ")
        }
        System.getOut().println("")
        return 0
    }
}
