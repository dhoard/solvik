// Oracle derived from LANGUAGE_SPEC section 11, which gives the nominal-generics example:
//   "class Box<T> {
//        var mutable value: T
//      }"
// together with "Solvik supports nominal generics." and "Calling the class name invokes
// its constructor." (section 7).
//
// Expected bytes derived by hand from the program text:
//   * `Box<String>("hi")` binds the type parameter `T` to `String` for this use of the
//     nominal class, so the constructor stores "hi" into the property `value` and
//     `print(b.value)` emits the two characters `hi`;
//   * `Box<Integer>(7)` binds `T` to `Integer`; `c.value = c.value + 1` reads and writes
//     the same `var mutable` property, and section 3 gives `1 + 1` as the integral value 2, so the
//     stored value becomes 8 and `print(c.value)` emits `8`;
//   * the `print(" ")` between them emits one space.
// Total expected stdout: `hi 8`. The two different type arguments on the same declared
// class are the point of the test: a runtime that shared one storage cell across all
// instantiations could not produce both `hi` and `8`.
// Deliberate scope note: the section-11 example is written without a constructor, which
// section 2 rejects for a `var mutable` property with no initializer, so this program supplies the
// constructor form that section 7 requires. That addition is required to make the
// example executable at all and changes no generics semantics.
// Executed as top-level statements (section 20). Uses print, so no platform line separator
// can enter the expected bytes.
class Box<T> {
    var mutable value: T

    Box(initial: T) {
        this.value = initial
    }
}

var b: Box<String> = Box<String>("hi")
print(b.value)
print(" ")
var mutable c: Box<Integer> = Box<Integer>(7)
c.value = c.value + 1
print(c.value)
