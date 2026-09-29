// Solvik TCK SOL-TCK-0429
// The rejected direction of the section's Animal/Dog pair, written as a static declaration initializer so the pinned code applies to the placement the section names.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Function-type assignability is contravariant in parameters and covariant in the result.
//   - Given `open class Animal` and `class Dog extends Animal`, a value of type func(Animal): Dog is assignable to func(Dog): Animal, and a value of type func(Dog): Animal is not assignable to func(Animal): Dog.
//   - Numeric widening is not a subtype relation (section 4) and is never applied inside function-type assignability: a function accepting `Long` is not assignable to a function type accepting `Integer` merely because an `Integer` argument may widen at an ordinary conversion site.
//   - Arguments supplied when a function value is invoked still receive the ordinary call-site widening rules.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
open class Animal {
    val name: String = "animal"
}

class Dog extends Animal {
}

func toAnimal(dog: Dog): Animal {
    return dog
}

// The section's rejected pair: source `func(Dog): Animal` against target
// `func(Animal): Dog`. Contravariant parameters require the target's `Animal` to be
// assignable to the source's `Dog` and covariant results require `Animal` to be
// assignable to `Dog`; neither holds. Written as a static initializer because that is
// the placement whose diagnostic the section names verbatim.
class Boundary {
    static val reversed: func(Animal): Dog = toAnimal
}

print("EXECUTED-INVALID")
