package reference_semantics_valid

// Compile-only coverage of Phase 7 audit fixes: statement termination is
// enforced but semicolons and multiline continuations remain valid.

func main() -> int {
    x: int = 5; y: int = 6
    z: int = x +
        y
    if z != 11 {
        return 1
    }
    w: string = "a"
        .. "b"
    if w != "ab" {
        return 2
    }
    // void functions and void function types remain valid.
    helper()
    cb: func<int, void> = func(n: int) {
        n
    }
    cb(1)
    // main returns int; a void main is also valid.
    return 0
}

func helper() {
    return
}
