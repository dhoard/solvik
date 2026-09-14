package bench

trait Worker {
    func work(self, x: Long): Long
}

struct Fast implements Worker {
    pub func new(): Self {
        return Self {}
    }
    pub func work(self, x: Long): Long {
        return x * 3 - 1
    }
}

struct Slow implements Worker {
    pub func new(): Self {
        return Self {}
    }
    pub func work(self, x: Long): Long {
        return x * 5 + 2
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let fast: Worker = Fast.new()
        let slow: Worker = Slow.new()
        var total: Long = 0
        var i: Long = 0
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
