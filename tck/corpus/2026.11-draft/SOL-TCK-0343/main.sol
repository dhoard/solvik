// Solvik TCK SOL-TCK-0343
// Returning the subtype Dog for an interface method declared to return Animal is a covariant return and is accepted.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An implementing method must use the same parameter types and a covariant return type.
//
mutable class Animal {
    Animal() {
    }
}
class Dog extends Animal {
    Dog() {
    }
}
interface Maker {
    func make(): Animal
}
class DogMaker implements Maker {
    DogMaker() {
    }

    func make(): Dog {
        return Dog()
    }
}
val m: Maker = DogMaker()
print("covok")
