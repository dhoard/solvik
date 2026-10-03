// Solvik TCK SOL-TCK-0366
// A static and an instance member share the name x, which the shared namespace forbids.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static member shares the class's member namespace: a static property and a static method may not reuse the name of an instance property, an instance method, or another static member of the same class, which is `SOLV-RESOL-002`.
//   - A static member is still subject to the rule that a member may not be named after its class.
//
class C {
    static mutable val x: Integer = 1
    val x: Integer

    C() {
        this.x = 2
    }
}
val c = C()
print("EXECUTED-INVALID")
