// test/logical_op_test.sol — && and || short-circuit operator tests
//
// Regression: && and || must always leave exactly one bool on the stack,
// even when short-circuiting.

package test

func getVal() -> Int {
    return [10, 20, 30][0]
}

func main() -> Int {
    // --- && with bool literals ---
    if false && true {
        println("FAIL: false && true should be false")
    }
    if false && false {
        println("FAIL: false && false should be false")
    }
    if true && true {
        println("PASS: true && true")
    }
    if true && false {
        println("FAIL: true && false should be false")
    }

    // --- || with bool literals ---
    if true || false {
        println("PASS: true || false")
    }
    if false || true {
        println("PASS: false || true")
    }
    if false || false {
        println("FAIL: false || false should be false")
    }
    if true || true {
        println("PASS: true || true")
    }

    // --- && with function result and comparison ---
    x: Int = getVal()
    if x != 10 && x != 20 {
        println("FAIL: x=10, x!=10 && x!=20 should be false")
    }

    y: Int = getVal()
    if y == 10 && y != 20 {
        println("PASS: y=10, y==10 && y!=20")
    }

    // --- || with function result and comparison ---
    z: Int = getVal()
    if z == 10 || z == 20 {
        println("PASS: z=10, z==10 || z==20")
    }

    w: Int = getVal()
    if w != 10 || w == 30 {
        println("FAIL: w=10, w!=10 || w==30 should be false")
    }

    // --- && result assignable to variable ---
    a: Bool = false && true
    if a != false {
        println("FAIL: (false && true) should be false")
    }

    b: Bool = true && true
    if b != true {
        println("FAIL: (true && true) should be true")
    }

    // --- || result assignable to variable ---
    c: Bool = false || false
    if c != false {
        println("FAIL: (false || false) should be false")
    }

    d: Bool = false || true
    if d != true {
        println("FAIL: (false || true) should be true")
    }

    // --- chained && with3 operands ---
    n: Int = 5
    if n != 1 && n != 2 && n != 3 {
        println("PASS: n=5, n!=1 && n!=2 && n!=3")
    }

    m: Int = getVal()
    if m != 1 && m != 2 && m != 3 {
        println("PASS: m=10 (from func), m!=1 && m!=2 && m!=3")
    }

    // --- chained || with 3 operands ---
    p: Int = 2
    if p == 1 || p == 2 || p == 3 {
        println("PASS: p=2, p==1 || p==2 || p==3")
    }

    // --- && with string comparisons from list ---
    names: List<String> = ["Alice", "Bob"]
    name: String = names[0]
    if name != "Alice" && name != "Bob" {
        println("FAIL: name=Alice, should match")
    }
    if name == "Alice" && name != "Bob" {
        println("PASS: name=Alice, name==Alice && name!=Bob")
    }

    println("logical_op tests passed")
    return 0
}
