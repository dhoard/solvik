// Solvik TCK SOL-TCK-0451
// The capture list is not part of the function type, so a capturing closure occupies a local initializer, a call argument, a generic type argument, and a nullable function type, and is called at each of those types
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every such dependency must appear in an explicit capture list between `func` and the parameter list:
//   - The capture list is part of the anonymous-function expression but not part of its function type: the example above has type `func(Integer): Integer`, because callers supply `value` while the declaration visibly binds `factor` into the function value.
//   - An empty capture list is a parse error, because a non-capturing anonymous function is written `func(...)`.
//   - Capture aliases and arbitrary capture expressions are not supported.
//
func throughParameter(f: func(Integer): Integer): Integer {
    return f(1)
}

func first(list: List<func(Integer): Integer>): Integer {
    return list.get(0)(1)
}

func run(): String {
    var offset = 100
    var scale = func [offset](value: Integer): Integer {
        return value * offset
    }
    var maybe: func(Integer): Integer? = func [offset](value: Integer): Integer {
        return value + offset
    }
    var cells: List<func(Integer): Integer> = List<func(Integer): Integer>()
    var cellClosure = func [offset](value: Integer): Integer {
        return offset
    }
    cells.add(cellClosure)

    var direct = scale(2).toString()
    var argument = throughParameter(scale).toString()
    var nullable = (maybe(1) ?? 0).toString()
    var inList = first(cells).toString()
    return direct .. "|" .. argument .. "|" .. nullable .. "|" .. inList
}

print(run())
