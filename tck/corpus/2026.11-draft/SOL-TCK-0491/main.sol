// Solvik TCK SOL-TCK-0491
// The positive half: the two branch types are incomparable, so neither of them can be the join, and the joined value is callable and flows onward as a value only if the join produced the third type the sentence names. `func(Dog): Dog` and `func(Animal): Animal` disagree in both positions -- a `Dog` is an `Animal`, so neither function type is assignable to the other -- which rules out selecting a branch type, and it is the parameter position that is contravariant, so the joined parameter is the *more specific* member (`Dog`) while the covariant result position takes the nearest common result type (`Animal`). The joined value is then invoked with a `Dog` argument and its result reaches an `Animal` parameter, and the same value is accepted by a binding whose declared type is the joined type written out. An implementation that fell back to `Any` here would fail to compile: `Any` is not callable and does not flow into a function-typed binding.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every non-null function type has `Any` as its top supertype, and a nullable function type relates to another under those same rules.
//   - The shared type join understands function types: for two same-arity function types each joined parameter takes the more specific of the two when one is assignable to the other, and the joined result is their nearest common result type. That joined function type is the least common function supertype allowed by contravariant parameters and covariant results.
//   - When a parameter pair is unrelated or the results have no unique join, no function-type join exists and the ordinary join may still select a shared nominal supertype such as `Any`; a join never introduces `Nothing`, a union, or an intersection in order to manufacture a function supertype.
//   - Generic type arguments remain invariant, so `List<func(Dog): Animal>` and `List<func(Animal): Dog>` are unrelated applications even though the function types inside them are comparable.
//   - An invocation whose callee is not a function type is `SOLV-TYPE-002`, and a call with the wrong number of arguments is `SOLV-TYPE-003`.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
mutable class Animal {
    val name: String = "animal"
}

class Dog extends Animal {
}

func dogToDog(dog: Dog): Dog {
    return dog
}

func animalToAnimal(animal: Animal): Animal {
    return animal
}

func nameOf(value: Animal): String {
    return value.name
}

// The two branch types are incomparable, so the join can only be the type the rule computes:
// the more specific parameter and the nearest common result. A value of that joined type is a
// callable function value, its result reaches an `Animal` parameter, and it is accepted by a
// binding that writes the joined type out.
val flag: Boolean = true
val joined = if (flag) { dogToDog } else { animalToAnimal }
val joinedWritten: func(Dog): Animal = joined

print(nameOf(joined(Dog())) .. "-" .. nameOf(joinedWritten(Dog())))
