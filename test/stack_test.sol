package test

func main() -> int {
    // === stack creation ===
    s: stack<int> = stack()

    // === push ===
    stack.push(s, 10)
    stack.push(s, 20)
    stack.push(s, 30)

    // === size ===
    if stack.size(s) != 3 {
        println("FAIL: size should be 3, got " .. string(stack.size(s)))
    }

    // === isEmpty ===
    if stack.isEmpty(s) {
        println("FAIL: stack should not be empty")
    }

    // === peek (does not remove) ===
    top: int = stack.peek(s)
    if top != 30 {
        println("FAIL: peek should be 30, got " .. string(top))
    }
    if stack.size(s) != 3 {
        println("FAIL: peek should not change size")
    }

    // === pop (removes from top) ===
    v1: int = stack.pop(s)
    if v1 != 30 {
        println("FAIL: first pop should be 30, got " .. string(v1))
    }
    v2: int = stack.pop(s)
    if v2 != 20 {
        println("FAIL: second pop should be 20, got " .. string(v2))
    }
    if stack.size(s) != 1 {
        println("FAIL: size should be 1 after two pops")
    }

    // === stack with strings ===
    ss: stack<string> = stack()
    stack.push(ss, "hello")
    stack.push(ss, "world")
    popped: string = stack.pop(ss)
    if popped != "world" {
        println("FAIL: pop string should be 'world', got " .. popped)
    }

    // === empty stack ===
    empty: stack<int> = stack()
    if stack.isEmpty(empty) == false {
        println("FAIL: new stack should be empty")
    }
    if stack.size(empty) != 0 {
        println("FAIL: empty stack size should be 0")
    }

    // === iteration ===
    iter: stack<int> = stack()
    stack.push(iter, 1)
    stack.push(iter, 2)
    stack.push(iter, 3)
    mut total: int = 0
    for v in iter {
        total = total + v
    }
    if total != 6 {
        println("FAIL: iteration total should be 6, got " .. string(total))
    }

    // === equality ===
    a: stack<int> = stack()
    stack.push(a, 1)
    stack.push(a, 2)
    b: stack<int> = stack()
    stack.push(b, 1)
    stack.push(b, 2)
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
