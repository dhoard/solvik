//! Native tree-walking runtime for the semantic AST.

use crate::semantic_ast::{Block, Decl, Expr, Function, Stmt};
use crate::semantic_types::TypeRef;
use std::cell::RefCell;
use std::collections::HashMap;
use std::fmt;
use std::rc::Rc;
use base64::Engine;
use md5::Digest as Md5Digest;
use sha1::Sha1;
use sha2::{Sha256, Sha512};
use chrono::{DateTime, SecondsFormat, Utc};
use serde_json::Value as JsonValue;

#[derive(Clone)]
enum Value {
    Null,
    Bool(bool),
    Byte(u8),
    Int(i64),
    Float(f64),
    Char(char),
    String(String),
    Regex(String),
    List(Rc<RefCell<Vec<Value>>>),
    Stack(Rc<RefCell<Vec<Value>>>),
    Map(Rc<RefCell<Vec<(Value, Value)>>>),
    Struct(Rc<RefCell<StructValue>>),
    StructType(Rc<StructDef>),
    Enum(Rc<EnumValue>),
    EnumType(Rc<EnumDef>),
    Namespace(Rc<NamespaceValue>),
    Module(Rc<ModuleValue>),
    Exception(RuntimeError),
    Function(Rc<Callable>),
    Builtin(Rc<dyn Fn(Vec<Value>) -> Result<Value, RuntimeError>>),
}

struct Callable { function: Function, closure: EnvRef, receiver: Option<Value>, bytecode: Option<Rc<BcCode>> }
#[derive(Clone)]
struct StructDef {
    name: String,
    owner_package: String,
    methods: HashMap<String, Function>,
    method_public: HashMap<String, bool>,
    field_public: HashMap<String, bool>,
    type_param_count: usize,
}
struct StructValue { definition: Rc<StructDef>, fields: HashMap<String, Value>, unresolved: bool }
#[derive(Clone)]
struct EnumDef { name: String, members: HashMap<String, EnumMemberDef>, type_param_count: usize }
#[derive(Clone)]
struct EnumMemberDef { payload_count: usize, value: Option<i64> }
#[derive(Clone)]
struct EnumValue { definition: Rc<EnumDef>, member: String, payload: Vec<Value>, unresolved: bool }
struct NamespaceValue { env: EnvRef }
struct ModuleValue { env: EnvRef, callable: Option<Rc<dyn Fn(Vec<Value>) -> Result<Value, RuntimeError>>> }

#[derive(Clone, Debug)]
pub struct RuntimeError { pub code: String, pub message: String }

impl RuntimeError {
    fn new(message: impl Into<String>) -> Self { Self { code: String::new(), message: message.into() } }
    fn coded(code: &str, message: impl Into<String>) -> Self { Self { code: code.into(), message: message.into() } }
}

impl fmt::Display for Value {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Value::Null => write!(f, "null"), Value::Bool(v) => write!(f, "{}", v), Value::Byte(v) => write!(f, "{}", v), Value::Int(v) => write!(f, "{}", v),
            Value::Float(v) => write!(f, "{}", v), Value::Char(v) => write!(f, "{}", v), Value::String(v) => write!(f, "{}", v),
            Value::Regex(v) => write!(f, "regex({})", v),
            Value::List(v) => { write!(f, "[")?; for (i, item) in v.borrow().iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}", item)?; } write!(f, "]") },
            Value::Map(v) => { write!(f, "{{")?; for (i, (key, value)) in v.borrow().iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}: {}", key, value)?; } write!(f, "}}") },
            Value::Stack(v) => write!(f, "stack({})", v.borrow().len()),
            Value::Struct(value) => write!(f, "{} {{...}}", value.borrow().definition.name),
            Value::StructType(value) => write!(f, "<{}>", value.name),
            Value::Enum(value) => { write!(f, "{}", value.member)?; if !value.payload.is_empty() { write!(f, "(")?; for (i, item) in value.payload.iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}", item)?; } write!(f, ")")?; } Ok(()) },
            Value::EnumType(value) => write!(f, "<{}>", value.name),
            Value::Namespace(_) => write!(f, "<namespace>"),
            Value::Module(_) => write!(f, "<module>"),
            Value::Exception(value) => write!(f, "{}", value.message),
            Value::Function(_) | Value::Builtin(_) => write!(f, "<function>"),
        }
    }
}

impl PartialEq for Value {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Value::Null, Value::Null) => true, (Value::Bool(a), Value::Bool(b)) => a == b,
            (Value::Byte(a), Value::Byte(b)) => a == b, (Value::Byte(a), Value::Int(b)) | (Value::Int(b), Value::Byte(a)) => (*a as i64) == *b,
            (Value::Int(a), Value::Int(b)) => a == b, (Value::Float(a), Value::Float(b)) => a == b,
            (Value::Int(a), Value::Float(b)) | (Value::Float(b), Value::Int(a)) => (*a as f64) == *b,
            (Value::Char(a), Value::Char(b)) => a == b, (Value::String(a), Value::String(b)) => a == b,
            (Value::Regex(a), Value::Regex(b)) => a == b,
            (Value::List(a), Value::List(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Map(a), Value::Map(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Stack(a), Value::Stack(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Struct(a), Value::Struct(b)) => {
                let a = a.borrow(); let b = b.borrow();
                a.definition.name == b.definition.name && a.fields == b.fields
            }
            (Value::Enum(a), Value::Enum(b)) => a.definition.name == b.definition.name && a.member == b.member && a.payload == b.payload,
            (Value::Exception(a), Value::Exception(b)) => a.code == b.code && a.message == b.message,
            (Value::Function(a), Value::Function(b)) => Rc::ptr_eq(a, b),
            _ => false,
        }
    }
}

type EnvRef = Rc<RefCell<Env>>;
struct Env { parent: Option<EnvRef>, values: HashMap<String, Value>, package: String }

impl Env {
    fn new(parent: Option<EnvRef>) -> EnvRef {
        let package = parent.as_ref().map(|parent| parent.borrow().package.clone()).unwrap_or_default();
        Rc::new(RefCell::new(Self { parent, values: HashMap::new(), package }))
    }
    fn package(parent: Option<EnvRef>, package: impl Into<String>) -> EnvRef {
        Rc::new(RefCell::new(Self { parent, values: HashMap::new(), package: package.into() }))
    }
    fn define(env: &EnvRef, name: String, value: Value) { env.borrow_mut().values.insert(name, value); }
    fn get(env: &EnvRef, name: &str) -> Option<Value> {
        if let Some(value) = env.borrow().values.get(name) { return Some(value.clone()); }
        env.borrow().parent.as_ref().and_then(|parent| Self::get(parent, name))
    }
    fn set(env: &EnvRef, name: &str, value: Value) -> bool {
        if env.borrow().values.contains_key(name) { env.borrow_mut().values.insert(name.into(), value); true }
        else if let Some(parent) = env.borrow().parent.clone() { Self::set(&parent, name, value) } else { false }
    }
}

enum Flow { Normal, Return(Value), Break, Continue, Throw(RuntimeError) }

pub fn run(program: &crate::semantic_ast::Program) -> Result<i32, RuntimeError> {
    let global = Env::package(None, program.package.clone());
    install_builtins(&global, &[]);
    install_declarations(&global, program);
    execute_main(&global)
}

fn install_declarations(global: &EnvRef, program: &crate::semantic_ast::Program) {
    for declaration in &program.declarations {
        if let Decl::Enum(enum_decl) = declaration {
            let mut next_value = 0;
            let members = enum_decl.members.iter().map(|member| {
                let value = if member.payload.is_empty() { let value = member.value.unwrap_or(next_value); next_value = value + 1; Some(value) } else { None };
                (member.name.clone(), EnumMemberDef { payload_count: member.payload.len(), value })
            }).collect();
            Env::define(&global, enum_decl.name.clone(), Value::EnumType(Rc::new(EnumDef { name: enum_decl.name.clone(), members, type_param_count: enum_decl.type_params.len() })));
        }
        if let Decl::Struct(struct_decl) = declaration {
            let methods = struct_decl.methods.iter().map(|method| (method.name.clone(), method.clone())).collect();
            let method_public = struct_decl.methods.iter().map(|method| (method.name.clone(), method.public)).collect();
            let field_public = struct_decl.fields.iter().map(|field| (field.name.clone(), field.public)).collect();
            let package = global.borrow().package.clone();
            Env::define(&global, struct_decl.name.clone(), Value::StructType(Rc::new(StructDef {
                name: struct_decl.name.clone(), owner_package: package, methods, method_public,
                field_public, type_param_count: struct_decl.type_params.len(),
            })));
        }
    }
    for declaration in &program.declarations {
        if let Decl::Function(function) = declaration {
            let callable = Value::Function(Rc::new(Callable { function: function.clone(), closure: global.clone(), receiver: None, bytecode: None }));
            Env::define(&global, function.name.clone(), callable);
        }
    }
}

fn execute_main(global: &EnvRef) -> Result<i32, RuntimeError> {
    let main = Env::get(&global, "main").ok_or_else(|| RuntimeError::new("entry function main not found"))?;
    match call(main, Vec::new())? { Value::Int(code) => Ok(code as i32), _ => Ok(0) }
}

fn validate_entry(program: &crate::semantic_ast::Program) -> Result<(), RuntimeError> {
    if let Some(Decl::Function(function)) = program.declarations.iter().find(|declaration| matches!(declaration, Decl::Function(function) if function.name == "main")) {
        if !function.params.is_empty() {
            return Err(RuntimeError::coded("C123", "entry function 'main' must take no parameters"));
        }
        if function.return_type.name != "int" && function.return_type.name != "void" {
            return Err(RuntimeError::coded("C124", format!("entry function 'main' must return int or nothing, not {}", function.return_type)));
        }
    }
    Ok(())
}

fn load_dependency_programs(path: &str, program: &crate::semantic_ast::Program) -> Result<Vec<crate::semantic_ast::Program>, RuntimeError> {
    let base = std::path::Path::new(path).parent().unwrap_or_else(|| std::path::Path::new("."));
    let builtins = ["core", "string", "math", "random", "path", "base64", "hash", "secrets", "file", "process", "env", "json", "time", "http", "test", "stack"];
    let mut dependencies = Vec::new();
    for dependency in &program.uses {
        if dependency.scheme != "file" { return Err(RuntimeError::new(format!("unsupported dependency scheme {}", dependency.scheme))); }
        let requested = std::path::Path::new(&dependency.value);
        let candidate = if requested.is_absolute() { requested.to_path_buf() } else { base.join(requested) };
        let dotted = if !dependency.value.contains('/') && !dependency.value.contains('\\') { Some(base.join(dependency.value.replace('.', "/") + ".sol")) } else { None };
        let dependency_path = if candidate.exists() { candidate } else if candidate.extension().is_none() { candidate.with_extension("sol") } else if dotted.as_ref().is_some_and(|path| path.exists()) { dotted.unwrap() } else { candidate };
        let dependency_source = std::fs::read_to_string(&dependency_path).map_err(|error| RuntimeError::new(format!("cannot read dependency {}: {}", dependency_path.display(), error)))?;
        let dependency_program = crate::semantic_parser::parse(&dependency_source).map_err(|error| parse_error(&dependency_path.display().to_string(), error))?;
        if builtins.contains(&dependency_program.package.as_str()) { return Err(RuntimeError::coded("C121", format!("package name '{}' conflicts with a built-in namespace", dependency_program.package))); }
        dependencies.push(dependency_program);
    }
    Ok(dependencies)
}

pub fn check_file(path: &str) -> Result<(), RuntimeError> {
    let source = std::fs::read_to_string(path).map_err(|error| RuntimeError::new(format!("cannot read source file: {}", error)))?;
    let program = crate::semantic_parser::parse(&source).map_err(|error| parse_error(path, error))?;
    validate_entry(&program)?;
    let dependencies = load_dependency_programs(path, &program)?;
    for dependency in &dependencies {
        crate::semantic_validator::validate(dependency).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    }
    crate::semantic_validator::validate_with_dependencies(&program, &dependencies).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    Ok(())
}

pub fn run_file(path: &str, program_args: &[String]) -> Result<i32, RuntimeError> {
    let source = std::fs::read_to_string(path).map_err(|error| RuntimeError::new(format!("cannot read source file: {}", error)))?;
    let program = crate::semantic_parser::parse(&source).map_err(|error| parse_error(path, error))?;
    validate_entry(&program)?;
    let dependencies = load_dependency_programs(path, &program)?;
    for dependency in &dependencies {
        crate::semantic_validator::validate(dependency).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    }
    crate::semantic_validator::validate_with_dependencies(&program, &dependencies).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    let global = Env::package(None, program.package.clone()); install_builtins(&global, program_args); install_declarations(&global, &program); install_bytecode_declarations(&global, &program);
    for dependency_program in &dependencies {
        let dependency_env = Env::package(Some(global.clone()), dependency_program.package.clone()); install_declarations(&dependency_env, &dependency_program); install_bytecode_declarations(&dependency_env, dependency_program);
        Env::define(&global, dependency_program.package.clone(), Value::Namespace(Rc::new(NamespaceValue { env: dependency_env })));
    }
    execute_main_bytecode(&global)
}

fn parse_error(path: &str, error: crate::semantic_parser::ParseError) -> RuntimeError {
    let code = if error.message.starts_with("P078:") { "P078" } else if error.message.starts_with("P075:") { "P075" } else if error.message.starts_with("L016:") || error.message.starts_with("unknown escape sequence") { "L016" } else if error.message.starts_with("L017:") || error.message.starts_with("invalid hexadecimal escape") { "L017" } else { "P000" };
    RuntimeError::coded(code, format!("{}:{}:{}: parse error: {}", path, error.position.line, error.position.column, error.message))
}

