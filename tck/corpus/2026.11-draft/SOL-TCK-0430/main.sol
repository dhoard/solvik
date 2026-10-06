// Solvik TCK SOL-TCK-0430
// A `func(Long): Long` value is not assignable to `func(Integer): Integer`: widening an `Integer` argument at a conversion site does not relate the two function types.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Function-type assignability is contravariant in parameters and covariant in the result.
//   - Given `mutable class Animal` and `class Dog extends Animal`, a value of type func(Animal): Dog is assignable to func(Dog): Animal, and a value of type func(Dog): Animal is not assignable to func(Animal): Dog.
//   - Numeric widening is not a subtype relation (section 4) and is never applied inside function-type assignability: a function accepting `Long` is not assignable to a function type accepting `Integer` merely because an `Integer` argument may widen at an ordinary conversion site.
//   - Arguments supplied when a function value is invoked still receive the ordinary call-site widening rules.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
func widenResult(value: Long): Long {
    return value + 1
}

// Numeric widening is not a subtype relation and is never applied inside function-type
// assignability: an `Integer` argument may widen at an ordinary conversion site, and that
// does not make this source's `Long` parameter match the target's `Integer` one. Written
// as a static initializer because that is the placement whose diagnostic the section names
// verbatim.
class Boundary {
    static var widened: func(Integer): Integer = widenResult
}

print("EXECUTED-INVALID")
