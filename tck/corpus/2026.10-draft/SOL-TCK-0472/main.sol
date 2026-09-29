// Solvik TCK SOL-TCK-0472
// An interface-typed receiver reaches two conforming instances' own requirements and the defaulted requirement, whose body calls the requirement back
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type invokes the implementation selected by the captured receiver's runtime class. Overrides, interface defaults, delegated implementations, and inherited instance methods behave the same through a bound reference as through an immediate method call.
//   - formatter.format === formatter.format // false: two bound-value creations
//
interface Speaker {
    func speak(): String
    func shout(): String {
        return speak() .. speak()
    }
}

class Dog implements Speaker {
    func speak(): String {
        return "woof"
    }
}

class Loud implements Speaker {
    func speak(): String {
        return "BARK"
    }
}

val dog: Speaker = Dog()
val loud: Speaker = Loud()
val dogSpeak: func(): String = dog.speak
val loudSpeak: func(): String = loud.speak
val dogShout: func(): String = dog.shout
print(dogSpeak())
print("|")
print(loudSpeak())
print("|")
print(dogShout())