fn install_builtins(env: &EnvRef, program_args: &[String]) {
    let print = |newline: bool| Value::Builtin(Rc::new(move |args| {
        if args.len() != 1 { return Err(RuntimeError::new("print expects 1 argument")); }
        if newline { println!("{}", args[0]); } else { print!("{}", args[0]); }
        Ok(Value::Null)
    }));
    Env::define(env, "print".into(), print(false)); Env::define(env, "println".into(), print(true));
    install_string(env);
    Env::define(env, "int".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("int expects 1 argument")); } match &args[0] { Value::Enum(value) if value.payload.is_empty() => value.definition.members.get(&value.member).and_then(|member| member.value).map(Value::Int).ok_or_else(|| RuntimeError::coded("E066", "payload enum values have no integer conversion")), Value::Enum(_) => Err(RuntimeError::coded("E066", "payload enum values have no integer conversion")), Value::Byte(v) => Ok(Value::Int(*v as i64)), Value::Int(v) => Ok(Value::Int(*v)), Value::Float(v) => Ok(Value::Int(*v as i64)), Value::Bool(v) => Ok(Value::Int(*v as i64)), Value::Char(v) => Ok(Value::Int(*v as i64)), Value::String(v) => v.parse().map(Value::Int).map_err(|_| RuntimeError::coded("E073", format!("cannot convert '{}' to int", v))), _ => Err(RuntimeError::new("cannot convert value to int")) } })));
    Env::define(env, "byte".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("byte expects 1 argument")); } let value = match &args[0] { Value::Byte(v) => *v as i64, Value::Int(v) => *v, Value::Float(v) => *v as i64, _ => return Err(RuntimeError::coded("E073", "cannot convert value to byte")) }; u8::try_from(value).map(Value::Byte).map_err(|_| RuntimeError::coded("E073", "byte value out of range")) })));
    Env::define(env, "float".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("float expects 1 argument")); } match &args[0] { Value::Int(v) => Ok(Value::Float(*v as f64)), Value::Float(v) => Ok(Value::Float(*v)), Value::String(v) => v.parse().map(Value::Float).map_err(|_| RuntimeError::coded("E073", format!("cannot convert '{}' to float", v))), _ => Err(RuntimeError::new("cannot convert value to float")) } })));
    Env::define(env, "bool".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("bool expects 1 argument")); } Ok(Value::Bool(truthy(&args[0]))) })));
    Env::define(env, "typeOf".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("typeOf expects 1 argument")); } Ok(Value::String(type_name(&args[0]))) })));
    Env::define(env, "isType".into(), Value::Builtin(Rc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("isType expects 2 arguments")); } let Value::String(expected) = &args[1] else { return Err(RuntimeError::new("isType expects a string as second argument")); }; Ok(Value::Bool(type_name(&args[0]) == expected.to_lowercase())) })));
    Env::define(env, "regex".into(), Value::Builtin(Rc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("regex expects 1 argument")); } let Value::String(pattern) = &args[0] else { return Err(RuntimeError::new("regex expects a string")); }; Ok(Value::Regex(pattern.clone())) })));
    let process = Env::new(Some(env.clone()));
    let args = program_args.iter().cloned().map(Value::String).collect::<Vec<_>>();
    Env::define(&process, "args".into(), Value::Builtin(Rc::new(move |values| { if !values.is_empty() { return Err(RuntimeError::new("process.args expects no arguments")); } Ok(Value::List(Rc::new(RefCell::new(args.clone())))) })));
    Env::define(&process, "run".into(), Value::Builtin(Rc::new(|values| {
        let Some(Value::String(command)) = values.first() else { return Err(RuntimeError::new("process.run expects at least one string")); };
        let arguments = values[1..].iter().map(|value| match value { Value::String(value) => Ok(value.clone()), _ => Err(RuntimeError::new("process.run arguments must be strings")) }).collect::<Result<Vec<_>, _>>()?;
        let status = std::process::Command::new(command).args(arguments).status()
            .map_err(|error| RuntimeError::coded("E072", format!("process run failed: {}", error)))?;
        Ok(Value::Int(status.code().unwrap_or(-1) as i64))
    })));
    Env::define(&process, "capture".into(), Value::Builtin(Rc::new(|values| {
        let Some(Value::String(command)) = values.first() else { return Err(RuntimeError::new("process.capture expects at least one string")); };
        let arguments = values[1..].iter().map(|value| match value { Value::String(value) => Ok(value.clone()), _ => Err(RuntimeError::new("process.capture arguments must be strings")) }).collect::<Result<Vec<_>, _>>()?;
        let output = std::process::Command::new(command).args(arguments).output()
            .map_err(|error| RuntimeError::coded("E072", format!("process capture failed: {}", error)))?;
        Ok(Value::Map(Rc::new(RefCell::new(vec![
            (Value::String("status".into()), Value::Int(output.status.code().unwrap_or(-1) as i64)),
            (Value::String("stdout".into()), Value::String(String::from_utf8_lossy(&output.stdout).into_owned())),
            (Value::String("stderr".into()), Value::String(String::from_utf8_lossy(&output.stderr).into_owned())),
        ]))))
    })));
    Env::define(env, "process".into(), Value::Namespace(Rc::new(NamespaceValue { env: process })));
    let environment = Env::new(Some(env.clone()));
    Env::define(&environment, "get".into(), Value::Builtin(Rc::new(|values| {
        let [Value::String(name)] = values.as_slice() else { return Err(RuntimeError::new("env.get expects one string")); };
        Ok(std::env::var(name).map(Value::String).unwrap_or(Value::Null))
    })));
    Env::define(&environment, "set".into(), Value::Builtin(Rc::new(|values| {
        let [Value::String(name), Value::String(value)] = values.as_slice() else { return Err(RuntimeError::new("env.set expects two strings")); };
        std::env::set_var(name, value);
        Ok(Value::Null)
    })));
    Env::define(&environment, "keys".into(), Value::Builtin(Rc::new(|values| {
        if !values.is_empty() { return Err(RuntimeError::new("env.keys expects no arguments")); }
        Ok(Value::List(Rc::new(RefCell::new(std::env::vars().map(|(name, _)| Value::String(name)).collect()))))
    })));
    Env::define(env, "env".into(), Value::Namespace(Rc::new(NamespaceValue { env: environment })));
    install_random(env);
    install_path(env);
    install_math(env);
    install_base64(env);
    install_hash(env);
    install_secrets(env);
    install_file(env);
    install_json(env);
    install_time(env);
    install_test(env);
    install_http(env);
    Env::define(env, "stack".into(), Value::Builtin(Rc::new(|args| { if !args.is_empty() { return Err(RuntimeError::new("stack expects no arguments")); } Ok(Value::Stack(Rc::new(RefCell::new(Vec::new())))) })));
}

fn install_random(env: &EnvRef) {
    let module = Env::new(Some(env.clone()));
    let state = Rc::new(RefCell::new(0x9e37_79b9_u64));
    let next = |state: Rc<RefCell<u64>>| move || { let mut value = state.borrow_mut(); *value = value.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407); *value };
    let float_state = state.clone();
    Env::define(&module, "float".into(), Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("random.float expects no arguments")); } Ok(Value::Float(next(float_state.clone())() as f64 / (u64::MAX as f64 + 1.0))) })));
    let int_state = state.clone();
    Env::define(&module, "int".into(), Value::Builtin(Rc::new(move |args| { let [Value::Int(low), Value::Int(high)] = args.as_slice() else { return Err(RuntimeError::new("random.int expects two integers")); }; if low > high { return Err(RuntimeError::coded("E072", "random.int lower bound exceeds upper bound")); } let width = (*high as i128 - *low as i128 + 1) as u128; let value = (next(int_state.clone())() as u128 % width) as i128 + *low as i128; Ok(Value::Int(value as i64)) })));
    let range_state = state.clone();
    Env::define(&module, "range".into(), Value::Builtin(Rc::new(move |args| { let [Value::Int(low), Value::Int(high)] = args.as_slice() else { return Err(RuntimeError::new("random.range expects two integers")); }; if low >= high { return Err(RuntimeError::coded("E072", "random.range requires lower bound below upper bound")); } Ok(Value::Int((next(range_state.clone())() % (*high - *low) as u64) as i64 + low)) })));
    let uniform_state = state.clone();
    Env::define(&module, "uniform".into(), Value::Builtin(Rc::new(move |args| { let [Value::Float(low), Value::Float(high)] = args.as_slice() else { return Err(RuntimeError::new("random.uniform expects two floats")); }; Ok(Value::Float(low + (high - low) * (next(uniform_state.clone())() as f64 / (u64::MAX as f64 + 1.0)))) })));
    let seed_state = state.clone();
    Env::define(&module, "seed".into(), Value::Builtin(Rc::new(move |args| { let [Value::Int(seed)] = args.as_slice() else { return Err(RuntimeError::new("random.seed expects one integer")); }; *seed_state.borrow_mut() = *seed as u64; Ok(Value::Null) })));
    let choice_state = state.clone();
    Env::define(&module, "choice".into(), Value::Builtin(Rc::new(move |args| { let [Value::List(items)] = args.as_slice() else { return Err(RuntimeError::new("random.choice expects a list")); }; let items = items.borrow(); if items.is_empty() { return Ok(Value::Null); } Ok(items[(next(choice_state.clone())() % items.len() as u64) as usize].clone()) })));
    let shuffle_state = state.clone();
    Env::define(&module, "shuffle".into(), Value::Builtin(Rc::new(move |args| { let [Value::List(items)] = args.as_slice() else { return Err(RuntimeError::new("random.shuffle expects a list")); }; let mut result = items.borrow().clone(); for i in (1..result.len()).rev() { let j = (next(shuffle_state.clone())() % (i as u64 + 1)) as usize; result.swap(i, j); } Ok(Value::List(Rc::new(RefCell::new(result)))) })));
    let sample_state = state;
    Env::define(&module, "sample".into(), Value::Builtin(Rc::new(move |args| { let [Value::List(items), Value::Int(requested)] = args.as_slice() else { return Err(RuntimeError::new("random.sample expects a list and count")); }; let mut result = items.borrow().clone(); for i in (1..result.len()).rev() { let j = (next(sample_state.clone())() % (i as u64 + 1)) as usize; result.swap(i, j); } result.truncate((*requested).max(0) as usize); Ok(Value::List(Rc::new(RefCell::new(result)))) })));
    Env::define(env, "random".into(), Value::Namespace(Rc::new(NamespaceValue { env: module })));
}

