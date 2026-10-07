// Oracle derived by hand from LANGUAGE_SPEC section 9, verbatim:
//   "Ambiguous delegation must be a compile-time error."
// Obligation: two delegates that both supply the same interface member, with no explicit
// resolving method on the class, must be rejected at compile time. The specification
// states the rule but names no stable code for it, so only the semantic family is
// asserted (the codes the current implementation emits occur nowhere in the
// specification).
interface SpeakerA {
    method say(): String
}

interface SpeakerB {
    method say(): String
}

class A implements SpeakerA {
    method say(): String {
        return "a"
    }
}

class B implements SpeakerB {
    method say(): String {
        return "b"
    }
}

class Hub implements SpeakerA {
    delegate x: SpeakerA
    delegate y: SpeakerB

    Hub(x: SpeakerA, y: SpeakerB) {
        this.x = x
        this.y = y
    }
}

print(Hub(A(), B()).say())
