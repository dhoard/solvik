// Solvik TCK SOL-TCK-0347
// A parameterized generic exception is thrown, which the throw operand does not accept.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A generic class cannot be thrown or caught at all today: constructing it yields a parameterized type, which neither a `throw` operand nor a `catch` handler type accepts.
//   - | `SEM_THROW_NON_EXCEPTION` | `SOLV-SEM-053` | the `throw` operand expression |
//
class MyErr<T> extends RuntimeException {
    val payload: T

    MyErr(payload: T) {
        this.payload = payload
    }
}
throw MyErr<Integer>(1)
print("EXECUTED-INVALID")
