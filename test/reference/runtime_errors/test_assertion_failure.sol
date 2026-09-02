// expected E071: a failed assertion raises a catchable exception
package runtime_errors

func main() -> int {
    test.assertEq(1, 2, "intentional failure")
    return 0
}
