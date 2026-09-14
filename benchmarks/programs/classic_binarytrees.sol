package bench.classic.binarytrees

// Binary-trees allocation benchmark. Builds a long-lived binary tree and
// then repeatedly allocates short-lived trees, traversing each to compute a
// deterministic node count. Stresses object allocation, object field
// initialization/access, recursive traversal, and garbage collection.
//
// Problem size: long-lived depth 15, short-lived depth 12, repeated
// `repeat_count` times. Expected result: 114681 (validated in the harness;
// see benches/bench.rs).

struct Node {

    item: Long
    left: Node?
    right: Node?

    public func new(item: Long, left: Node?, right: Node?): Self {
        return Self { item: item, left: left, right: right, }
    }

    // Build a complete binary tree of the given depth, in post-order.
    public func bottom_up(depth: Long): Node {
        if depth == 0 {
            return Node.new(1, null, null)
        }
        let left: Node = Node.bottom_up(depth - 1)
        let right: Node = Node.bottom_up(depth - 1)
        return Node.new(1, left, right)
    }

    // Sum of item values over the whole tree (nodes hold item 1).
    public func sum_items(self): Long {
        let l: Node? = self.left
        let r: Node? = self.right
        let mutable total: Long = self.item
        if l != null {
            total += l.sum_items()
        }
        if r != null {
            total += r.sum_items()
        }
        return total
    }
}

struct Main {

    public func run(args: String...): Integer {
        let repeat_count: Long = 6
        // Long-lived tree: its nodes must stay reachable for the whole run.
        let long_lived: Node = Node.bottom_up(15)
        let mutable total: Long = 0
        let mutable rep: Long = 0
        while rep < repeat_count {
            // Short-lived tree: becomes unreachable at the end of the
            // iteration, producing garbage for the collector.
            let short_lived: Node = Node.bottom_up(12)
            total += short_lived.sum_items()
            rep += 1
        }
        total += long_lived.sum_items()
        return Integer.from(total % 1000003)
    }
}
