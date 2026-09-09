package lists

class Main {
    pub static run(args: String...): Int {
        x: List<Int> = [1, 2, 3]
        stdout.println(x.size())
        x.add(4)
        stdout.println(x.get(0))
        stdout.println(x.get(3))
        x.set(0, 10)
        stdout.println(x.get(0))
        stdout.println(x.contains(3))
        stdout.println(x.indexOf(4))
        x.remove(1)
        stdout.println(x.size())
        stdout.println(x.join(","))
        y: List<Int> = List<Int>::new()
        stdout.println(y.isEmpty())
        for v in x {
            stdout.print(v .. " ")
        }
        stdout.println("")
        return 0
    }
}
