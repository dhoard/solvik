// Solvik TCK SOL-TCK-0418
// A `List`, a `List` of parenthesized nullable function values, and a `Map` with function-typed values are declared; each is empty, so each size prints 0.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//   - Parentheses are required when nullability applies to the function value itself:
//   - (func(Integer): String)?  // nullable function value
//
val callbacks: List<func(Integer): String> = List()
val optionalCallbacks: List<(func(Integer): String)?> = List()
val factories: Map<String, func(Integer): String> = Map()
print(callbacks.size)
print(optionalCallbacks.size)
print(factories.size)
