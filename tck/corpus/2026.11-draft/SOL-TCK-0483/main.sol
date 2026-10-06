// Solvik TCK SOL-TCK-0483
// `?.` through a receiver whose static type is non-null keeps the non-null function type, which a *non-nullable* function-typed binding can only accept if the type was retained
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

var target = Target()
var method: func(): String = target?.describe
print(method())
