module smf

class Counter {

    mutable count: Long

    public static new(): Self {
        return Self { count: 0, }
    }

    public tick(): Long {
        self.count += 1
        return self.count
    }
}

class Main {

    public static run(args: String...): Long {
        let c: Counter = Counter.new()
        c.tick()
        stdout.println(c.tick())
        return 0
    }
}
