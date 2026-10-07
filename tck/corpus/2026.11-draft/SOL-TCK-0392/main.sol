// Solvik TCK SOL-TCK-0392
// Sibling blocks each declare the same local name and both print their own value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A scope block introduces a new lexical scope for the statements it contains; sibling blocks are independent scopes, so the same local name may be declared in each without any shadowing between them.
//
{
    var result: Integer = 1
    print("s" .. result)
}
{
    var result: Integer = 2
    print("s" .. result)
}
