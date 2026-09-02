// expected C120: private types are not visible cross-package
package reference_invalid

use file:../multipkg_lib/lib

func main() -> int {
    x: lib.Internal = lib.Internal { x: 1 }
    return 0
}
