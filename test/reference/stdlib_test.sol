package reference_stdlib_test
func main() -> int {
    test.assert(true, "always true")
    test.assertTrue(1 == 1)
    test.assertFalse(1 == 2)
    test.assertEq(2 + 2, 4, "arithmetic")
    test.assertNe(2 + 2, 5)
    test.assertEq("a" .. "b", "ab")
    n: int? = null
    test.assertNull(n)
    mut ok: bool = false
    try {
        test.assertEq(1, 2, "should fail")
        return 1
    } catch (e: exception) {
        ok = true
    }
    if !ok {
        return 2
    }
    return 0
}
