// Solvik TCK SOL-TCK-0115
// The exact code is pinned because the required-diagnostics registry names it (SEM_SWITCH_EXPRESSION_MISSING_DEFAULT). Two sentences are needed to make this program meaningful and both are relied on: section 14 says a wildcard/default is required "where exhaustiveness is required" without saying where, and section 13 supplies the missing half by requiring `default` for expression switches while explicitly permitting its absence in statement switches. SOL-TCK-0113 is that permitted statement form, so the pair separates the two rules instead of simply accepting `default` everywhere. The scrutinee is a `String` dispatched by a regex case, the form section 14 names.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every expression `switch` must contain exactly one `default`, and it must remain last. `switch` does not gain enum exhaustiveness; that remains the responsibility of `match`. Requiring `default` makes value production explicit for `Integer`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do nothing when no label matches.
//
var input = "42"
var v = switch (input) {
  case regex r#"^\d+$"# {
    "number"
  }
}
print(v)
