package concurrentcolls

class Worker implements Runnable {

    list: List<Long>
    map: Map<Long, Long>

    public static new(list: List<Long>, map: Map<Long, Long>): Self {
        return Self { list: list, map: map, }
    }

    public run(): Void {
        let mutable i: Long = 0
        while i < 5000 {
            self.list.add(i)
            self.map.put(i, i * 2)
            i += 1
        }
    }
}

class Main {

    public static run(args: String...): Long {
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
        System.out().println(la.size())
        System.out().println(lb.size())
        System.out().println(ma.size())
        System.out().println(mb.size())
        System.out().println(ma.get(4999) == 9998)
        return 0
    }
}
