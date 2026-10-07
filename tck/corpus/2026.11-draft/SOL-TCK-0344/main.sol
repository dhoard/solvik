// Solvik TCK SOL-TCK-0344
// Rock is unrelated to Animal, so the return type is not covariant.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An implementing method must use the same parameter types and a covariant return type.
//
class mutable Animal {
    Animal() {
    }
}
class Rock {
    Rock() {
    }
}
interface Maker {
    method make(): Animal
}
class RockMaker implements Maker {
    RockMaker() {
    }

    method make(): Rock {
        return Rock()
    }
}
var m: Maker = RockMaker()
print("EXECUTED-INVALID")