fn module(env: &EnvRef) -> EnvRef { Env::new(Some(env.clone())) }
fn install_path(env: &EnvRef) {
    let path = module(env);
    Env::define(&path, "join".into(), Value::Builtin(Rc::new(|args| { if args.is_empty() { return Err(RuntimeError::new("path.join expects at least one argument")); } let mut result = std::path::PathBuf::new(); for value in args { let Value::String(value) = value else { return Err(RuntimeError::new("path.join expects strings")); }; result.push(value); } Ok(Value::String(result.to_string_lossy().replace('\\', "/"))) })));
    Env::define(&path, "basename".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.basename expects one string")); }; Ok(Value::String(std::path::Path::new(value).file_name().and_then(|part| part.to_str()).unwrap_or("").into())) })));
    Env::define(&path, "dirname".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.dirname expects one string")); }; Ok(Value::String(std::path::Path::new(value).parent().and_then(|part| part.to_str()).unwrap_or(".").replace('\\', "/"))) })));
    Env::define(&path, "ext".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.ext expects one string")); }; Ok(Value::String(std::path::Path::new(value).extension().and_then(|part| part.to_str()).map(|part| format!(".{}", part)).unwrap_or_default())) })));
    Env::define(&path, "abs".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.abs expects one string")); }; let path = std::path::Path::new(value); let path = if path.is_absolute() { path.to_path_buf() } else { std::env::current_dir().unwrap_or_default().join(path) }; Ok(Value::String(path.to_string_lossy().replace('\\', "/"))) })));
    Env::define(&path, "exists".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.exists expects one string")); }; Ok(Value::Bool(std::path::Path::new(value).exists())) })));
    Env::define(env, "path".into(), Value::Namespace(Rc::new(NamespaceValue { env: path })));
}

fn install_math(env: &EnvRef) {
    let math = module(env);
    Env::define(&math, "PI".into(), Value::Float(std::f64::consts::PI)); Env::define(&math, "E".into(), Value::Float(std::f64::consts::E));
    Env::define(&math, "abs".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.abs expects one argument")); }; match value { Value::Int(value) => Ok(Value::Int(value.abs())), Value::Float(value) => Ok(Value::Float(value.abs())), _ => Err(RuntimeError::new("math.abs expects a numeric argument")) } })));
    Env::define(&math, "min".into(), Value::Builtin(Rc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.min expects 2 arguments")); } if matches!((&args[0], &args[1]), (Value::Float(_), _) | (_, Value::Float(_))) { Ok(Value::Float(number(&args[0])?.min(number(&args[1])?))) } else { Ok(Value::Int(integer_value(&args[0])?.min(integer_value(&args[1])?))) } })));
    Env::define(&math, "max".into(), Value::Builtin(Rc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.max expects 2 arguments")); } if matches!((&args[0], &args[1]), (Value::Float(_), _) | (_, Value::Float(_))) { Ok(Value::Float(number(&args[0])?.max(number(&args[1])?))) } else { Ok(Value::Int(integer_value(&args[0])?.max(integer_value(&args[1])?))) } })));
    Env::define(&math, "floor".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.floor expects one argument")); }; Ok(Value::Int(number(value)?.floor() as i64)) })));
    Env::define(&math, "ceil".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.ceil expects one argument")); }; Ok(Value::Int(number(value)?.ceil() as i64)) })));
    Env::define(&math, "round".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.round expects one argument")); }; Ok(Value::Int(number(value)?.round() as i64)) })));
    Env::define(&math, "sqrt".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.sqrt expects one argument")); }; Ok(Value::Float(number(value)?.sqrt())) })));
    Env::define(&math, "pow".into(), Value::Builtin(Rc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.pow expects 2 arguments")); }; Ok(Value::Float(number(&args[0])?.powf(number(&args[1])?))) })));
    Env::define(&math, "sin".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.sin expects one argument")); }; Ok(Value::Float(number(value)?.sin())) })));
    Env::define(&math, "cos".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.cos expects one argument")); }; Ok(Value::Float(number(value)?.cos())) })));
    Env::define(&math, "tan".into(), Value::Builtin(Rc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.tan expects one argument")); }; Ok(Value::Float(number(value)?.tan())) })));
    Env::define(env, "math".into(), Value::Namespace(Rc::new(NamespaceValue { env: math })));
}

fn install_base64(env: &EnvRef) {
    let base64 = module(env);
    Env::define(&base64, "encode".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("base64.encode expects one string")); }; Ok(Value::String(base64::engine::general_purpose::STANDARD.encode(value.as_bytes()))) })));
    Env::define(&base64, "decode".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("base64.decode expects one string")); }; let bytes = base64::engine::general_purpose::STANDARD.decode(value).map_err(|_| RuntimeError::coded("E072", "base64 decode failed"))?; String::from_utf8(bytes).map(Value::String).map_err(|_| RuntimeError::coded("E072", "base64 decoded data is not UTF-8")) })));
    Env::define(env, "base64".into(), Value::Namespace(Rc::new(NamespaceValue { env: base64 })));
}

fn digest_hex<D: Md5Digest>(bytes: &[u8]) -> String { D::digest(bytes).iter().map(|byte| format!("{:02x}", byte)).collect() }
fn install_hash(env: &EnvRef) {
    let hash = module(env);
    Env::define(&hash, "md5".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.md5 expects one string")); }; Ok(Value::String(digest_hex::<md5::Md5>(value.as_bytes()))) })));
    Env::define(&hash, "sha1".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha1 expects one string")); }; Ok(Value::String(digest_hex::<Sha1>(value.as_bytes()))) })));
    Env::define(&hash, "sha256".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha256 expects one string")); }; Ok(Value::String(digest_hex::<Sha256>(value.as_bytes()))) })));
    Env::define(&hash, "sha512".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha512 expects one string")); }; Ok(Value::String(digest_hex::<Sha512>(value.as_bytes()))) })));
    Env::define(env, "hash".into(), Value::Namespace(Rc::new(NamespaceValue { env: hash })));
}

fn random_bytes(count: usize) -> Vec<u8> { let mut bytes = vec![0; count]; if getrandom::getrandom(&mut bytes).is_err() { let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos() as u64; for (index, byte) in bytes.iter_mut().enumerate() { *byte = stamp.rotate_left(index as u32) as u8; } } bytes }
fn install_secrets(env: &EnvRef) {
    let secrets = module(env);
    Env::define(&secrets, "token".into(), Value::Builtin(Rc::new(|args| { let [Value::Int(count)] = args.as_slice() else { return Err(RuntimeError::new("secrets.token expects one integer")); }; if *count <= 0 { return Err(RuntimeError::coded("E072", "secrets.token n must be > 0")); } Ok(Value::String(base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(random_bytes(*count as usize)))) })));
    Env::define(&secrets, "hex".into(), Value::Builtin(Rc::new(|args| { let [Value::Int(count)] = args.as_slice() else { return Err(RuntimeError::new("secrets.hex expects one integer")); }; if *count <= 0 { return Err(RuntimeError::coded("E072", "secrets.hex n must be > 0")); } Ok(Value::String(random_bytes(*count as usize).iter().map(|byte| format!("{:02x}", byte)).collect())) })));
    Env::define(env, "secrets".into(), Value::Namespace(Rc::new(NamespaceValue { env: secrets })));
}

fn install_json(env: &EnvRef) {
    let json = module(env);
    Env::define(&json, "parse".into(), Value::Builtin(Rc::new(|args| {
        let [Value::String(source)] = args.as_slice() else {
            return Err(RuntimeError::new("json.parse expects one string"));
        };
        let parsed: JsonValue = serde_json::from_str(source)
            .map_err(|error| RuntimeError::coded("E072", format!("json parse error: {}", error)))?;
        json_to_value(parsed)
    })));
    Env::define(&json, "stringify".into(), Value::Builtin(Rc::new(|args| {
        let [value] = args.as_slice() else {
            return Err(RuntimeError::new("json.stringify expects one argument"));
        };
        value_to_json(value)
            .map(|json| format_json(&json))
            .map(Value::String)
    })));
    Env::define(env, "json".into(), Value::Namespace(Rc::new(NamespaceValue { env: json })));
}

fn json_to_value(value: JsonValue) -> Result<Value, RuntimeError> {
    match value {
        JsonValue::Null => Ok(Value::Null),
        JsonValue::Bool(value) => Ok(Value::Bool(value)),
        JsonValue::Number(value) => value.as_i64().map(Value::Int)
            .or_else(|| value.as_f64().map(Value::Float))
            .ok_or_else(|| RuntimeError::coded("E072", "json number is outside Solvik numeric range")),
        JsonValue::String(value) => Ok(Value::String(value)),
        JsonValue::Array(values) => Ok(Value::List(Rc::new(RefCell::new(
            values.into_iter().map(json_to_value).collect::<Result<Vec<_>, _>>()?,
        )))),
        JsonValue::Object(values) => Ok(Value::Map(Rc::new(RefCell::new(
            values.into_iter().map(|(key, value)| Ok((Value::String(key), json_to_value(value)?)))
                .collect::<Result<Vec<_>, RuntimeError>>()?,
        )))),
    }
}

fn value_to_json(value: &Value) -> Result<JsonValue, RuntimeError> {
    match value {
        Value::Null => Ok(JsonValue::Null),
        Value::Bool(value) => Ok(JsonValue::Bool(*value)),
        Value::Byte(value) => Ok(JsonValue::Number((*value as u64).into())),
        Value::Int(value) => Ok(JsonValue::Number((*value).into())),
        Value::Float(value) => serde_json::Number::from_f64(*value)
            .map(JsonValue::Number)
            .ok_or_else(|| RuntimeError::coded("E072", "value of type float is not representable as JSON")),
        Value::Char(value) => Ok(JsonValue::String(value.to_string())),
        Value::String(value) => Ok(JsonValue::String(value.clone())),
        Value::List(values) | Value::Stack(values) => Ok(JsonValue::Array(
            values.borrow().iter().map(value_to_json).collect::<Result<Vec<_>, _>>()?,
        )),
        Value::Map(values) => {
            let mut object = serde_json::Map::new();
            for (key, value) in values.borrow().iter() {
                let key = match key {
                    Value::String(key) => key.clone(),
                    Value::Int(key) => key.to_string(),
                    Value::Byte(key) => key.to_string(),
                    Value::Float(key) => key.to_string(),
                    Value::Bool(key) => key.to_string(),
                    Value::Null => "null".into(),
                    _ => return Err(RuntimeError::coded("E072", format!("value of type {} is not a valid JSON object key", type_name(key)))),
                };
                object.insert(key, value_to_json(value)?);
            }
            Ok(JsonValue::Object(object))
        }
        Value::Struct(_) | Value::Enum(_) | Value::Exception(_) => Ok(JsonValue::String(value.to_string())),
        _ => Err(RuntimeError::coded("E072", format!("value of type {} is not representable as JSON", type_name(value)))),
    }
}

fn format_json(value: &JsonValue) -> String {
    match value {
        JsonValue::Array(values) => format!("[{}]", values.iter().map(format_json).collect::<Vec<_>>().join(", ")),
        JsonValue::Object(values) => format!("{{{}}}", values.iter().map(|(key, value)| {
            format!("{}: {}", serde_json::to_string(key).unwrap_or_else(|_| "\"\"".into()), format_json(value))
        }).collect::<Vec<_>>().join(", ")),
        _ => serde_json::to_string(value).unwrap_or_else(|_| "null".into()),
    }
}

fn install_time(env: &EnvRef) {
    let time = module(env);
    Env::define(&time, "now".into(), Value::Builtin(Rc::new(|args| {
        if !args.is_empty() { return Err(RuntimeError::new("time.now expects no arguments")); }
        let millis = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)
            .map_err(|error| RuntimeError::coded("E072", error.to_string()))?
            .as_millis();
        Ok(Value::Int(millis.min(i64::MAX as u128) as i64))
    })));
    Env::define(&time, "sleep".into(), Value::Builtin(Rc::new(|args| {
        let [milliseconds] = args.as_slice() else { return Err(RuntimeError::new("time.sleep expects one integer")); };
        let milliseconds = integer_value(milliseconds)?;
        if milliseconds > 0 { std::thread::sleep(std::time::Duration::from_millis(milliseconds as u64)); }
        Ok(Value::Null)
    })));
    Env::define(&time, "iso".into(), Value::Builtin(Rc::new(|args| {
        let [milliseconds] = args.as_slice() else { return Err(RuntimeError::new("time.iso expects one integer")); };
        let milliseconds = integer_value(milliseconds)?;
        let value = DateTime::<Utc>::from_timestamp_millis(milliseconds)
            .ok_or_else(|| RuntimeError::coded("E072", "time.iso timestamp is out of range"))?;
        Ok(Value::String(value.to_rfc3339_opts(SecondsFormat::AutoSi, true)))
    })));
    Env::define(&time, "parse".into(), Value::Builtin(Rc::new(|args| {
        let [Value::String(source)] = args.as_slice() else { return Err(RuntimeError::new("time.parse expects one string")); };
        let source = source.strip_suffix('Z').unwrap_or(source);
        let parsed = DateTime::parse_from_rfc3339(&format!("{}Z", source))
            .or_else(|_| DateTime::parse_from_rfc3339(source))
            .map_err(|error| RuntimeError::coded("E072", format!("time parse error: {}", error)))?;
        Ok(Value::Int(parsed.timestamp_millis()))
    })));
    Env::define(env, "time".into(), Value::Namespace(Rc::new(NamespaceValue { env: time })));
}

fn install_test(env: &EnvRef) {
    let test = module(env);
    let assertion = |name: &'static str, check: fn(&[Value]) -> Result<(), String>| {
        Value::Builtin(Rc::new(move |args| check(&args).map(|_| Value::Null).map_err(|message| RuntimeError::coded("E071", format!("{}: {}", name, message)))))
    };
    Env::define(&test, "assert".into(), assertion("assert", |args| {
        if args.is_empty() || args.len() > 2 { return Err("expected condition and optional message".into()); }
        if truthy(&args[0]) { Ok(()) } else { Err(args.get(1).map(ToString::to_string).unwrap_or_else(|| "assertion failed".into())) }
    }));
    Env::define(&test, "assertTrue".into(), assertion("assertTrue", |args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if truthy(&args[0]) { Ok(()) } else { Err(args.get(1).map(ToString::to_string).unwrap_or_else(|| "expected true".into())) }
    }));
    Env::define(&test, "assertFalse".into(), assertion("assertFalse", |args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if !truthy(&args[0]) { Ok(()) } else { Err(args.get(1).map(ToString::to_string).unwrap_or_else(|| "expected false".into())) }
    }));
    Env::define(&test, "assertEq".into(), assertion("assertEq", |args| {
        if args.len() < 2 || args.len() > 3 { return Err("expected two values and optional message".into()); }
        if args[0] == args[1] { Ok(()) } else { Err(args.get(2).map(ToString::to_string).unwrap_or_else(|| format!("expected {} == {}", args[0], args[1]))) }
    }));
    Env::define(&test, "assertNe".into(), assertion("assertNe", |args| {
        if args.len() < 2 || args.len() > 3 { return Err("expected two values and optional message".into()); }
        if args[0] != args[1] { Ok(()) } else { Err(args.get(2).map(ToString::to_string).unwrap_or_else(|| format!("expected values to differ: {}", args[0]))) }
    }));
    Env::define(&test, "assertNull".into(), assertion("assertNull", |args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if matches!(args[0], Value::Null) { Ok(()) } else { Err(args.get(1).map(ToString::to_string).unwrap_or_else(|| format!("expected null, got {}", type_name(&args[0])))) }
    }));
    Env::define(env, "test".into(), Value::Namespace(Rc::new(NamespaceValue { env: test })));
}

fn install_http(env: &EnvRef) {
    let http = module(env);
    let request = Rc::new(|args: Vec<Value>| -> Result<Value, RuntimeError> {
        if args.len() != 4 { return Err(RuntimeError::new("http.request expects method, url, body, and headers")); }
        let Value::String(method) = &args[0] else { return Err(RuntimeError::new("http method must be a string")); };
        let Value::String(url) = &args[1] else { return Err(RuntimeError::new("http url must be a string")); };
        let body = match &args[2] { Value::Null => None, Value::String(value) => Some(value.as_bytes().to_vec()), _ => return Err(RuntimeError::new("http body must be a string or null")) };
        let Value::Map(headers) = &args[3] else { return Err(RuntimeError::new("http headers must be a map")); };
        http_request(method, url, body, headers)
    });
    let get_request = request.clone();
    Env::define(&http, "get".into(), Value::Builtin(Rc::new(move |args| {
        let [Value::String(url)] = args.as_slice() else { return Err(RuntimeError::new("http.get expects one URL")); };
        get_request(vec![Value::String("GET".into()), Value::String(url.clone()), Value::Null, Value::Map(Rc::new(RefCell::new(Vec::new())))])
    })));
    let post_request = request.clone();
    Env::define(&http, "post".into(), Value::Builtin(Rc::new(move |args| {
        let [Value::String(url), Value::String(body)] = args.as_slice() else { return Err(RuntimeError::new("http.post expects URL and body")); };
        post_request(vec![Value::String("POST".into()), Value::String(url.clone()), Value::String(body.clone()), Value::Map(Rc::new(RefCell::new(Vec::new())))])
    })));
    Env::define(&http, "request".into(), Value::Builtin(request));
    Env::define(env, "http".into(), Value::Namespace(Rc::new(NamespaceValue { env: http })));
}

fn http_request(method: &str, url: &str, body: Option<Vec<u8>>, headers: &Rc<RefCell<Vec<(Value, Value)>>>) -> Result<Value, RuntimeError> {
    let remainder = url.strip_prefix("http://").ok_or_else(|| RuntimeError::coded("E072", "http client currently requires an http:// URL"))?;
    let (authority, path) = remainder.split_once('/').map(|(host, path)| (host, format!("/{}", path))).unwrap_or((remainder, "/".into()));
    let (host, port) = authority.rsplit_once(':').filter(|(_, port)| port.parse::<u16>().is_ok())
        .map(|(host, port)| (host, port.parse::<u16>().unwrap()))
        .unwrap_or((authority, 80));
    let mut stream = std::net::TcpStream::connect((host, port)).map_err(|error| RuntimeError::coded("E072", format!("http request failed: {}", error)))?;
    use std::io::{Read, Write};
    let body = body.unwrap_or_default();
    let mut request = format!("{} {} HTTP/1.1\r\nHost: {}\r\nConnection: close\r\n", method, path, host);
    for (key, value) in headers.borrow().iter() {
        let (Value::String(key), Value::String(value)) = (key, value) else { return Err(RuntimeError::new("http headers must be string pairs")); };
        request.push_str(&format!("{}: {}\r\n", key, value));
    }
    if !body.is_empty() { request.push_str(&format!("Content-Length: {}\r\n", body.len())); }
    request.push_str("\r\n");
    stream.write_all(request.as_bytes()).and_then(|_| stream.write_all(&body))
        .map_err(|error| RuntimeError::coded("E072", format!("http request failed: {}", error)))?;
    let mut response = Vec::new();
    stream.read_to_end(&mut response).map_err(|error| RuntimeError::coded("E072", format!("http response failed: {}", error)))?;
    let response = String::from_utf8_lossy(&response);
    let (head, body) = response.split_once("\r\n\r\n").unwrap_or((&response, ""));
    let status = head.lines().next().and_then(|line| line.split_whitespace().nth(1)).and_then(|value| value.parse::<i64>().ok()).unwrap_or(-1);
    let mut response_headers = Vec::new();
    for line in head.lines().skip(1) {
        if let Some((key, value)) = line.split_once(':') { response_headers.push((Value::String(key.trim().into()), Value::String(value.trim().into()))); }
    }
    Ok(Value::Map(Rc::new(RefCell::new(vec![
        (Value::String("status".into()), Value::Int(status)),
        (Value::String("body".into()), Value::String(body.into())),
        (Value::String("headers".into()), Value::Map(Rc::new(RefCell::new(response_headers)))),
    ]))))
}

fn install_file(env: &EnvRef) {
    let file = module(env);
    Env::define(&file, "read".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.read expects one string")); }; std::fs::read_to_string(path).map(Value::String).map_err(|error| RuntimeError::coded("E072", error.to_string())) })));
    Env::define(&file, "write".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path), Value::String(contents)] = args.as_slice() else { return Err(RuntimeError::new("file.write expects path and contents")); }; std::fs::write(path, contents).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "append".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path), Value::String(contents)] = args.as_slice() else { return Err(RuntimeError::new("file.append expects path and contents")); }; use std::io::Write; let mut handle = std::fs::OpenOptions::new().append(true).create(true).open(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; handle.write_all(contents.as_bytes()).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "delete".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.delete expects one string")); }; if std::fs::remove_file(path).is_err() { std::fs::remove_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; } Ok(Value::Null) })));
    Env::define(&file, "exists".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.exists expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).exists())) })));
    Env::define(&file, "list".into(), Value::Builtin(Rc::new(|args| {
        let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.list expects one string")); };
        let mut entries = std::fs::read_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?
            .map(|entry| entry.map(|entry| Value::String(entry.file_name().to_string_lossy().into_owned())))
            .collect::<Result<Vec<_>, _>>().map_err(|error| RuntimeError::coded("E072", error.to_string()))?;
        entries.sort_by_key(|entry| entry.to_string());
        Ok(Value::List(Rc::new(RefCell::new(entries))))
    })));
    Env::define(&file, "isFile".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.isFile expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).is_file())) })));
    Env::define(&file, "isDir".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.isDir expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).is_dir())) })));
    Env::define(&file, "mkdir".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.mkdir expects one string")); }; std::fs::create_dir_all(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "size".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.size expects one string")); }; Ok(Value::Int(std::fs::metadata(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?.len() as i64)) })));
    Env::define(&file, "rename".into(), Value::Builtin(Rc::new(|args| { let [Value::String(from), Value::String(to)] = args.as_slice() else { return Err(RuntimeError::new("file.rename expects two strings")); }; std::fs::rename(from, to).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "remove".into(), Value::Builtin(Rc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.remove expects one string")); }; if std::fs::remove_file(path).is_err() { std::fs::remove_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; } Ok(Value::Null) })));
    Env::define(&file, "temp".into(), Value::Builtin(Rc::new(|args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("file.temp expects one string")); }; let path = unique_temp_path(prefix); std::fs::File::create(&path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::String(path.to_string_lossy().into())) })));
    Env::define(&file, "tempDir".into(), Value::Builtin(Rc::new(|args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("file.tempDir expects one string")); }; let path = unique_temp_path(prefix); std::fs::create_dir(&path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::String(path.to_string_lossy().into())) })));
    Env::define(env, "file".into(), Value::Namespace(Rc::new(NamespaceValue { env: file })));
}

