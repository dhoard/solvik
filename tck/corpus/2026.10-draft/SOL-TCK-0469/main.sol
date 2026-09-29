// Solvik TCK SOL-TCK-0469
// The same value shape crosses a result and a parameter boundary and is called three times, so the witness is not an initializer position alone
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Reading an instance method without calling it produces a bound method value:
//   - The method's implicit receiver does not appear in the function type.
//   - It never reports a top-level function reference or a bound reference to a declared instance method, because this revision accepts both. Section 3 and section 23.4 retain their existing bare-member-read rejections unchanged.
//
class Adder {
    val offset: Integer
    Adder(offset: Integer) {
        this.offset = offset
    }
    func add(value: Integer): Integer {
        return value + this.offset
    }
}

func operationOf(adder: Adder): func(Integer): Integer {
    return adder.add
}

func thrice(operation: func(Integer): Integer, value: Integer): Integer {
    return operation(operation(operation(value)))
}

print(thrice(operationOf(Adder(3)), 10))
