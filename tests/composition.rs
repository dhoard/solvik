//! End-to-end composition and delegation semantics through the verified VM.
//!
//! These exercise the full pipeline (lex -> parse -> resolve -> check -> IR ->
//! bytecode -> decode -> verify -> VM) for the composition-first object model.

use solvik_rs::diagnostic::Diagnostics;
use solvik_rs::ir::{IrInstr, IrModule};
use solvik_rs::source::SourceManager;
use solvik_rs::vm::Vm;

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("composition.sol", src).expect("program must compile");
    Vm::run_main(module, vec![]).expect("program must run")
}

/// Build the IR module for a program without lowering to bytecode, so a test
/// can inspect delegation lowering directly.
fn check_ir(src: &str) -> IrModule {
    let mut sources = SourceManager::default();
    sources.add("ir.sol", src.to_string());
    let mut diags = Diagnostics::default();
    let tokens = solvik_rs::lexer::Lexer::new(0, src).tokenize(&mut diags);
    let mut parser = solvik_rs::parser::Parser::new(tokens);
    let program = parser.parse_program().expect("parse");
    diags.items.append(&mut parser.diags.items);
    let resolved = solvik_rs::resolve::resolve_program(&program, &mut diags);
    assert!(!diags.has_errors(), "resolve: {:?}", diags.items);
    let mut checker = solvik_rs::check::Checker::new(&resolved, &sources);
    checker.check_program();
    assert!(
        !checker.diags.has_errors(),
        "check: {:?}",
        checker.diags.items
    );
    checker.ir
}

#[test]
fn delegation_lowers_to_field_load_and_interface_call() {
    let ir = check_ir(
        "module m\n\
         interface Named { name(): String }\n\
         class Person implements Named {\n\
             v: String\n\
             public static new(x: String): Self { return Self { v: x, } }\n\
             public name(): String { return self.v }\n\
         }\n\
         class Employee implements Named {\n\
             person: Person\n\
             delegate Named to person\n\
             public static new(x: String): Self { return Self { person: Person.new(x), } }\n\
         }\n\
         class Main { public static run(args: String...): Long { return 0 } }",
    );
    let f = ir
        .functions
        .iter()
        .find(|f| f.name == "Employee.name")
        .expect("delegation wrapper function");
    // Lowering: load self, load the private delegate field, then a normal
    // interface call, then return. No delegation-specific opcode exists.
    assert!(matches!(f.instrs[0], IrInstr::LoadLocal(0)));
    assert!(matches!(f.instrs[1], IrInstr::LoadField(_)));
    assert!(f
        .instrs
        .iter()
        .any(|i| matches!(i, IrInstr::CallInterface(_, _, 0))));
    assert!(f.instrs.iter().any(|i| matches!(i, IrInstr::Return)));
}

#[test]
fn delegation_through_concrete_and_interface_receiver() {
    let code = run("module m\n\
         interface Named { name(): String }\n\
         class Person implements Named {\n\
             nameValue: String\n\
             public static new(n: String): Self { return Self { nameValue: n, } }\n\
             public name(): String { return self.nameValue }\n\
         }\n\
         class Employee implements Named {\n\
             person: Person\n\
             delegate Named to person\n\
             public static new(n: String): Self { return Self { person: Person.new(n), } }\n\
         }\n\
         class Main { public static run(args: String...): Long {\n\
             let e: Employee = Employee.new(\"Alice\")\n\
             if e.name() != \"Alice\" { return 1 }\n\
             let n: Named = e\n\
             if n.name() != \"Alice\" { return 2 }\n\
             return 0\n\
         } }");
    assert_eq!(code, 0);
}

#[test]
fn explicit_class_method_beats_delegation() {
    let code = run("module m\n\
         interface Named { name(): String\n displayName(): String }\n\
         class Person implements Named {\n\
             v: String\n\
             public static new(x: String): Self { return Self { v: x, } }\n\
             public name(): String { return self.v }\n\
             public displayName(): String { return self.v }\n\
         }\n\
         class Employee implements Named {\n\
             person: Person\n\
             delegate Named to person\n\
             public static new(x: String): Self { return Self { person: Person.new(x), } }\n\
             public displayName(): String { return \"E:\" .. self.person.displayName() }\n\
         }\n\
         class Main { public static run(args: String...): Long {\n\
             let e: Employee = Employee.new(\"A\")\n\
             if e.name() != \"A\" { return 1 }\n\
             if e.displayName() != \"E:A\" { return 2 }\n\
             return 0\n\
         } }");
    assert_eq!(code, 0);
}

#[test]
fn interface_default_dispatches_to_receiver_over_delegation() {
    let code = run("module m\n\
         interface Greeting {\n\
             greeting(): String\n\
             farewell(): String { return \"bye \" .. greeting() }\n\
         }\n\
         class Bot implements Greeting {\n\
             public static new(): Self { return Self {} }\n\
             public greeting(): String { return \"bot\" }\n\
         }\n\
         class Wrapper implements Greeting {\n\
             bot: Bot\n\
             delegate Greeting to bot\n\
             public static new(): Self { return Self { bot: Bot.new(), } }\n\
         }\n\
         class Main { public static run(args: String...): Long {\n\
             let w: Wrapper = Wrapper.new()\n\
             if w.farewell() != \"bye bot\" { return 1 }\n\
             return 0\n\
         } }");
    assert_eq!(code, 0);
}

#[test]
fn object_dynamic_dispatch_reaches_delegated_method() {
    let code = run("module m\n\
         interface Named { name(): String }\n\
         class Person implements Named {\n\
             v: String\n\
             public static new(x: String): Self { return Self { v: x, } }\n\
             public name(): String { return self.v }\n\
         }\n\
         class Employee implements Named {\n\
             person: Person\n\
             delegate Named to person\n\
             public static new(x: String): Self { return Self { person: Person.new(x), } }\n\
         }\n\
         class Main { public static run(args: String...): Long {\n\
             let o: Object = Employee.new(\"Z\")\n\
             if o.name().toString() != \"Z\" { return 1 }\n\
             return 0\n\
         } }");
    assert_eq!(code, 0);
}

#[test]
fn private_method_is_not_dynamically_exposed() {
    let src = "module m\n\
         class A {\n\
             secret(): Long { return 41 }\n\
             public static new(): Self { return Self {} }\n\
         }\n\
         class Main { public static run(args: String...): Long {\n\
             let o: Object = A.new()\n\
             let r: Object = o.secret()\n\
             return 0\n\
         } }";
    let module = solvik_rs::compile("composition.sol", src).expect("compiles");
    // The public dynamic table must not contain the private method.
    let a = module.classes.iter().find(|c| c.name == "A").unwrap();
    assert!(!a.dyn_methods.iter().any(|(n, _)| n == "secret"));
    // And a dynamic call must fail at runtime, not crash.
    assert!(Vm::run_main(module, vec![]).is_err());
}
