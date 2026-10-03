mutable class ParseError extends RuntimeException {
}
func pick(n: Integer): Integer {
    if (n > 0) {
        return n
    }
    throw ParseError("negative")
}
print("[" .. pick(5) .. "]")