fn install_string(env: &EnvRef) {
    let string = module(env);
    Env::define(&string, "len".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.len expects one string")); }; Ok(Value::Int(value.chars().count() as i64)) })));
    Env::define(&string, "byteLength".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.byteLength expects one string")); }; Ok(Value::Int(value.len() as i64)) })));
    Env::define(&string, "charAt".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), index] = args.as_slice() else { return Err(RuntimeError::new("string.charAt expects string and index")); }; let index = integer_value(index)?; if index < 0 { return Ok(Value::Null); } Ok(value.chars().nth(index as usize).map(Value::Char).unwrap_or(Value::Null)) })));
    Env::define(&string, "substring".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), start, end] = args.as_slice() else { return Err(RuntimeError::new("string.substring expects string and two indexes")); }; let start = integer_value(start)?.max(0) as usize; let end = integer_value(end)?.max(start as i64) as usize; Ok(Value::String(value.chars().skip(start).take(end.saturating_sub(start)).collect())) })));
    Env::define(&string, "contains".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("string.contains expects two strings")); }; Ok(Value::Bool(value.contains(needle))) })));
    Env::define(&string, "startsWith".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("string.startsWith expects two strings")); }; Ok(Value::Bool(value.starts_with(prefix))) })));
    Env::define(&string, "endsWith".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), Value::String(suffix)] = args.as_slice() else { return Err(RuntimeError::new("string.endsWith expects two strings")); }; Ok(Value::Bool(value.ends_with(suffix))) })));
    Env::define(&string, "indexOf".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("string.indexOf expects two strings")); }; Ok(Value::Int(value.find(needle).map(|index| value[..index].chars().count() as i64).unwrap_or(-1))) })));
    Env::define(&string, "toUpper".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.toUpper expects one string")); }; Ok(Value::String(value.to_uppercase())) })));
    Env::define(&string, "toLower".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.toLower expects one string")); }; Ok(Value::String(value.to_lowercase())) })));
    Env::define(&string, "trim".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.trim expects one string")); }; Ok(Value::String(value.trim().into())) })));
    Env::define(&string, "split".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("string.split expects two strings")); }; Ok(Value::List(Rc::new(RefCell::new(value.split(separator).map(|part| Value::String(part.into())).collect())))) })));
    Env::define(&string, "join".into(), Value::Builtin(Rc::new(|args| { let [Value::List(values), Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("string.join expects list and separator")); }; Ok(Value::String(values.borrow().iter().map(ToString::to_string).collect::<Vec<_>>().join(separator))) })));
    Env::define(&string, "repeat".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), count] = args.as_slice() else { return Err(RuntimeError::new("string.repeat expects string and count")); }; Ok(Value::String(value.repeat(integer_value(count)?.max(0) as usize))) })));
    Env::define(&string, "padStart".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), width, Value::String(fill)] = args.as_slice() else { return Err(RuntimeError::new("string.padStart expects string, width, fill")); }; Ok(Value::String(pad_string(value, integer_value(width)?, fill, true))) })));
    Env::define(&string, "padEnd".into(), Value::Builtin(Rc::new(|args| { let [Value::String(value), width, Value::String(fill)] = args.as_slice() else { return Err(RuntimeError::new("string.padEnd expects string, width, fill")); }; Ok(Value::String(pad_string(value, integer_value(width)?, fill, false))) })));
    let converter = Rc::new(|args: Vec<Value>| { if args.len() != 1 { return Err(RuntimeError::new("string expects 1 argument")); } Ok(Value::String(args[0].to_string())) });
    Env::define(env, "string".into(), Value::Module(Rc::new(ModuleValue { env: string, callable: Some(converter) })));
}

fn pad_string(value: &str, width: i64, fill: &str, start: bool) -> String {
    let width = width.max(0) as usize; let length = value.chars().count(); if length >= width || fill.is_empty() { return value.into(); }
    let needed = width - length; let padding: String = fill.chars().cycle().take(needed).collect(); if start { format!("{}{}", padding, value) } else { format!("{}{}", value, padding) }
}

fn unique_temp_path(prefix: &str) -> std::path::PathBuf {
    let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
    std::env::temp_dir().join(format!("{}{}-{}", prefix, std::process::id(), stamp))
}

fn type_name(value: &Value) -> String { match value { Value::Null => "null".into(), Value::Bool(_) => "bool".into(), Value::Byte(_) => "byte".into(), Value::Int(_) => "int".into(), Value::Float(_) => "float".into(), Value::Char(_) => "char".into(), Value::String(_) => "string".into(), Value::Regex(_) => "regex".into(), Value::List(_) => "list".into(), Value::Stack(_) => "stack".into(), Value::Map(_) => "map".into(), Value::Struct(value) => value.borrow().definition.name.to_lowercase(), Value::StructType(value) => value.name.to_lowercase(), Value::Enum(value) => value.definition.name.to_lowercase(), Value::EnumType(value) => value.name.to_lowercase(), Value::Namespace(_) | Value::Module(_) => "namespace".into(), Value::Exception(_) => "exception".into(), Value::Function(_) | Value::Builtin(_) => "function".into() } }
fn truthy(value: &Value) -> bool { match value { Value::Null => false, Value::Bool(v) => *v, Value::Byte(v) => *v != 0, Value::Int(v) => *v != 0, Value::Float(v) => *v != 0.0, Value::String(v) => !v.is_empty(), Value::List(v) | Value::Stack(v) => !v.borrow().is_empty(), Value::Map(v) => !v.borrow().is_empty(), _ => true } }

fn lookup_path(env: &EnvRef, path: &str) -> Option<Value> {
    let mut parts = path.split('.'); let first = parts.next()?; let mut value = Env::get(env, first)?;
    for part in parts { value = member(value, part, env).ok()?; }
    Some(value)
}

fn call(callee: Value, args: Vec<Value>) -> Result<Value, RuntimeError> {
    match callee {
        Value::Builtin(function) => function(args),
        Value::Module(module) if module.callable.is_some() => (module.callable.as_ref().unwrap())(args),
        Value::Function(function) => {
            if let Some(code) = &function.bytecode {
                return bc_call(function.clone(), code.clone(), args);
            }
            let mut generic_bindings = HashMap::new();
            for (param, argument) in function.function.params.iter().zip(args.iter()) {
                if param.typ.args.is_empty() && param.typ.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) {
                    generic_bindings.insert(param.typ.name.clone(), argument.clone());
                }
            }
            for type_param in &function.function.type_params {
                if let Some(argument) = generic_bindings.get(&type_param.name) {
                    for constraint in &type_param.constraints {
                        let method_name = match constraint.name.rsplit('.').next().unwrap_or(&constraint.name) {
                            "Stringable" => "string", "Equatable" => "equals", "Countable" => "len",
                            "Iterable" => "iterator", "Collection" => "iterator", _ => continue,
                        };
                        if member(argument.clone(), method_name, &function.closure).is_err() {
                            return Err(RuntimeError::coded("E067", format!("value of type {} does not satisfy generic constraint {}", type_name(argument), constraint.name)));
                        }
                    }
                }
            }
            let fixed = function.function.params.iter().filter(|param| !param.variadic).count();
            let variadic = function.function.params.last().is_some_and(|param| param.variadic);
            if (!variadic && function.function.params.len() != args.len()) || (variadic && args.len() < fixed) {
                if function.function.name == "<closure>" { return Err(RuntimeError::coded("E068", format!("closure expects {} argument(s), found {}", function.function.params.len(), args.len()))); }
                return Err(RuntimeError::coded("E068", "function argument count mismatch"));
            }
            let local = Env::new(Some(function.closure.clone()));
            if let Some(receiver) = &function.receiver {
                Env::define(&local, "self".into(), receiver.clone());
                if let Value::Struct(value) = receiver {
                    for (name, field) in value.borrow().fields.clone() { Env::define(&local, name, field); }
                    for (name, method) in value.borrow().definition.methods.clone() {
                        Env::define(&local, name, Value::Function(Rc::new(Callable {
                            function: method,
                            closure: local.clone(),
                            receiver: Some(receiver.clone()),
                            bytecode: None,
                        })));
                    }
                }
            }
            let mut argument_index = 0;
            for param in &function.function.params {
                if param.variadic {
                    Env::define(&local, param.name.clone(), Value::List(Rc::new(RefCell::new(args[argument_index..].to_vec()))));
                    argument_index = args.len();
                } else {
                    Env::define(&local, param.name.clone(), args[argument_index].clone());
                    argument_index += 1;
                }
            }
            let flow = exec_block(function.function.body.as_ref().ok_or_else(|| RuntimeError::new("function has no body"))?, &local)?;
            if let Some(Value::Struct(receiver)) = &function.receiver {
                let mut receiver = receiver.borrow_mut();
                for field in receiver.fields.keys().cloned().collect::<Vec<_>>() {
                    if let Some(value) = Env::get(&local, &field) { receiver.fields.insert(field, value); }
                }
            }
            match flow { Flow::Return(value) => Ok(value), Flow::Normal => Ok(Value::Null), Flow::Throw(error) => Err(error), Flow::Break | Flow::Continue => Err(RuntimeError::new("invalid control flow")) }
        }
        Value::Null => Err(RuntimeError::coded("E031", "null reference: value is not callable")),
        _ => Err(RuntimeError::coded("E068", "value is not callable")),
    }
}

