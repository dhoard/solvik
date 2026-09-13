package setstackjavaapi

struct Main {

    public static func run(args: String...): Long {
        let s: Set<Long> = Set<Long>.withCapacity(4)
        System.getOut().println(s.add(1))            // true
        System.getOut().println(s.add(1))            // false
        let t: Set<Long> = Set<Long>.new()
        t.add(2)
        t.add(3)
        System.getOut().println(s.addAll(t))         // true
        System.getOut().println(s.containsAll(t))    // true
        System.getOut().println(s.remove(3))         // true
        System.getOut().println(s.containsAll(t))    // false
        let members: List<Long> = s.toList()
        members.sort()
        System.getOut().println(members.join(","))   // 1,2

        let dq: Stack<Long> = Stack<Long>.withCapacity(4)
        dq.addFirst(1)
        dq.addLast(3)
        dq.push(2)
        System.getOut().println(dq.peekFirst() == 1)    // true
        System.getOut().println(dq.peekLast() == 2)     // true
        System.getOut().println(dq.removeFirst() == 1)  // true
        System.getOut().println(dq.pop() == 2)          // true
        System.getOut().println(dq.poll() == 3)         // true
        System.getOut().println(dq.peek() == null)      // true
        System.getOut().println(dq.poll() == null)      // true
        return 0
    }
}
