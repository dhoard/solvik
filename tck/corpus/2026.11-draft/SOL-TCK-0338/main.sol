// Solvik TCK SOL-TCK-0338
// The subclass name must not expose the superclass's static member.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static member is **not inherited** and is **not overridable**. It is reached only through the name of the class that declares it, so a superclass and a subclass may each declare a static member of the same name as two independent members, and a subclass does not expose its superclass's static members.
//
mutable class A {
    static mutable val n: Integer = 5
}
class B extends A {
    B() {
    }
}
print(B.n)

print("EXECUTED-INVALID")
