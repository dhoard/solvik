// Solvik TCK SOL-TCK-0489
// A static method reference used as a value is one of the deferred callables the required-diagnostics paragraph enumerates by name, and that paragraph states its code
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Only a declared callable binds. The fixed language-defined universal members `toString`, `equals`, and `hashCode`, and the synthesized `Result` operations, are not bindable: a bare read of one of them stays the compile-time error that section 3 and section 23.4 already require, and the same holds for a static method, a constructor, and an enum variant.
//   - A property may itself have a function type. Because a class member namespace cannot hold a property and a method with the same name, member resolution decides statically whether `receiver.member` reads a stored function value or creates a bound method value.
//   - `TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`) reports a callable that this revision keeps explicitly deferred used as a value: a static method reference, a constructor, an enum variant, and a bare read of a fixed language-defined member or a synthesized `Result` operation.
//
class Registry {
    static func make(): Integer {
        return 5
    }
}

func use() {
    val factory: Any = Registry.make
    print("bound")
}

use()
print("EXECUTED-INVALID")
