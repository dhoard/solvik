// Runtime-failure conformance test. Oracle derived by hand from LANGUAGE_SPEC section
// 22.5, verbatim: "When a thrown value reaches this boundary, no Solvik handler remains,
// so the value is uncaught. An uncaught thrown value is a guest-visible failure: it
// terminates the program with a non-zero exit status ... and it is reported as an
// ordinary guest error, never as a host internal error." Section 22.4 establishes the
// call-crossing used here: "A `throw` inside a called function is caught by a handler in
// any dynamically enclosing function frame" — and there is none, so the value reaches
// the boundary. The expected structured category is the protocol's normative
// classification of an uncaught guest exception (protocol.md section 4.1: UNCAUGHT_EXCEPTION),
// a spec+protocol derivation, not a value captured from the IUT. No output precedes the
// throw, so the failure happens with empty stdout; the class/message reporting text in
// section 22.5 is host-side and not asserted, because section 22.5 does not place it on
// guest stdout.
class ParseError extends RuntimeException {

}

func parse(text: String): Integer {
    throw ParseError("bad int")
}

var n: Integer = parse("x")
