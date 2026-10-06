// Solvik TCK SOL-TCK-0428
// The section's accepted Animal/Dog direction is invoked, and a `func(Long): Long` value is called with an `Integer` literal, which is ordinary call-site widening.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Function-type assignability is contravariant in parameters and covariant in the result.
//   - Given `mutable class Animal` and `class Dog extends Animal`, a value of type func(Animal): Dog is assignable to func(Dog): Animal, and a value of type func(Dog): Animal is not assignable to func(Animal): Dog.
//   - Numeric widening is not a subtype relation (section 4) and is never applied inside function-type assignability: a function accepting `Long` is not assignable to a function type accepting `Integer` merely because an `Integer` argument may widen at an ordinary conversion site.
//   - Arguments supplied when a function value is invoked still receive the ordinary call-site widening rules.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
mutable class Animal {
    var name: String = "animal"
}

class Dog extends Animal {
}

func toDog(animal: Animal): Dog {
    return Dog()
}

func nameOf(value: Animal): String {
    return value.name
}

func widenResult(value: Long): Long {
    return value + 1
}

// The section's own accepted direction: source `func(Animal): Dog` against target
// `func(Dog): Animal`. Parameters are contravariant, so the target's `Dog` must be
// assignable to the source's `Animal`, and the result is covariant, so the source's `Dog`
// result is assignable to the target's `Animal`.
var accepted: func(Dog): Animal = toDog

// Call-site widening is a separate rule from assignability and still applies through a
// function value: this passes an `Integer` literal to a `Long` parameter.
var takesLong: func(Long): Long = widenResult

print(nameOf(accepted(Dog())) .. "-" .. takesLong(1))
