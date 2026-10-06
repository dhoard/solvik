// Solvik TCK SOL-TCK-0492
// The joined parameter is the more specific member, so the joined value does not accept the wider one: `func(Dog): Animal` is not assignable to `func(Animal): Animal`, because contravariant parameters require the target's `Animal` to be assignable to the joined `Dog`. This is the direction a join that took the less specific parameter would allow, and it is a static declaration initializer, the placement whose non-assignable diagnostic the section names verbatim, so `SOLV-TYPE-001` is pinned; the sentinel proves non-execution.
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
    var name: String = "animal"
}

class Dog extends Animal {
}

func dogToDog(dog: Dog): Dog {
    return dog
}

func animalToAnimal(animal: Animal): Animal {
    return animal
}

class WiderParameter {
    static var wide: func(Animal): Animal = if (true) {
        dogToDog
    }
    else {
        animalToAnimal
    }
}

print("EXECUTED-INVALID")
