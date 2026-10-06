// Solvik TCK SOL-TCK-0487
// A bare read of `toString`, `equals`, or `hashCode` is not bindable even where the class overrides one, so a dispatch-table read that found an override to bind would be wrong
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Only a declared callable binds. The fixed language-defined universal members `toString`, `equals`, and `hashCode`, and the synthesized `Result` operations, are not bindable: a bare read of one of them stays the compile-time error that section 3 and section 23.4 already require, and the same holds for a static method, a constructor, and an enum variant.
//   - A property may itself have a function type. Because a class member namespace cannot hold a property and a method with the same name, member resolution decides statically whether `receiver.member` reads a stored function value or creates a bound method value.
//   - `TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`) reports a callable that this revision keeps explicitly deferred used as a value: a static method reference, a constructor, an enum variant, and a bare read of a fixed language-defined member or a synthesized `Result` operation.
//
class Point {
    override func toString(): String {
        return "p"
    }
    var x: Integer
    Point(x: Integer) {
        this.x = x
    }
}

func use(point: Point) {
    var render: Any = point.toString
    print("bound")
}

use(Point(1))
print("EXECUTED-INVALID")
