// Solvik TCK SOL-TCK-0471
// `sound` is declared only on the superclass, so the inherited instance method route is the one under test and both receivers resolve through it
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type invokes the implementation selected by the captured receiver's runtime class. Overrides, interface defaults, delegated implementations, and inherited instance methods behave the same through a bound reference as through an immediate method call.
//   - formatter.format === formatter.format // false: two bound-value creations
//
open class Animal {
    open func sound(): String {
        return "generic"
    }
}

class Cat extends Animal {
    override func sound(): String {
        return "meow"
    }
}

val animal: Animal = Animal()
val cat: Animal = Cat()
val animalMethod: func(): String = animal.sound
val catMethod: func(): String = cat.sound
print(animalMethod())
print("|")
print(catMethod())
