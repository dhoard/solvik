package setiteration

class Main {

    public static run(args: String...): Long {
        // for-in over a Set: every member is visited exactly once, in
        // unspecified (hash) order. The sum, visit count, and sorted join
        // are order-independent observations.
        let s: Set<Long> = Set<Long>.new()
        s.add(5)
        s.add(1)
        s.add(3)
        s.add(1)   // duplicate: no effect
        let mutable total: Long = 0
        let mutable visits: Long = 0
        let collected: List<Long> = List<Long>.new()
        for v in s {
            total += v
            visits += 1
            collected.add(v)
        }
        System.out().println(total)                    // 9
        System.out().println(visits)                   // 3
        collected.sort()
        System.out().println(collected.join(","))      // 1,3,5

        // String members.
        let words: Set<String> = Set<String>.new()
        words.add("b")
        words.add("a")
        let mutable found: Long = 0
        for w in words {
            if w == "a" {
                found += 1
            }
        }
        System.out().println(found)                    // 1

        // An empty set iterates zero times.
        let empty: Set<Long> = Set<Long>.new()
        let mutable n: Long = 0
        for v in empty {
            n += 1
        }
        System.out().println(n)                        // 0
        return 0
    }
}
