// Solvik TCK SOL-TCK-0481
// `?.` on a null receiver yields null with no bound function and on a non-null receiver the corresponding bound method, which the refined nullable value is then called at
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A normal member reference on a nullable receiver is illegal.
//   - Safe member access produces a nullable function value and evaluates the receiver once:
//   - If the receiver is null the result is null and no bound function is created; if it is non-null the result is the corresponding bound method.
//   - When the receiver's static type is non-null, `?.` retains the non-null function type, matching existing safe-access behavior.
//
class Target {
    func describe(): String {
        return "t"
    }
}

var nothing: Target? = null
var absent: (func(): String)? = nothing?.describe
print(absent)
print("|")
var something: Target? = Target()
var present: (func(): String)? = something?.describe
if (present != null) {
    print(present())
}
