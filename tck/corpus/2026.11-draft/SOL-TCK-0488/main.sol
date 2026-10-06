// Solvik TCK SOL-TCK-0488
// The same rejection holds through `super`, the path that resolves through a superclass's dispatch table and so could otherwise bind an inherited override
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Only a declared callable binds. The fixed language-defined universal members `toString`, `equals`, and `hashCode`, and the synthesized `Result` operations, are not bindable: a bare read of one of them stays the compile-time error that section 3 and section 23.4 already require, and the same holds for a static method, a constructor, and an enum variant.
//   - A property may itself have a function type. Because a class member namespace cannot hold a property and a method with the same name, member resolution decides statically whether `receiver.member` reads a stored function value or creates a bound method value.
//   - `TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`) reports a callable that this revision keeps explicitly deferred used as a value: a static method reference, a constructor, an enum variant, and a bare read of a fixed language-defined member or a synthesized `Result` operation.
//
mutable class Base {
    override mutable func equals(other: Any?): Boolean {
        return true
    }
    override mutable func hashCode(): Integer {
        return 1
    }
}

class Derived extends Base {
    func use(): Any {
        var method: Any = super.equals
        return method
    }
}

print("EXECUTED-INVALID")
