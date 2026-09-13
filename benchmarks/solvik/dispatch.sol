package bench

interface Worker {
    func work(self, x: Long): Long
}

struct Fast implements Worker {
    public func new(): Self {
        return Self {}
    }
    public func work(self, x: Long): Long {
        return x * 3 - 1
    }
}

struct Slow implements Worker {
    public func new(): Self {
        return Self {}
    }
    public func work(self, x: Long): Long {
        return x * 5 + 2
    }
}

struct Main {

    public func run(args: String...): Long {
        let fast: Worker = Fast.new()
        let slow: Worker = Slow.new()
        let mutable total: Long = 0
        let mutable i: Long = 0
        while i < 50000000 {
            if i % 2 == 0 {
                total += fast.work(i)
            } else {
                total += slow.work(i)
            }
            i += 1
        }
        System.getOut().println(total)
        return 0
    }
}
