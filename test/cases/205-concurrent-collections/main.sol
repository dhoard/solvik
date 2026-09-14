package concurrentcolls

struct Worker implements Runnable {

    list: List<Long>
    map: Map<Long, Long>

    public func new(list: List<Long>, map: Map<Long, Long>): Self {
        return Self { list: list, map: map, }
    }

    public func run(self) {
        let mutable i: Long = 0
        while i < 5000 {
            self.list.add(i)
            self.map.put(i, i * 2)
            i += 1
        }
    }
}

struct Main {

    public func run(args: String...): Integer {
        // Independent collections on independent threads: per-collection
        // locking lets both workers progress without a global heap lock.
        let la: List<Long> = List.new()
        let ma: Map<Long, Long> = Map.new()
        let lb: List<Long> = List.new()
        let mb: Map<Long, Long> = Map.new()
        let ta: Thread = Thread.new(Worker.new(la, ma))
        let tb: Thread = Thread.new(Worker.new(lb, mb))
        ta.start()
        tb.start()
        ta.join()
        tb.join()
        System.getOut().println(la.size())
        System.getOut().println(lb.size())
        System.getOut().println(ma.size())
        System.getOut().println(mb.size())
        System.getOut().println(ma.get(4999) == 9998)
        return 0
    }
}