fn exec_block(block: &Block, env: &EnvRef) -> Result<Flow, RuntimeError> {
    for statement in &block.statements { match exec_stmt(statement, env)? { Flow::Normal => {}, flow => return Ok(flow) } }
    Ok(Flow::Normal)
}

fn exec_stmt(statement: &Stmt, env: &EnvRef) -> Result<Flow, RuntimeError> {
    match statement {
        Stmt::Var { name, typ, value, .. } => {
            let mut value = value.as_ref().map(|expr| eval(expr, env)).transpose()?.unwrap_or(Value::Null);
            if typ.as_ref().is_some_and(|typ| typ.name == "exception") {
                if let Value::String(message) = value { value = Value::Exception(RuntimeError::new(message)); }
            }
            if let Value::Enum(enum_value) = &value {
                if enum_value.unresolved {
                    if typ.as_ref().is_some_and(|typ| typ.name.rsplit('.').next() == enum_value.definition.name.rsplit('.').next() && typ.args.len() == enum_value.definition.type_param_count) {
                        let mut resolved = (**enum_value).clone(); resolved.unresolved = false; value = Value::Enum(Rc::new(resolved));
                    } else if typ.as_ref().is_some_and(|typ| typ.name == "any") {
                        return Err(RuntimeError::coded("E067", "unbound enum type parameter"));
                    }
                }
            }
            if let Value::Struct(struct_value) = &value {
                if struct_value.borrow().unresolved && typ.as_ref().is_some_and(|typ| typ.name == "any") {
                    return Err(RuntimeError::coded("E067", "struct literal type parameter cannot be inferred from null"));
                }
            }
            Env::define(env, name.clone(), value); Ok(Flow::Normal)
        }
        Stmt::Expr(expr) => { eval(expr, env)?; Ok(Flow::Normal) }
        Stmt::Return(expr) => Ok(Flow::Return(expr.as_ref().map(|e| eval(e, env)).transpose()?.unwrap_or(Value::Null))),
        Stmt::Block(block) => { let child = Env::new(Some(env.clone())); exec_block(block, &child) }
        Stmt::If { condition, then_block, else_branch } => { if truthy(&eval(condition, env)?) { exec_block(then_block, &Env::new(Some(env.clone()))) } else if let Some(branch) = else_branch { exec_stmt(branch, env) } else { Ok(Flow::Normal) } }
        Stmt::While { condition, body } => { loop { if !truthy(&eval(condition, env)?) { break; } match exec_block(body, &Env::new(Some(env.clone())))? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) } } Ok(Flow::Normal) }
        Stmt::For { names, iterable, body } => {
            let source = eval(iterable, env)?;
            let entries: Vec<Vec<Value>> = match source {
                Value::List(values) => values.borrow().iter().cloned().map(|value| vec![value]).collect(),
                Value::Map(values) => values.borrow().iter().cloned().map(|(key, value)| vec![key, value]).collect(),
                Value::Stack(values) => values.borrow().iter().rev().cloned().map(|value| vec![value]).collect(),
                Value::String(value) => value.chars().map(|value| vec![Value::Char(value)]).collect(),
                Value::Struct(_) => {
                    let iterator = member(source.clone(), "iterator", env)?;
                    match call(iterator, Vec::new())? {
                        Value::List(values) => values.borrow().iter().cloned().map(|value| vec![value]).collect(),
                        _ => return Err(RuntimeError::new("iterator must return a list")),
                    }
                }
                _ => return Err(RuntimeError::new("value is not iterable")),
            };
            for entry in entries {
                if entry.len() < names.len() { return Err(RuntimeError::new("loop binding count mismatch")); }
                let child = Env::new(Some(env.clone()));
                for (name, value) in names.iter().zip(entry) { Env::define(&child, name.clone(), value); }
                match exec_block(body, &child)? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) }
            }
            Ok(Flow::Normal)
        }
        Stmt::Break => Ok(Flow::Break), Stmt::Continue => Ok(Flow::Continue),
        Stmt::Throw(expr) => Err(RuntimeError::coded("E000", eval(expr, env)?.to_string())),
        Stmt::Switch { value, cases } => {
            let value = eval(value, env)?;
            for (pattern, body) in cases {
                let bindings = match pattern { Some(pattern) => match_pattern(pattern, &value)?, None => Some(HashMap::new()) };
                if let Some(bindings) = bindings {
                    let child = Env::new(Some(env.clone()));
                    for (name, bound) in bindings { Env::define(&child, name, bound); }
                    return exec_block(body, &child);
                }
            }
            Ok(Flow::Normal)
        }
        Stmt::Try { body, catch_name, catch, finally } => {
            let mut result = match exec_block(body, &Env::new(Some(env.clone()))) {
                Ok(flow) => Ok(flow),
                Err(error) => {
                    if let Some(catch_block) = catch {
                        let child = Env::new(Some(env.clone()));
                        if let Some(name) = catch_name { Env::define(&child, name.clone(), Value::Exception(error.clone())); }
                        exec_block(catch_block, &child)
                    } else { Err(error) }
                }
            };
            if let Some(finally_block) = finally {
                let finally_result = exec_block(finally_block, &Env::new(Some(env.clone())));
                if finally_result.is_err() || matches!(finally_result, Ok(Flow::Return(_) | Flow::Throw(_))) { result = finally_result; }
            }
            result
        }
    }
}

fn eval(expr: &Expr, env: &EnvRef) -> Result<Value, RuntimeError> {
    match expr {
        Expr::Int(v) => Ok(Value::Int(*v)), Expr::Float(v) => Ok(Value::Float(*v)), Expr::Bool(v) => Ok(Value::Bool(*v)), Expr::Char(v) => Ok(Value::Char(*v)), Expr::String(v) => Ok(Value::String(v.clone())), Expr::Null => Ok(Value::Null),
        Expr::Name { name, .. } => lookup_path(env, name).ok_or_else(|| RuntimeError::new(format!("undefined name {}", name))),
        Expr::List(items) => Ok(Value::List(Rc::new(RefCell::new(items.iter().map(|item| eval(item, env)).collect::<Result<Vec<_>, _>>()?)))),
        Expr::Map(items) => Ok(Value::Map(Rc::new(RefCell::new(items.iter().map(|(key, value)| Ok((eval(key, env)?, eval(value, env)?))).collect::<Result<Vec<_>, RuntimeError>>()?)))),
        Expr::Spread(inner) => eval(inner, env),
        Expr::Function { params, return_type, body } => Ok(Value::Function(Rc::new(Callable { function: Function { name: "<closure>".into(), public: false, mutating: false, params: params.clone(), return_type: return_type.clone(), type_params: Vec::new(), body: Some(body.clone()) }, closure: env.clone(), receiver: None, bytecode: None }))),
        Expr::Call { callee, args, .. } => {
            let callee_value = eval(callee, env)?; let mut values = Vec::new();
            if let Value::Function(function) = &callee_value {
                let explicit = match callee.as_ref() {
                    Expr::Name { type_args, .. } | Expr::Member { type_args, .. } => type_args.len(),
                    _ => 0,
                };
                if !function.function.type_params.is_empty() && explicit == 0 && args.iter().any(|arg| matches!(&arg.expr, Expr::Null)) {
                    return Err(RuntimeError::coded("E067", "a null value cannot infer a generic type parameter"));
                }
            }
            if args.iter().any(|arg| matches!(&arg.expr, Expr::Spread(_))) {
                for arg in args {
                    if let Expr::Spread(inner) = &arg.expr {
                        let value = eval(inner, env)?;
                        if let Value::List(items) = value { values.extend(items.borrow().iter().cloned()); } else { return Err(RuntimeError::new("spread value is not a list")); }
                    } else { values.push(eval(&arg.expr, env)?); }
                }
                return call(callee_value, values);
            }
            for arg in args { let value = eval(&arg.expr, env)?; if arg.spread { if let Value::List(items) = value { values.extend(items.borrow().iter().cloned()); } else { return Err(RuntimeError::new("spread value is not a list")); } } else { values.push(value); } }
            if let Value::Function(function) = &callee_value {
                let explicit = match callee.as_ref() {
                    Expr::Name { type_args, .. } | Expr::Member { type_args, .. } => type_args.len(),
                    _ => 0,
                };
                if !function.function.type_params.is_empty() && explicit != 0 && explicit != function.function.type_params.len() {
                    return Err(RuntimeError::coded("E067", "generic type argument count mismatch"));
                }
            }
            call(callee_value, values)
        }
        Expr::Member { object, name, type_args } => {
            let explicit_count = if !type_args.is_empty() { type_args.len() } else { match object.as_ref() { Expr::Name { type_args, .. } => type_args.len(), _ => 0 } };
            let object = eval(object, env)?;
            if explicit_count > 0 {
                if let Value::EnumType(definition) = object {
                    let mut instantiated = (*definition).clone();
                    if instantiated.type_param_count == explicit_count { instantiated.type_param_count = 0; }
                    return member(Value::EnumType(Rc::new(instantiated)), name, env);
                }
            }
            member(object, name, env)
        }
        Expr::Index { object, index } => {
            let object = eval(object, env)?; let index = eval(index, env)?;
            match object {
                Value::List(items) => { let i = integer_value(&index)?; if i < 0 { return Err(RuntimeError::new("index out of range")); } items.borrow().get(i as usize).cloned().ok_or_else(|| RuntimeError::new("index out of range")) }
                Value::String(text) => { let i = integer_value(&index)?; if i < 0 { return Err(RuntimeError::new("index out of range")); } text.chars().nth(i as usize).map(Value::Char).ok_or_else(|| RuntimeError::new("index out of range")) }
                Value::Map(items) => items.borrow().iter().find(|(key, _)| *key == index).map(|(_, value)| value.clone()).ok_or_else(|| RuntimeError::new("key not found")),
                _ => Err(RuntimeError::new("value is not indexable")),
            }
        }
        Expr::Struct { name, fields, .. } => {
            let definition = match lookup_path(env, name) { Some(Value::StructType(definition)) => definition, _ => return Err(RuntimeError::new(format!("unknown struct {}", name))) };
            let values = fields.iter().map(|(field, value)| Ok((field.clone(), eval(value, env)?))).collect::<Result<HashMap<_, _>, RuntimeError>>()?;
            let unresolved = definition.type_param_count > 0 && values.values().any(|value| matches!(value, Value::Null));
            Ok(Value::Struct(Rc::new(RefCell::new(StructValue { definition, fields: values, unresolved }))))
        }
        Expr::Unary { op, expr } => { let value = eval(expr, env)?; match (op.as_str(), value) { ("!", value) => Ok(Value::Bool(!truthy(&value))), ("-", Value::Int(v)) => Ok(Value::Int(-v)), ("-", Value::Float(v)) => Ok(Value::Float(-v)), ("~", Value::Int(v)) => Ok(Value::Int(!v)), _ => Err(RuntimeError::new("invalid unary operation")) } }
        Expr::Binary { left, op, right } => {
            if op == "&&" { let l = eval(left, env)?; return if truthy(&l) { Ok(Value::Bool(truthy(&eval(right, env)?))) } else { Ok(Value::Bool(false)) }; }
            if op == "||" { let l = eval(left, env)?; return if truthy(&l) { Ok(Value::Bool(true)) } else { Ok(Value::Bool(truthy(&eval(right, env)?))) }; }
            if op == "=" {
                let value = eval(right, env)?;
                if let Expr::Name { name, .. } = left.as_ref() { if !Env::set(env, name, value.clone()) { return Err(RuntimeError::new(format!("undefined name {}", name))); } return Ok(value); }
                if let Expr::Member { object, name, .. } = left.as_ref() { let target = eval(object, env)?; if let Value::Struct(target) = target { target.borrow_mut().fields.insert(name.clone(), value.clone()); let _ = Env::set(env, name, value.clone()); return Ok(value); } return Err(RuntimeError::new("assignment target is not a struct field")); }
                if let Expr::Index { object, index } = left.as_ref() {
                    let target = eval(object, env)?; let key = eval(index, env)?;
                    if let Value::List(items) = &target { let index = integer_value(&key)?; if index < 0 { return Err(RuntimeError::new("index out of range")); } let mut items = items.borrow_mut(); let slot = items.get_mut(index as usize).ok_or_else(|| RuntimeError::new("index out of range"))?; *slot = value.clone(); return Ok(value); }
                    if let Value::Map(items) = target { let mut items = items.borrow_mut(); if let Some((_, existing)) = items.iter_mut().find(|(entry, _)| *entry == key) { *existing = value.clone(); } else { items.push((key, value.clone())); } return Ok(value); }
                    return Err(RuntimeError::new("assignment target is not a map entry"));
                }
                return Err(RuntimeError::new("assignment target is invalid"));
            }
            let l = eval(left, env)?; let r = eval(right, env)?; binary(&l, op, &r)
        }
    }
}

