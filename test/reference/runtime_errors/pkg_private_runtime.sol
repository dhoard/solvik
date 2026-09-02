// expected E070: private access through an untyped (any) value
package runtime_errors

use file:../multipkg_lib/lib

func main() -> int {
    x: any = lib.makeUser("a")
    println(x.password)
    return 0
}
