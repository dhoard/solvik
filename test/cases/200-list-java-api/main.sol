package listjavaapi

struct Main {

    public func run(args: String...): Long {
        let l: List<Long> = List<Long>.withCapacity(8)
        l.addAt(0, 1)
        l.addAt(1, 3)
        l.addAt(1, 2)
        System.getOut().println(l.join(","))         // 1,2,3
        let old: Long = l.set(0, 10)
        System.getOut().println(old)                 // 1
        let gone: Long = l.remove(0)
        System.getOut().println(gone)                // 10
        System.getOut().println(l.removeValue(99))   // false
        System.getOut().println(l.removeValue(3))    // true
        let rev: List<Long> = l.reversed()
        System.getOut().println(rev.join(","))       // 2
        rev.add(77)
        System.getOut().println(l.join(","))         // 2 (reversed is a copy)
        let more: List<Long> = [7, 8]
        l.addAll(more)
        System.getOut().println(l.join(","))         // 2,7,8
        return 0
    }
}
