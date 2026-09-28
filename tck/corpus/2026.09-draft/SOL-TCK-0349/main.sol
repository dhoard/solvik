// Solvik TCK SOL-TCK-0349
// Two arguments for a zero-argument generic exception constructor remain an arity error, not a synthesized message.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - for a generic one, supplying more arguments than its declared constructor has remains an ordinary arity error rather than a message.
//   - supplying more than one extra trailing argument is an arity error (`SOLV-TYPE-003`).
//
class MyErr<T> extends RuntimeException {
    MyErr() {
    }
}
throw MyErr<Integer>(1, 2)
print("EXECUTED-INVALID")
