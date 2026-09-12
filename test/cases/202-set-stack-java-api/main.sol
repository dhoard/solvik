package setstackjavaapi

class Main {

    public static run(args: String...): Long {
        let s: Set<Long> = Set<Long>.withCapacity(4)
        System.out().println(s.add(1))            // true
        System.out().println(s.add(1))            // false
        let t: Set<Long> = Set<Long>.new()
        t.add(2)
        t.add(3)
        System.out().println(s.addAll(t))         // true
        System.out().println(s.containsAll(t))    // true
        System.out().println(s.remove(3))         // true
        System.out().println(s.containsAll(t))    // false
        let members: List<Long> = s.toList()
        members.sort()
        System.out().println(members.join(","))   // 1,2

        let dq: Stack<Long> = Stack<Long>.withCapacity(4)
        dq.addFirst(1)
        dq.addLast(3)
        dq.push(2)
        System.out().println(dq.peekFirst() == 1)    // true
        System.out().println(dq.peekLast() == 2)     // true
        System.out().println(dq.removeFirst() == 1)  // true
        System.out().println(dq.pop() == 2)          // true
        System.out().println(dq.poll() == 3)         // true
        System.out().println(dq.peek() == null)      // true
        System.out().println(dq.poll() == null)      // true
        return 0
    }
}
