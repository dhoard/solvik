// Solvik TCK SOL-TCK-0113
// The specification's own section 14 example, driven with all three of its inputs in a single program and printed as one bracketed token per arm, so the expected string names which arm ran for each input rather than merely that something printed. Three tests run the same example with the inputs in a different order; together they place every regex arm and the `default` arm in more than one dispatch position, so an implementation that got one arm wrong cannot be masked by another. Order cannot rescue a wrong arm here: each token is delimited, so an extra or omitted print changes the sequence. `default` is permitted to be absent in a statement `switch`, which section 13 states, so this program asserts nothing about that permission -- SOL-TCK-0115 covers the expression form where `default` is mandatory.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Regex patterns may be used in `switch` cases
//
val a = "42"
switch (a) {
  case regex r#"^\d+$"# {
    print("[number] ")
  }
  case regex r#"^[A-Za-z]+$"# {
    print("[word] ")
  }
  default {
    print("[other] ")
  }
}
val b = "hi"
switch (b) {
  case regex r#"^\d+$"# {
    print("[number] ")
  }
  case regex r#"^[A-Za-z]+$"# {
    print("[word] ")
  }
  default {
    print("[other] ")
  }
}
val c = "!!"
switch (c) {
  case regex r#"^\d+$"# {
    print("[number] ")
  }
  case regex r#"^[A-Za-z]+$"# {
    print("[word] ")
  }
  default {
    print("[other] ")
  }
}
