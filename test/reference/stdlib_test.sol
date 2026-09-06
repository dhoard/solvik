package reference_stdlib_test
func main() -> Int {
    test.assert(true, "always true")
    test.assertTrue(1 == 1)
    test.assertFalse(1 == 2)
    test.assertEq(2 + 2, 4, "arithmetic")
    test.assertNe(2 + 2, 5)
    test.assertEq("a" .. "b", "ab")
    n: Int? = null
    test.assertNull(n)
    mut ok: Bool = false
    try {
        test.assertEq(1, 2, "should fail")
        return 1
    } catch (e: Exception) {
        ok = true
    }
    if !ok {
        return 2
    }
    return 0
}