fn member(object: Value, name: &str, env: &EnvRef) -> Result<Value, RuntimeError> {
    match (object, name) {
        (Value::String(text), name) => {
            let text = Rc::new(text);
            match name {
                "string" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String((*text).clone())) }))),
                "equals" => Ok(Value::Builtin(Rc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("equals expects one argument")); }; Ok(Value::Bool(Value::String((*text).clone()) == *other)) }))),
                "len" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(text.chars().count() as i64)) }))),
                "byteLength" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("byteLength expects no arguments")); } Ok(Value::Int(text.len() as i64)) }))),
                "charAt" => Ok(Value::Builtin(Rc::new(move |args| { let [index] = args.as_slice() else { return Err(RuntimeError::new("charAt expects one index")); }; let index = integer_value(index)?; Ok(if index < 0 { Value::Null } else { text.chars().nth(index as usize).map(Value::Char).unwrap_or(Value::Null) }) }))),
                "substring" => Ok(Value::Builtin(Rc::new(move |args| { let [start, end] = args.as_slice() else { return Err(RuntimeError::new("substring expects two indexes")); }; let start = integer_value(start)?.max(0) as usize; let end = integer_value(end)?.max(start as i64) as usize; Ok(Value::String(text.chars().skip(start).take(end.saturating_sub(start)).collect())) }))),
                "contains" => Ok(Value::Builtin(Rc::new(move |args| { let [Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("contains expects one string")); }; Ok(Value::Bool(text.contains(needle))) }))),
                "startsWith" => Ok(Value::Builtin(Rc::new(move |args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("startsWith expects one string")); }; Ok(Value::Bool(text.starts_with(prefix))) }))),
                "endsWith" => Ok(Value::Builtin(Rc::new(move |args| { let [Value::String(suffix)] = args.as_slice() else { return Err(RuntimeError::new("endsWith expects one string")); }; Ok(Value::Bool(text.ends_with(suffix))) }))),
                "indexOf" => Ok(Value::Builtin(Rc::new(move |args| { let [Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("indexOf expects one string")); }; Ok(Value::Int(text.find(needle).map(|index| text[..index].chars().count() as i64).unwrap_or(-1))) }))),
                "trim" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("trim expects no arguments")); }; Ok(Value::String(text.trim().into())) }))),
                "toUpper" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("toUpper expects no arguments")); }; Ok(Value::String(text.to_uppercase())) }))),
                "toLower" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("toLower expects no arguments")); }; Ok(Value::String(text.to_lowercase())) }))),
                "split" => Ok(Value::Builtin(Rc::new(move |args| { let [Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("split expects one string")); }; Ok(Value::List(Rc::new(RefCell::new(text.split(separator).map(|part| Value::String(part.into())).collect())))) }))),
                _ => Err(RuntimeError::new(format!("unknown member {}", name))),
            }
        }
        (Value::List(items), name) => {
            match name {
                "iterator" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("iterator expects no arguments")); } Ok(Value::List(Rc::new(RefCell::new(items.borrow().clone())))) }))),
                "string" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(items.borrow().iter().map(ToString::to_string).collect::<Vec<_>>().join(", "))) }))),
                "len" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
                "contains" => Ok(Value::Builtin(Rc::new(move |args| { let [needle] = args.as_slice() else { return Err(RuntimeError::new("contains expects one argument")); }; Ok(Value::Bool(items.borrow().iter().any(|item| item == needle))) }))),
                "first" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("first expects no arguments")); } Ok(items.borrow().first().cloned().unwrap_or(Value::Null)) }))),
                "last" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("last expects no arguments")); } Ok(items.borrow().last().cloned().unwrap_or(Value::Null)) }))),
                "reverse" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("reverse expects no arguments")); } let mut result = items.borrow().clone(); result.reverse(); Ok(Value::List(Rc::new(RefCell::new(result)))) }))),
                "map" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("map expects one function")); }; let mut result = Vec::new(); for item in items.borrow().iter().cloned() { result.push(call(function.clone(), vec![item])?); } Ok(Value::List(Rc::new(RefCell::new(result)))) }))),
                "filter" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("filter expects one function")); }; let mut result = Vec::new(); for item in items.borrow().iter().cloned() { if truthy(&call(function.clone(), vec![item.clone()])?) { result.push(item); } } Ok(Value::List(Rc::new(RefCell::new(result)))) }))),
                "fold" => Ok(Value::Builtin(Rc::new(move |args| { let [initial, function] = args.as_slice() else { return Err(RuntimeError::new("fold expects an initial value and function")); }; let mut result = initial.clone(); for item in items.borrow().iter().cloned() { result = call(function.clone(), vec![result, item])?; } Ok(result) }))),
                "reduce" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("reduce expects one function")); }; let values = items.borrow().clone(); let mut values = values.into_iter(); let Some(mut result) = values.next() else { return Err(RuntimeError::coded("E072", "reduce of empty list")); }; for item in values { result = call(function.clone(), vec![result, item])?; } Ok(result) }))),
                "find" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("find expects one function")); }; for item in items.borrow().iter().cloned() { if truthy(&call(function.clone(), vec![item.clone()])?) { return Ok(item); } } Ok(Value::Null) }))),
                "any" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("any expects one function")); }; for item in items.borrow().iter().cloned() { if truthy(&call(function.clone(), vec![item])?) { return Ok(Value::Bool(true)); } } Ok(Value::Bool(false)) }))),
                "all" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("all expects one function")); }; for item in items.borrow().iter().cloned() { if !truthy(&call(function.clone(), vec![item])?) { return Ok(Value::Bool(false)); } } Ok(Value::Bool(true)) }))),
                "sort" => Ok(Value::Builtin(Rc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("sort expects one function")); }; let mut result = items.borrow().clone(); for i in 1..result.len() { let mut j = i; while j > 0 { let order = integer_value(&call(function.clone(), vec![result[j - 1].clone(), result[j].clone()])?)?; if order <= 0 { break; } result.swap(j - 1, j); j -= 1; } } Ok(Value::List(Rc::new(RefCell::new(result)))) }))),
                _ => Err(RuntimeError::new(format!("unknown member {}", name))),
            }
        }
        (Value::Stack(items), name) => {
            match name {
                "len" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
                "isEmpty" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("isEmpty expects no arguments")); } Ok(Value::Bool(items.borrow().is_empty())) }))),
                "push" => Ok(Value::Builtin(Rc::new(move |args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("push expects one argument")); }; items.borrow_mut().push(value.clone()); Ok(Value::Null) }))),
                "pop" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("pop expects no arguments")); } items.borrow_mut().pop().ok_or_else(|| RuntimeError::coded("E072", "stack.pop: stack is empty")) }))),
                "peek" => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("peek expects no arguments")); } items.borrow().last().cloned().ok_or_else(|| RuntimeError::coded("E072", "stack.peek: stack is empty")) }))),
                _ => Err(RuntimeError::new(format!("unknown member {}", name))),
            }
        }
        (Value::Map(items), "len") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
        (Value::Map(items), "contains") => Ok(Value::Builtin(Rc::new(move |args| { let [needle] = args.as_slice() else { return Err(RuntimeError::new("contains expects one argument")); }; Ok(Value::Bool(items.borrow().iter().any(|(key, _)| key == needle))) }))),
        (Value::Function(function), "string") => {
            let name = function.function.name.clone();
            Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(if name == "<closure>" { "<closure>".into() } else { format!("<function {}>", name) })) })))
        }
        (Value::Function(function), "equals") => {
            let expected = Value::Function(function);
            Ok(Value::Builtin(Rc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("equals expects one argument")); }; Ok(Value::Bool(expected == *other)) })))
        }
        (Value::Builtin(function), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } let _ = &function; Ok(Value::String("<function>".into())) }))),
        (Value::Int(value), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Byte(value), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Float(value), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Bool(value), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Char(value), "string") => Ok(Value::Builtin(Rc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Exception(error), "message") => Ok(Value::String(error.message)),
        (Value::Exception(error), "code") => Ok(Value::String(error.code)),
        (Value::Exception(_), "trace") => Ok(Value::String(String::new())),
        (Value::EnumType(definition), member_name) => {
            let member = definition.members.get(member_name).ok_or_else(|| RuntimeError::new(format!("unknown enum member {}", member_name)))?.clone();
            if member.payload_count == 0 {
                Ok(Value::Enum(Rc::new(EnumValue { unresolved: definition.type_param_count > 0, definition, member: member_name.into(), payload: Vec::new() })))
            } else {
                let definition = definition.clone(); let member_name = member_name.to_string();
                Ok(Value::Builtin(Rc::new(move |args| {
                    if args.len() != member.payload_count { return Err(RuntimeError::coded("E069", "enum payload arity mismatch")); }
                    Ok(Value::Enum(Rc::new(EnumValue { unresolved: definition.type_param_count > args.len(), definition: definition.clone(), member: member_name.clone(), payload: args })))
                })))
            }
        }
        (Value::Enum(value), "member") => Ok(Value::String(value.member.clone())),
        (Value::Namespace(namespace), name) => Env::get(&namespace.env, name).ok_or_else(|| RuntimeError::new(format!("unknown namespace member {}", name))),
        (Value::Module(module), name) => Env::get(&module.env, name).ok_or_else(|| RuntimeError::new(format!("unknown module member {}", name))),
        (Value::Struct(value), name) => {
            let definition = value.borrow().definition.clone();
            let internal_receiver = matches!(Env::get(env, "self"), Some(Value::Struct(receiver)) if Rc::ptr_eq(&receiver, &value));
            let same_package = env.borrow().package == definition.owner_package;
            if let Some(field) = value.borrow().fields.get(name).cloned() {
                if !definition.field_public.get(name).copied().unwrap_or(false) && !same_package && !internal_receiver {
                    return Err(RuntimeError::coded("E070", format!("field '{}' is private", name)));
                }
                return Ok(field);
            }
            let method = definition.methods.get(name).cloned().ok_or_else(|| RuntimeError::new(format!("unknown member {}", name)))?;
            if !definition.method_public.get(name).copied().unwrap_or(false) && !same_package && !internal_receiver {
                return Err(RuntimeError::coded("E070", format!("method '{}' is private", name)));
            }
            Ok(Value::Function(Rc::new(Callable { function: method.clone(), closure: env.clone(), receiver: Some(Value::Struct(value)), bytecode: Some(bc_compile_block(method.body.as_ref())) })))
        }
        (Value::StructType(_), _) | (_, _) => Err(RuntimeError::new(format!("unknown member {}", name))),
    }
}

fn match_pattern(pattern: &Expr, value: &Value) -> Result<Option<HashMap<String, Value>>, RuntimeError> {
    let mut bindings = HashMap::new();
    if match_pattern_into(pattern, value, &mut bindings)? { Ok(Some(bindings)) } else { Ok(None) }
}

fn match_pattern_into(pattern: &Expr, value: &Value, bindings: &mut HashMap<String, Value>) -> Result<bool, RuntimeError> {
    match pattern {
        Expr::Name { name, .. } if name == "_" => Ok(true),
        Expr::Name { name, .. } => { bindings.insert(name.clone(), value.clone()); Ok(true) }
        Expr::Null => Ok(matches!(value, Value::Null)),
        Expr::Int(expected) => Ok(matches!(value, Value::Int(actual) if actual == expected)),
        Expr::Float(expected) => Ok(matches!(value, Value::Float(actual) if actual == expected)),
        Expr::Bool(expected) => Ok(matches!(value, Value::Bool(actual) if actual == expected)),
        Expr::Char(expected) => Ok(matches!(value, Value::Char(actual) if actual == expected)),
        Expr::String(expected) => Ok(matches!(value, Value::String(actual) if actual == expected)),
        Expr::Call { callee, args, .. } if matches!(callee.as_ref(), Expr::Name { name, .. } if name == "regex") => {
            if args.len() != 1 { return Ok(false); }
            let Expr::String(pattern) = &args[0].expr else { return Ok(false); };
            let Value::String(actual) = value else { return Ok(false); };
            Ok(regex::Regex::new(pattern).map(|regex| regex.is_match(actual)).unwrap_or(false))
        }
        Expr::Member { object, name, .. } => match value {
            Value::Enum(actual) => Ok(enum_pattern_name(object, name) && actual.member == *name && actual.payload.is_empty()),
            _ => Ok(false),
        },
        Expr::Call { callee, args, .. } => {
            let (enum_expr, case_name) = match callee.as_ref() {
                Expr::Member { object, name, .. } => (Some(object.as_ref()), name.as_str()),
                Expr::Name { name, .. } => (None, name.as_str()),
                _ => return Ok(false),
            };
            let Value::Enum(actual) = value else { return Ok(false) };
            if actual.member != case_name || !enum_expr.map(|expr| enum_pattern_type(expr, &actual.definition.name)).unwrap_or(true) || actual.payload.len() != args.len() { return Ok(false); }
            for (arg, actual_value) in args.iter().map(|arg| &arg.expr).zip(actual.payload.iter()) {
                if !match_pattern_into(arg, actual_value, bindings)? { return Ok(false); }
            }
            Ok(true)
        }
        _ => Ok(false),
    }
}

fn enum_pattern_name(object: &Expr, case_name: &str) -> bool {
    match object { Expr::Name { name, .. } => !name.is_empty() && case_name != name, Expr::Member { .. } => true, _ => false }
}

fn enum_pattern_type(object: &Expr, enum_name: &str) -> bool {
    match object { Expr::Name { name, .. } => name.rsplit('.').next() == Some(enum_name.rsplit('.').next().unwrap_or(enum_name)), _ => true }
}

