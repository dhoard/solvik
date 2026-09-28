// Solvik TCK SOL-TCK-0424
// An `Integer` is tested against a function type; the written target is the defect the section names, so the rejection is the non-reifiable-target diagnostic.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Function types are not reifiable. A function type used as the target of `is` or `as` is `SOLV-TYPE-025`. A null check may still refine a nullable function type.
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//
val value: Integer = 1
if (value is func(Integer): String) {
    print("matched")
}
print("EXECUTED-INVALID")
