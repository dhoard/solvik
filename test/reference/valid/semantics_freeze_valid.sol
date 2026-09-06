package reference_semantics_valid

// Compile-only coverage of Phase 7 audit fixes: statement termination is
// enforced but semicolons and multiline continuations remain valid.

func main() -> Int {
    x: Int = 5; y: Int = 6
    z: Int = x +
        y
    if z != 11 {
        return 1
    }
    w: String = "a"
        .. "b"
    if w != "ab" {
        return 2
    }
    // void functions and void function types remain valid.
    helper()
    cb: Func<Int, Void> = func(n: Int) {
        n
    }
    cb(1)
    // main returns int; a void main is also valid.
    return 0
}

func helper() {
    return
}