fn binary(left: &Value, op: &str, right: &Value) -> Result<Value, RuntimeError> {
    match op {
        "==" => Ok(Value::Bool(left == right)), "!=" => Ok(Value::Bool(left != right)), ".." => Ok(Value::String(format!("{}{}", left, right))),
        "+" | "-" | "*" | "/" | "%" => numeric(left, op, right),
        "<" | "<=" | ">" | ">=" => compare(left, op, right),
        "|" | "&" | "^" | "<<" | ">>" => integer(left, op, right), "??" => if matches!(left, Value::Null) { Ok(right.clone()) } else { Ok(left.clone()) },
        _ => Err(RuntimeError::new(format!("unknown binary operator {}", op))),
    }
}
fn numeric(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> {
    let float = matches!((a,b), (Value::Float(_), _) | (_, Value::Float(_)));
    if float {
        let (a,b) = (number(a)?,number(b)?); if matches!(op, "/" | "%") && b == 0.0 { return Err(RuntimeError::coded("E031", "division by zero")); }
        Ok(Value::Float(match op { "+"=>a+b,"-"=>a-b,"*"=>a*b,"/"=>a/b,"%"=>a%b,_=>0.0 }))
    } else {
        let (a,b) = (integer_value(a)?,integer_value(b)?); if matches!(op, "/" | "%") && b == 0 { return Err(RuntimeError::coded("E031", "division by zero")); }
        Ok(Value::Int(match op { "+"=>a+b,"-"=>a-b,"*"=>a*b,"/"=>a/b,"%"=>a%b,_=>0 }))
    }
}
fn compare(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> { let (a,b) = (number(a)?,number(b)?); Ok(Value::Bool(match op { "<"=>a<b,"<="=>a<=b,">"=>a>b,">="=>a>=b,_=>false })) }
fn integer(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> { let (a,b) = (integer_value(a)?,integer_value(b)?); Ok(Value::Int(match op { "|"=>a|b,"&"=>a&b,"^"=>a^b,"<<"=>a<<b,">>"=>a>>b,_=>0 })) }
fn integer_value(value: &Value) -> Result<i64, RuntimeError> { match value { Value::Byte(v) => Ok(*v as i64), Value::Int(v) => Ok(*v), _ => Err(RuntimeError::new("integer operand required")) } }
fn number(value: &Value) -> Result<f64, RuntimeError> { match value { Value::Byte(v) => Ok(*v as f64), Value::Int(v) => Ok(*v as f64), Value::Float(v) => Ok(*v), Value::Char(v) => Ok(*v as u32 as f64), _ => Err(RuntimeError::new("numeric operand required")) } }

// Semantic AST bytecode. The dispatcher below owns expression evaluation and
// control flow; it does not invoke the tree-walking statement evaluator.
#[derive(Clone)]
enum BcInstr {
    Push(Value), Load(String, Vec<TypeRef>), Store(String, Option<TypeRef>), Pop,
    Unary(String), Binary(String), Short(String, Rc<BcCode>),
    Call(Vec<bool>, Vec<TypeRef>, bool), Member(String, Vec<TypeRef>), Index,
    List(usize), Map(usize), Struct(String, Vec<String>), Closure(Function),
    AssignName(String), AssignMember(Rc<BcCode>, String), AssignIndex(Rc<BcCode>, Rc<BcCode>), Block(Rc<BcCode>),
    If(Rc<BcCode>, Option<Rc<BcCode>>), While(Rc<BcCode>, Rc<BcCode>),
    For(Vec<String>, Rc<BcCode>, Rc<BcCode>),
    Switch(Rc<BcCode>, Vec<(Option<Expr>, Rc<BcCode>)>),
    Try(Rc<BcCode>, Option<String>, Option<Rc<BcCode>>, Option<Rc<BcCode>>),
    Throw, Return, Break, Continue,
}

#[derive(Clone, Default)]
struct BcCode { instructions: Vec<BcInstr> }

fn bc_compile_block(block: Option<&Block>) -> Rc<BcCode> {
    let mut code = BcCode::default();
    if let Some(block) = block { for statement in &block.statements { bc_compile_stmt(&mut code, statement); } }
    Rc::new(code)
}

fn bc_compile_stmt(code: &mut BcCode, statement: &Stmt) {
    match statement {
        Stmt::Var { name, value, typ, .. } => { if let Some(value) = value { bc_compile_expr(code, value); } else { code.instructions.push(BcInstr::Push(Value::Null)); } code.instructions.push(BcInstr::Store(name.clone(), typ.clone())); }
        Stmt::Expr(expr) => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Pop); }
        Stmt::Return(expr) => { if let Some(expr) = expr { bc_compile_expr(code, expr); } else { code.instructions.push(BcInstr::Push(Value::Null)); } code.instructions.push(BcInstr::Return); }
        Stmt::Block(block) => code.instructions.push(BcInstr::Block(bc_compile_block(Some(block)))),
        Stmt::If { condition, then_block, else_branch } => {
            bc_compile_expr(code, condition);
            let else_code = else_branch.as_deref().map(|branch| { let mut child = BcCode::default(); bc_compile_stmt(&mut child, branch); Rc::new(child) });
            code.instructions.push(BcInstr::If(bc_compile_block(Some(then_block)), else_code));
        }
        Stmt::While { condition, body } => code.instructions.push(BcInstr::While(bc_compile_expr_code(condition), bc_compile_block(Some(body)))),
        Stmt::For { names, iterable, body } => code.instructions.push(BcInstr::For(names.clone(), bc_compile_expr_code(iterable), bc_compile_block(Some(body)))),
        Stmt::Switch { value, cases } => code.instructions.push(BcInstr::Switch(bc_compile_expr_code(value), cases.iter().map(|(pattern, body)| (pattern.clone(), bc_compile_block(Some(body)))).collect())),
        Stmt::Try { body, catch_name, catch, finally } => code.instructions.push(BcInstr::Try(bc_compile_block(Some(body)), catch_name.clone(), catch.as_ref().map(|body| bc_compile_block(Some(body))), finally.as_ref().map(|body| bc_compile_block(Some(body))))),
        Stmt::Throw(expr) => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Throw); }
        Stmt::Break => code.instructions.push(BcInstr::Break), Stmt::Continue => code.instructions.push(BcInstr::Continue),
    }
}

fn bc_compile_expr_code(expr: &Expr) -> Rc<BcCode> { let mut code = BcCode::default(); bc_compile_expr(&mut code, expr); Rc::new(code) }

fn bc_compile_expr(code: &mut BcCode, expr: &Expr) {
    match expr {
        Expr::Int(v) => code.instructions.push(BcInstr::Push(Value::Int(*v))), Expr::Float(v) => code.instructions.push(BcInstr::Push(Value::Float(*v))), Expr::Bool(v) => code.instructions.push(BcInstr::Push(Value::Bool(*v))), Expr::Char(v) => code.instructions.push(BcInstr::Push(Value::Char(*v))), Expr::String(v) => code.instructions.push(BcInstr::Push(Value::String(v.clone()))), Expr::Null => code.instructions.push(BcInstr::Push(Value::Null)),
        Expr::Name { name, type_args } => code.instructions.push(BcInstr::Load(name.clone(), type_args.clone())),
        Expr::Function { params, return_type, body } => code.instructions.push(BcInstr::Closure(Function { name: "<closure>".into(), public: false, mutating: false, params: params.clone(), return_type: return_type.clone(), type_params: Vec::new(), body: Some(body.clone()) })),
        Expr::Unary { op, expr } => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Unary(op.clone())); }
        Expr::Binary { left, op, right } if op == "=" => {
            bc_compile_expr(code, right);
            match left.as_ref() {
                Expr::Name { name, .. } => code.instructions.push(BcInstr::AssignName(name.clone())),
                Expr::Member { object, name, .. } => code.instructions.push(BcInstr::AssignMember(bc_compile_expr_code(object), name.clone())),
                Expr::Index { object, index } => code.instructions.push(BcInstr::AssignIndex(bc_compile_expr_code(object), bc_compile_expr_code(index))),
                _ => code.instructions.push(BcInstr::Pop),
            }
        }
        Expr::Binary { left, op, right } if matches!(op.as_str(), "&&" | "||" | "??") => { bc_compile_expr(code, left); code.instructions.push(BcInstr::Short(op.clone(), bc_compile_expr_code(right))); }
        Expr::Binary { left, op, right } => { bc_compile_expr(code, left); bc_compile_expr(code, right); code.instructions.push(BcInstr::Binary(op.clone())); }
        Expr::Call { callee, args, type_args } => { bc_compile_expr(code, callee); for arg in args { bc_compile_expr(code, &arg.expr); } let explicit = if !type_args.is_empty() { type_args.clone() } else { match callee.as_ref() { Expr::Name { type_args, .. } | Expr::Member { type_args, .. } => type_args.clone(), _ => Vec::new() } }; code.instructions.push(BcInstr::Call(args.iter().map(|arg| arg.spread || matches!(arg.expr, Expr::Spread(_))).collect(), explicit, args.iter().any(|arg| matches!(arg.expr, Expr::Null)))); }
        Expr::Member { object, name, type_args } => { bc_compile_expr(code, object); code.instructions.push(BcInstr::Member(name.clone(), type_args.clone())); }
        Expr::Index { object, index } => { bc_compile_expr(code, object); bc_compile_expr(code, index); code.instructions.push(BcInstr::Index); }
        Expr::List(items) => { for item in items { bc_compile_expr(code, item); } code.instructions.push(BcInstr::List(items.len())); }
        Expr::Map(items) => { for (key, value) in items { bc_compile_expr(code, key); bc_compile_expr(code, value); } code.instructions.push(BcInstr::Map(items.len())); }
        Expr::Struct { name, fields, .. } => { for (_, value) in fields { bc_compile_expr(code, value); } code.instructions.push(BcInstr::Struct(name.clone(), fields.iter().map(|(name, _)| name.clone()).collect())); }
        Expr::Spread(inner) => bc_compile_expr(code, inner),
    }
}

fn install_bytecode_declarations(env: &EnvRef, program: &crate::semantic_ast::Program) {
    for declaration in &program.declarations {
        if let Decl::Function(function) = declaration {
            Env::define(env, function.name.clone(), Value::Function(Rc::new(Callable { function: function.clone(), closure: env.clone(), receiver: None, bytecode: Some(bc_compile_block(function.body.as_ref())) })));
        }
    }
}

fn execute_main_bytecode(global: &EnvRef) -> Result<i32, RuntimeError> {
    let main = Env::get(global, "main").ok_or_else(|| RuntimeError::new("entry function main not found"))?;
    match call(main, Vec::new())? { Value::Int(code) => Ok(code as i32), _ => Ok(0) }
}

fn bc_eval(code: &BcCode, env: &EnvRef) -> Result<Value, RuntimeError> {
    let mut stack = Vec::new();
    match bc_run(code, env, &mut stack)? { Flow::Normal => Ok(stack.pop().unwrap_or(Value::Null)), Flow::Throw(error) => Err(error), _ => Err(RuntimeError::new("invalid control flow in expression")) }
}

