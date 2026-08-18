package test

func main() -> int {
    // === stack creation ===
    s: stack<int> = stack()

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
    top: int = s.peek()
    if top != 30 {
        println("FAIL: peek should be 30, got " .. string(top))
    }
    if s.len() != 3 {
        println("FAIL: peek should not change size")
    }

    // === pop (removes from top) ===
    v1: int = s.pop()
    if v1 != 30 {
        println("FAIL: first pop should be 30, got " .. string(v1))
    }
    v2: int = s.pop()
    if v2 != 20 {
        println("FAIL: second pop should be 20, got " .. string(v2))
    }
    if s.len() != 1 {
        println("FAIL: size should be 1 after two pops")
    }

    // === stack with strings ===
    ss: stack<string> = stack()
    ss.push("hello")
    ss.push("world")
    popped: string = ss.pop()
    if popped != "world" {
        println("FAIL: pop string should be 'world', got " .. popped)
    }

    // === empty stack ===
    empty: stack<int> = stack()
    if empty.isEmpty() == false {
        println("FAIL: new stack should be empty")
    }
    if empty.len() != 0 {
        println("FAIL: empty stack size should be 0")
    }

    // === iteration ===
    iter: stack<int> = stack()
    iter.push(1)
    iter.push(2)
    iter.push(3)
    mut total: int = 0
    for v in iter {
        total = total + v
    }
    if total != 6 {
        println("FAIL: iteration total should be 6, got " .. string(total))
    }

    // === equality ===
    a: stack<int> = stack()
    a.push(1)
    a.push(2)
    b: stack<int> = stack()
    b.push(1)
    b.push(2)
    if a != b {
        println("FAIL: equal stacks should be equal")
    }

    // === nullable ===
    ns: stack<int>? = null
    if ns != null {
        println("FAIL: nullable stack should be null")
    }

    println("stack tests passed")
    return 0
}
