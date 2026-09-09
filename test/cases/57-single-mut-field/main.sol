package smf

class Counter {
    mut count: Int

    pub static new(): Counter {
        return Self { count: 0 }
    }

    pub tick(): Int {
        count += 1
        return count
    }
}

class Main {
    pub static run(args: String...): Int {
        c: Counter = Counter::new()
        c.tick()
        stdout.println(c.tick())
        return 0
    }
}
