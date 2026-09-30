// Declaration-only module included by FirstClassFunctions.sol. Its named functions are reached as
// values through the module alias, which is how a module exports a callable rather than only a
// declaration to call (docs/LANGUAGE_SPEC.md section 6, "Named functions as values").

module scaling

func doubled(value: Integer): Integer {
    return value * 2
}

func tripled(value: Integer): Integer {
    return value * 3
}
