// The implicit entry point of the default module including a file that declares a named module and
// calling its function through the module name (docs/LANGUAGE_SPEC.md section 20).
include "ModulesLib.sol"

println(math_utils::double(21))
