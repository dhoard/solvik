// expected C120: private members are not visible cross-package
package reference_invalid

use file:../multipkg_lib/lib

func main() -> int {
    u: lib.User = lib.makeUser("a")
    println(u.password)
    return 0
}
