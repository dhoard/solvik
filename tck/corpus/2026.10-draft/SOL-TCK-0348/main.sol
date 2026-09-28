// Solvik TCK SOL-TCK-0348
// The handler names a parameterized generic type, which is not an accepted catch handler type.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A generic class cannot be thrown or caught at all today: constructing it yields a parameterized type, which neither a `throw` operand nor a `catch` handler type accepts.
//   - | `SEM_INVALID_CATCH_TYPE` | `SOLV-SEM-054` | the `catch` clause's type reference |
//
class Simple extends RuntimeException {
    Simple() {
    }
}
class MyErr<T> extends RuntimeException {
    val payload: T

    MyErr(payload: T) {
        this.payload = payload
    }
}
func f() {
    throw Simple()
}
try {
    f()
} catch (e: MyErr<Integer>) {
    print("caught")
}
print("EXECUTED-INVALID")
