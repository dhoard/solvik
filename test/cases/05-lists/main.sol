package lists

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [1, 2, 3]
        System.out().println(x.size())
        x.add(4)
        System.out().println(x.get(0))
        System.out().println(x.get(3))
        x.set(0, 10)
        System.out().println(x.get(0))
        System.out().println(x.contains(3))
        System.out().println(x.indexOf(4))
        x.remove(1)
        System.out().println(x.size())
        System.out().println(x.join(","))
        let y: List<Long> = List<Long>.new()
        System.out().println(y.isEmpty())
        for v in x {
            System.out().print(v .. " ")
        }
        System.out().println("")
        return 0
    }
}
