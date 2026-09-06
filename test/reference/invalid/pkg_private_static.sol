// expected C120: private associated function is not visible cross-package
package reference_invalid

use file:../multipkg_lib/lib

func main() -> Int {
    lib.User.hidden()
    return 0
}
