package test

func main() -> Int {
    // === stack creation ===
    s: Stack<Int> = stack()

    // === push ===
    s.push(10)
    s.push(20)
    s.push(30)

    // === size ===
    if s.len() != 3 {
        println("FAIL: size should be 3, got " .. string(s.len()))
    }

    // === isEmpty ===
    if s.isEmpty() {
        println("FAIL: stack should not be empty")
    }

    // === peek (does not remove) ===
    top: Int = s.peek()
    if top != 30 {
        println("FAIL: peek should be 30, got " .. string(top))
    }
    if s.len() != 3 {
        println("FAIL: peek should not change size")
    }

    // === pop (removes from top) ===
    v1: Int = s.pop()
    if v1 != 30 {
        println("FAIL: first pop should be 30, got " .. string(v1))
    }
    v2: Int = s.pop()
    if v2 != 20 {
        println("FAIL: second pop should be 20, got " .. string(v2))
    }
    if s.len() != 1 {
        println("FAIL: size should be 1 after two pops")
    }

    // === stack with strings ===
    ss: Stack<String> = stack()
    ss.push("hello")
    ss.push("world")
    popped: String = ss.pop()
    if popped != "world" {
        println("FAIL: pop string should be 'world', got " .. popped)
    }

    // === empty stack ===
    empty: Stack<Int> = stack()
    if empty.isEmpty() == false {
        println("FAIL: new stack should be empty")
    }
    if empty.len() != 0 {
        println("FAIL: empty stack size should be 0")
    }

    // === iteration ===
    iter: Stack<Int> = stack()
    iter.push(1)
    iter.push(2)
    iter.push(3)
    mut total: Int = 0
    for v in iter {
        total = total + v
    }
    if total != 6 {
        println("FAIL: iteration total should be 6, got " .. string(total))
    }

    // === equality ===
    a: Stack<Int> = stack()
    a.push(1)
    a.push(2)
    b: Stack<Int> = stack()
    b.push(1)
    b.push(2)
    if a != b {
        println("FAIL: equal stacks should be equal")
    }

    // === nullable ===
    ns: Stack<Int>? = null
    if ns != null {
        println("FAIL: nullable stack should be null")
    }

    println("stack tests passed")
    return 0
}
