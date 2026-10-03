// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.6,
// verbatim: "because a block is an expression a branch may use a block expression for
// multiple statements", with the spec's own branch shape
//   Ok(value) => {
//       println("ok")
//       value
//   }
// and the rule that "The block follows the same tail-result, semicolon, scope,
// abrupt-completion, and typing rules as any other block expression."
// `println` is replaced by `print` so no platform line separator enters the expected bytes.
// Both arms are exercised: the Ok branch emits the marker "ok" and then its bound value 3,
// the Err branch's tail expression is 0. Expected stdout is exactly "ok30".
enum Shape {
    Ok(Integer)
    Err(String)
}
func extract(s: Shape): Integer {
    return match s {
        Ok(value) => {
            print("ok")
            value
        }
        Err(reason) => {
            0
        }
    }
}
print(extract(Shape.Ok(3)))
print(extract(Shape.Err("bad")))
