// expected C121: dependency packages may not reuse built-in namespace names
package reference_invalid

use file:pkg_builtin_name_lib/math
func main() -> int {
    return 0
}
