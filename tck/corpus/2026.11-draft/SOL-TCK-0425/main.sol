// Solvik TCK SOL-TCK-0425
// An `Any` holding an `Integer` is cast to a function type, which the non-reifiability rule forbids independently of the operand's runtime value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Function types are not reifiable. A function type used as the target of `is` or `as` is `SOLV-TYPE-025`. A null check may still refine a nullable function type.
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//
var value: Any = 1
var operation = value as func(Integer): String
print("EXECUTED-INVALID")
