package example

def combine(a: string, b: string) -> string {
    return a + b
}

def combineThree(a: string, b: string, c: string) -> string {
    return a + b + c
}

def greet() -> string {
    return "hello"
}

def identity(x: string) -> string {
    return x
}

def main() -> int {
    // Test 1: Single argument with trailing comma
    print("hello",)

    // Test 2: Single argument without trailing comma
    print("hello")

    // Test 3: Multiple arguments with trailing comma
    result: string = combine("a", "b",)
    print(result)

    // Test 4: Multiple arguments without trailing comma
    result = combine("a", "b")
    print(result)

    // Test 5: Three arguments with trailing comma
    result = combineThree("x", "y", "z",)
    print(result)

    // Test 6: Empty call
    print(greet())

    // Test 7: Multiline with trailing comma
    result = combineThree(
        "a",
        "b",
        "c",
    )
    print(result)

    // Test 8: Expression as final arg with trailing comma
    result = combine("hello", string(42),)
    print(result)

    // Test 9: Nested calls with trailing commas
    result = combine(
        identity("inner",),
        "outer",
    )
    print(result)

    // Test 10: Verify evaluation order
    print("all tests passed")
    return 0
}