fn bc_run(code: &BcCode, env: &EnvRef, stack: &mut Vec<Value>) -> Result<Flow, RuntimeError> {
    for instruction in &code.instructions {
        match instruction {
            BcInstr::Push(value) => stack.push(value.clone()),
            BcInstr::Load(name, type_args) => {
                let mut value = lookup_path(env, name).ok_or_else(|| RuntimeError::new(format!("undefined name {}", name)))?;
                if !type_args.is_empty() {
                    if let Value::EnumType(definition) = value {
                        if definition.type_param_count != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", definition.name, definition.type_param_count, type_args.len()))); }
                        let mut definition = (*definition).clone(); definition.type_param_count = 0; value = Value::EnumType(Rc::new(definition));
                    }
                }
                stack.push(value);
            }
            BcInstr::Store(name, typ) => {
                let mut value = stack.pop().unwrap_or(Value::Null);
                if typ.as_ref().is_some_and(|typ| typ.name == "exception") {
                    if let Value::String(message) = value { value = Value::Exception(RuntimeError::new(message)); }
                }
                if let Value::Enum(enum_value) = &value {
                    if enum_value.unresolved {
                        let resolves = typ.as_ref().is_some_and(|typ| typ.name.rsplit('.').next() == Some(enum_value.definition.name.rsplit('.').next().unwrap_or(&enum_value.definition.name)) && typ.args.len() == enum_value.definition.type_param_count);
                        if resolves { let mut resolved = (**enum_value).clone(); resolved.unresolved = false; value = Value::Enum(Rc::new(resolved)); }
                        else if typ.as_ref().is_some_and(|typ| typ.name == "any") { return Err(RuntimeError::coded("E067", "cannot infer type parameter for enum; use explicit type arguments or annotate the value's type")); }
                    }
                }
                if let Value::Struct(struct_value) = &value {
                    if struct_value.borrow().unresolved {
                        let resolves = typ.as_ref().is_some_and(|typ| typ.name.rsplit('.').next() == Some(struct_value.borrow().definition.name.rsplit('.').next().unwrap_or(&struct_value.borrow().definition.name)) && typ.args.len() == struct_value.borrow().definition.type_param_count);
                        if resolves { let mut resolved = struct_value.borrow_mut(); resolved.unresolved = false; }
                        else if typ.as_ref().is_some_and(|typ| typ.name == "any") { return Err(RuntimeError::coded("E067", "cannot infer type parameter for struct; annotate the declaration or use explicit type arguments")); }
                    }
                }
                Env::define(env, name.clone(), value);
            }
            BcInstr::Pop => { stack.pop(); }
            BcInstr::Unary(op) => { let value = stack.pop().unwrap_or(Value::Null); stack.push(match (op.as_str(), value) { ("!", value) => Value::Bool(!truthy(&value)), ("-", Value::Int(v)) => Value::Int(-v), ("-", Value::Float(v)) => Value::Float(-v), ("~", Value::Int(v)) => Value::Int(!v), _ => return Err(RuntimeError::new("invalid unary operation")) }); }
            BcInstr::Binary(op) => { let right = stack.pop().unwrap_or(Value::Null); let left = stack.pop().unwrap_or(Value::Null); stack.push(binary(&left, op, &right)?); }
            BcInstr::Short(op, right) => {
                let left = stack.pop().unwrap_or(Value::Null);
                let value = match op.as_str() { "&&" => if truthy(&left) { Value::Bool(truthy(&bc_eval(right, env)?)) } else { Value::Bool(false) }, "||" => if truthy(&left) { Value::Bool(true) } else { Value::Bool(truthy(&bc_eval(right, env)?)) }, "??" => if matches!(left, Value::Null) { bc_eval(right, env)? } else { left }, _ => unreachable!() };
                stack.push(value);
            }
            BcInstr::Call(spreads, type_args, literal_null) => {
                let mut raw = Vec::with_capacity(spreads.len()); for _ in 0..spreads.len() { raw.push(stack.pop().unwrap_or(Value::Null)); } raw.reverse();
                let mut args = Vec::new(); for (value, spread) in raw.into_iter().zip(spreads) { if *spread { let Value::List(items) = value else { return Err(RuntimeError::new("spread value is not a list")); }; args.extend(items.borrow().iter().cloned()); } else { args.push(value); } }
                let callee = stack.pop().unwrap_or(Value::Null);
                if let Value::Function(function) = &callee {
                    if !type_args.is_empty() && type_args.len() != function.function.type_params.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", function.function.name, function.function.type_params.len(), type_args.len()))); }
                    if *literal_null && function.function.type_params.len() > type_args.len() { return Err(RuntimeError::coded("E067", "a null value cannot infer a generic type parameter")); }
                }
                stack.push(call(callee, args)?);
            }
            BcInstr::Member(name, type_args) => {
                let object = stack.pop().unwrap_or(Value::Null);
                let value = if !type_args.is_empty() {
                    if let Value::EnumType(definition) = object.clone() {
                        if definition.type_param_count != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", definition.name, definition.type_param_count, type_args.len()))); }
                        let mut definition = (*definition).clone(); definition.type_param_count = 0;
                        member(Value::EnumType(Rc::new(definition)), name, env)?
                    } else {
                        let value = member(object, name, env)?;
                        if let Value::Function(function) = &value {
                            if function.function.type_params.len() != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", function.function.name, function.function.type_params.len(), type_args.len()))); }
                        }
                        value
                    }
                } else { member(object, name, env)? };
                stack.push(value);
            }
            BcInstr::Index => { let index = stack.pop().unwrap_or(Value::Null); let object = stack.pop().unwrap_or(Value::Null); stack.push(bc_index(object, index)?); }
            BcInstr::List(count) => { let mut values = Vec::with_capacity(*count); for _ in 0..*count { values.push(stack.pop().unwrap_or(Value::Null)); } values.reverse(); stack.push(Value::List(Rc::new(RefCell::new(values)))); }
            BcInstr::Map(count) => { let mut values = Vec::with_capacity(*count); for _ in 0..*count { let value = stack.pop().unwrap_or(Value::Null); let key = stack.pop().unwrap_or(Value::Null); values.push((key, value)); } values.reverse(); stack.push(Value::Map(Rc::new(RefCell::new(values)))); }
            BcInstr::Struct(name, fields) => { let mut values = HashMap::new(); for field in fields.iter().rev() { values.insert(field.clone(), stack.pop().unwrap_or(Value::Null)); } let definition = match lookup_path(env, name) { Some(Value::StructType(definition)) => definition, _ => return Err(RuntimeError::new(format!("unknown struct {}", name))) }; let unresolved = definition.type_param_count > 0 && values.values().any(|value| matches!(value, Value::Null)); stack.push(Value::Struct(Rc::new(RefCell::new(StructValue { definition, fields: values, unresolved })))); }
            BcInstr::Closure(function) => stack.push(Value::Function(Rc::new(Callable { function: function.clone(), closure: env.clone(), receiver: None, bytecode: Some(bc_compile_block(function.body.as_ref())) }))),
            BcInstr::Block(block) => match bc_run(block, &Env::new(Some(env.clone())), stack)? { Flow::Normal => {}, flow => return Ok(flow) },
            BcInstr::AssignName(name) => { let value = stack.pop().unwrap_or(Value::Null); if !Env::set(env, name, value.clone()) { return Err(RuntimeError::new(format!("undefined name {}", name))); } stack.push(value); }
            BcInstr::AssignMember(object_code, name) => { let value = stack.pop().unwrap_or(Value::Null); let object = bc_eval(object_code, env)?; match object { Value::Struct(target) => { target.borrow_mut().fields.insert(name.clone(), value.clone()); let _ = Env::set(env, name, value.clone()); stack.push(value); }, _ => return Err(RuntimeError::new("assignment target is not a struct field")) } }
            BcInstr::AssignIndex(object_code, index_code) => { let value = stack.pop().unwrap_or(Value::Null); let object = bc_eval(object_code, env)?; let index = bc_eval(index_code, env)?; match object { Value::List(items) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::new("index out of range")); } let mut items = items.borrow_mut(); let slot = items.get_mut(index as usize).ok_or_else(|| RuntimeError::new("index out of range"))?; *slot = value.clone(); stack.push(value); }, Value::Map(items) => { let mut items = items.borrow_mut(); if let Some((_, existing)) = items.iter_mut().find(|(key, _)| *key == index) { *existing = value.clone(); } else { items.push((index, value.clone())); } stack.push(value); }, _ => return Err(RuntimeError::new("assignment target is not indexable")) } }
            BcInstr::If(then_code, else_code) => { let condition = stack.pop().unwrap_or(Value::Null); let flow = if truthy(&condition) { bc_run(then_code, &Env::new(Some(env.clone())), stack)? } else if let Some(else_code) = else_code { bc_run(else_code, &Env::new(Some(env.clone())), stack)? } else { Flow::Normal }; if !matches!(flow, Flow::Normal) { return Ok(flow); } }
            BcInstr::While(condition, body) => loop { if !truthy(&bc_eval(condition, env)?) { break; } match bc_run(body, &Env::new(Some(env.clone())), stack)? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) } },
            BcInstr::For(names, iterable, body) => { let entries = bc_iterable(bc_eval(iterable, env)?)?; for entry in entries { if entry.len() < names.len() { return Err(RuntimeError::new("loop binding count mismatch")); } let child = Env::new(Some(env.clone())); for (name, value) in names.iter().zip(entry) { Env::define(&child, name.clone(), value); } match bc_run(body, &child, stack)? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) } } }
            BcInstr::Switch(value_code, cases) => { let value = bc_eval(value_code, env)?; for (pattern, body) in cases { let bindings = match pattern { Some(pattern) => match_pattern(pattern, &value)?, None => Some(HashMap::new()) }; if let Some(bindings) = bindings { let child = Env::new(Some(env.clone())); for (name, bound) in bindings { Env::define(&child, name, bound); } match bc_run(body, &child, stack)? { Flow::Normal => {}, flow => return Ok(flow) } break; } } }
            BcInstr::Try(body, catch_name, catch, finally) => {
                let mut outcome = bc_run(body, &Env::new(Some(env.clone())), stack);
                if let Err(error) = outcome {
                    if let Some(catch) = catch { let child = Env::new(Some(env.clone())); if let Some(name) = catch_name { Env::define(&child, name.clone(), Value::Exception(error)); } outcome = bc_run(catch, &child, stack); }
                    else { outcome = Err(error); }
                }
                if let Some(finally) = finally { let final_outcome = bc_run(finally, &Env::new(Some(env.clone())), stack); if final_outcome.is_err() || matches!(final_outcome, Ok(Flow::Return(_) | Flow::Throw(_))) { outcome = final_outcome; } }
                match outcome? { Flow::Normal => {}, flow => return Ok(flow) }
            }
            BcInstr::Throw => { let value = stack.pop().unwrap_or(Value::Null); return Err(RuntimeError::coded("E000", value.to_string())); }
            BcInstr::Return => return Ok(Flow::Return(stack.pop().unwrap_or(Value::Null))),
            BcInstr::Break => return Ok(Flow::Break), BcInstr::Continue => return Ok(Flow::Continue),
        }
    }
    Ok(Flow::Normal)
}

fn bc_index(object: Value, index: Value) -> Result<Value, RuntimeError> {
    match object {
        Value::List(items) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::new("index out of range")); } items.borrow().get(index as usize).cloned().ok_or_else(|| RuntimeError::new("index out of range")) }
        Value::String(text) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::new("index out of range")); } text.chars().nth(index as usize).map(Value::Char).ok_or_else(|| RuntimeError::new("index out of range")) }
        Value::Map(items) => items.borrow().iter().find(|(key, _)| *key == index).map(|(_, value)| value.clone()).ok_or_else(|| RuntimeError::new("key not found")),
        _ => Err(RuntimeError::new("value is not indexable")),
    }
}

fn bc_iterable(source: Value) -> Result<Vec<Vec<Value>>, RuntimeError> {
    match source {
        Value::List(values) => Ok(values.borrow().iter().cloned().map(|value| vec![value]).collect()),
        Value::Map(values) => Ok(values.borrow().iter().cloned().map(|(key, value)| vec![key, value]).collect()),
        Value::Stack(values) => Ok(values.borrow().iter().rev().cloned().map(|value| vec![value]).collect()),
        Value::String(value) => Ok(value.chars().map(|value| vec![Value::Char(value)]).collect()),
        Value::Struct(value) => { let iterator = member(Value::Struct(value), "iterator", &Env::package(None, String::new()))?; match call(iterator, Vec::new())? { Value::List(values) => Ok(values.borrow().iter().cloned().map(|value| vec![value]).collect()), _ => Err(RuntimeError::new("iterator must return a list")) } }
        _ => Err(RuntimeError::new("value is not iterable")),
    }
}

fn bc_call(function: Rc<Callable>, code: Rc<BcCode>, args: Vec<Value>) -> Result<Value, RuntimeError> {
    let params = &function.function.params;
    let fixed = params.iter().filter(|param| !param.variadic).count();
    let variadic = params.last().is_some_and(|param| param.variadic);
    if (!variadic && args.len() != params.len()) || (variadic && args.len() < fixed) { return Err(RuntimeError::coded("E068", format!("{} argument count mismatch", function.function.name))); }
    let mut generic_bindings = HashMap::new();
    for (param, argument) in params.iter().zip(args.iter()) {
        if param.typ.args.is_empty() && param.typ.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) { generic_bindings.insert(param.typ.name.clone(), argument.clone()); }
    }
    for type_param in &function.function.type_params {
        if let Some(argument) = generic_bindings.get(&type_param.name) {
            for constraint in &type_param.constraints {
                let method_name = match constraint.name.rsplit('.').next().unwrap_or(&constraint.name) { "Stringable" => "string", "Equatable" => "equals", "Countable" => "len", "Iterable" | "Collection" => "iterator", _ => continue };
                if member(argument.clone(), method_name, &function.closure).is_err() { return Err(RuntimeError::coded("E067", format!("type {} does not satisfy generic constraint {}", type_name(argument), constraint.name))); }
            }
        }
    }
    let local = Env::new(Some(function.closure.clone()));
    if let Some(receiver) = &function.receiver {
        Env::define(&local, "self".into(), receiver.clone());
        if let Value::Struct(value) = receiver {
            for (name, field) in value.borrow().fields.clone() { Env::define(&local, name, field); }
            for (name, method) in value.borrow().definition.methods.clone() {
                Env::define(&local, name, Value::Function(Rc::new(Callable { function: method.clone(), closure: local.clone(), receiver: Some(receiver.clone()), bytecode: Some(bc_compile_block(method.body.as_ref())) })));
            }
        }
    }
    let mut index = 0;
    for param in params { if param.variadic { Env::define(&local, param.name.clone(), Value::List(Rc::new(RefCell::new(args[index..].to_vec())))); index = args.len(); } else { Env::define(&local, param.name.clone(), args[index].clone()); index += 1; } }
    let mut stack = Vec::new();
    let flow = bc_run(&code, &local, &mut stack)?;
    if let Some(Value::Struct(value)) = &function.receiver { let fields = value.borrow().fields.keys().cloned().collect::<Vec<_>>(); let mut target = value.borrow_mut(); for field in fields { if let Some(value) = Env::get(&local, &field) { target.fields.insert(field, value); } } }
    match flow { Flow::Return(value) => Ok(value), Flow::Normal => Ok(Value::Null), Flow::Throw(error) => Err(error), Flow::Break | Flow::Continue => Err(RuntimeError::new("invalid control flow")) }
}

#[cfg(test)]
mod tests {
    use super::run;
    use crate::semantic_parser::parse;

    #[test]
    fn executes_native_functions_and_control_flow() {
        let source = "package demo\nfunc twice(x: int) -> int {\n return x * 2\n}\nfunc main() -> int {\n mut total: int = 0\n for x in [1, 2, 3] {\n  total = total + twice(x)\n }\n return total\n}\n";
        let program = parse(source).unwrap();
        assert_eq!(run(&program).unwrap(), 12);
    }

    #[test]
    fn executes_native_closure_capture() {
        let program = parse("package demo\nfunc main() -> int {\n mut n: int = 4\n f: func<int> = func() -> int { return n }\n n = 7\n return f()\n}\n").unwrap();
        assert_eq!(run(&program).unwrap(), 7);
    }

    #[test]
    fn executes_native_struct_literal_and_bound_method() {
        let program = parse("package demo\nstruct Box { value: int\n func get() -> int { return self.value }\n}\nfunc main() -> int {\n b: Box = Box { value: 41 }\n return b.get() + 1\n}\n").unwrap();
        assert_eq!(run(&program).unwrap(), 42);
    }
}
