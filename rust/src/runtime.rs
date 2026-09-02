//! Compilation pipeline orchestration and multi-file support.
//!
//! Port of internal/runtime/runtime.go (URL fetching is not supported in the
//! Rust build; `use url:` declarations produce an error).

use crate::ast::{self, Program};
use crate::checker::{Checker, ExternalFunc};
use crate::compiler::Compiler;
use crate::diagnostic::Diagnostics;
use crate::lexer::Lexer;
use crate::parser::Parser;
use crate::resolver::{resolve_type_annotation, Resolver};
use crate::source::Source;
use crate::types::{self, Kind, StructFieldInfo, StructMethodInfo, TraitMethodInfo, Type};
use crate::verifier;
use crate::vm::{Limits, NativeRegistry, Value, Vm};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::time::Instant;

pub struct Result_ {
    pub program: Option<Rc<crate::bytecode::Program>>,
    pub diags: Diagnostics,
    pub value: Value,
    pub error: Option<String>,
}

pub fn default_options_limits() -> Limits {
    Limits::default()
}

thread_local! {
    static GLOBAL_REGISTRY: Rc<NativeRegistry> = Rc::new({
        let mut r = NativeRegistry::new();
        crate::native::register_all(&mut r);
        r
    });
}

/// Resolves a `use` declaration path to a local filesystem path.
pub fn resolve_use_path(src_file: &str, use_path: &str, _checksum: &str, insecure: bool) -> Result<String, String> {
    if use_path.starts_with("https://") || (insecure && use_path.starts_with("http://")) {
        return Err(format!(
            "URL use declarations are not supported in the Rust build: {}",
            use_path
        ));
    }
    if use_path.starts_with("http://") {
        return Err(format!("http URLs require insecure flag: {}", use_path));
    }

    let module_path = use_path.replace('.', "/");

    let resolved: PathBuf = if module_path == "~" || module_path.starts_with("~/") {
        let home = std::env::var("HOME")
            .map_err(|_| "cannot expand ~: $HOME is not defined".to_string())?;
        if module_path == "~" {
            PathBuf::from(home)
        } else {
            Path::new(&home).join(&module_path[2..])
        }
    } else if Path::new(&module_path).is_absolute() {
        PathBuf::from(&module_path)
    } else {
        Path::new(src_file)
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join(&module_path)
    };

    let mut s = resolved.to_string_lossy().to_string();
    if !s.ends_with(".sol") {
        s.push_str(".sol");
    }
    let s = clean_path(&s);
    Ok(s)
}

/// Lexical path cleaning (no filesystem access), mirroring filepath.Clean.
fn clean_path(p: &str) -> String {
    let rooted = p.starts_with('/');
    let mut out: Vec<String> = Vec::new();
    for part in p.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                if let Some(last) = out.last() {
                    if last != ".." {
                        out.pop();
                        continue;
                    }
                }
                if !rooted {
                    out.push("..".to_string());
                }
            }
            _ => out.push(part.to_string()),
        }
    }
    let mut s = out.join("/");
    if rooted {
        s = format!("/{}", s);
    }
    if s.is_empty() {
        return if rooted { "/".to_string() } else { ".".to_string() };
    }
    s
}

/// Compiles a source file and all its `use` dependencies.
pub fn compile_with_uses(entry_file: &str) -> (Option<Rc<crate::bytecode::Program>>, Diagnostics, Option<String>) {
    let (prog, diags, _sources, err) = compile_with_sources(entry_file);
    (prog, diags, err)
}

/// Compiles like `compile_with_uses` and additionally returns a per-file
/// source map covering every file in the dependency graph. The map is keyed
/// by the same file paths carried in diagnostic spans, so callers can format
/// each diagnostic against the correct file's source even when the error is
/// located in a use dependency.
pub fn compile_with_sources(
    entry_file: &str,
) -> (
    Option<Rc<crate::bytecode::Program>>,
    Diagnostics,
    HashMap<String, Source>,
    Option<String>,
) {
    let mut seen: HashMap<String, bool> = HashMap::new();
    let mut files: HashMap<String, String> = HashMap::new();
    let mut sources: HashMap<String, Source> = HashMap::new();
    let mut all_diags = Diagnostics::new();

    fn load(
        path: &str,
        seen: &mut HashMap<String, bool>,
        files: &mut HashMap<String, String>,
        sources: &mut HashMap<String, Source>,
        all_diags: &mut Diagnostics,
    ) -> Result<(), String> {
        if seen.get(path).copied().unwrap_or(false) {
            return Ok(());
        }
        seen.insert(path.to_string(), true);

        let data = std::fs::read_to_string(path).map_err(|e| {
            format!(
                "cannot read {}: open {}: {}",
                path,
                path,
                crate::gocompat::go_os_error_message(&e)
            )
        })?;
        files.insert(path.to_string(), data);

        // Register the source before lexing so diagnostics for this file can
        // be rendered even when lexing or parsing fails here.
        sources.insert(
            path.to_string(),
            Source::new(path, files.get(path).unwrap()),
        );
        {
            let src = &sources[path];
            let (tokens, lex_diags) = Lexer::new(src).tokenize();
            if lex_diags.has_errors() {
                all_diags.extend(&lex_diags);
                return Err(format!("lex error in {}", path));
            }

            let (_, parse_diags) = Parser::new(src, tokens).parse();
            if parse_diags.has_errors() {
                all_diags.extend(&parse_diags);
                return Err(format!("parse error in {}", path));
            }
        }

        // Note: uses are re-parsed from the raw text here to avoid holding
        // the AST across the recursive load (matches the Go structure).
        let content = files.get(path).unwrap().clone();
        for use_decl in parse_use_decls(&content) {
            let dep_path = resolve_use_path(path, &use_decl.0, "", use_decl.1)?;
            load(&dep_path, seen, files, sources, all_diags)?;
        }
        Ok(())
    }

    if let Err(e) = load(entry_file, &mut seen, &mut files, &mut sources, &mut all_diags) {
        return (None, all_diags, sources, Some(e));
    }

    let (prog, diags, err) = compile_files(files, entry_file);
    (prog, diags, sources, err)
}

/// Extracts `use file:<path>` declarations from raw source (compile-time scan
/// used while loading the dependency graph).
fn parse_use_decls(content: &str) -> Vec<(String, bool)> {
    let mut result = Vec::new();
    for line in content.lines() {
        let t = line.trim_start();
        if let Some(rest) = t.strip_prefix("use ") {
            let rest = rest.trim_start();
            if let Some(value) = rest.strip_prefix("file:") {
                let mut path = String::new();
                let mut chars = value.chars().peekable();
                if let Some(&'"') = chars.peek() {
                    chars.next();
                    for c in chars.by_ref() {
                        if c == '"' {
                            break;
                        }
                        path.push(c);
                    }
                } else {
                    for c in chars.by_ref() {
                        if c.is_whitespace() {
                            break;
                        }
                        path.push(c);
                    }
                }
                if !path.is_empty() {
                    result.push((path, false));
                }
            }
        }
    }
    result
}

/// Compiles a single source text.
pub fn compile(name: &str, source_text: &str) -> (Option<Rc<crate::bytecode::Program>>, Diagnostics, Option<String>) {
    let mut files = HashMap::new();
    files.insert(name.to_string(), source_text.to_string());
    compile_files(files, name)
}

struct FileResult {
    src: Source,
    prog: Program,
}

/// Type declarations of a package across files.
struct ModuleTypes {
    enums: Vec<(String, usize)>, // (name, key)
    structs: Vec<(String, usize, usize)>,
    traits: Vec<(String, usize, usize)>,
}

/// Compiles multiple source files into a single bytecode program.
pub fn compile_files(
    files: HashMap<String, String>,
    entry_file: &str,
) -> (Option<Rc<crate::bytecode::Program>>, Diagnostics, Option<String>) {
    let mut all_diags = Diagnostics::new();

    // Phase 1: Lex and parse all files (sorted for determinism)
    let mut file_names: Vec<String> = files.keys().cloned().collect();
    file_names.sort();

    let mut file_results: Vec<FileResult> = Vec::new();
    for name in &file_names {
        let source_text = &files[name];
        let src = Source::new(name, source_text);

        let (tokens, lex_diags) = Lexer::new(&src).tokenize();
        if lex_diags.has_errors() {
            all_diags.extend(&lex_diags);
            continue;
        }

        let (prog, parse_diags) = Parser::new(&src, tokens).parse();
        if parse_diags.has_errors() {
            all_diags.extend(&parse_diags);
            continue;
        }

        file_results.push(FileResult { src, prog });
    }

    if all_diags.has_errors() {
        return (None, all_diags, Some("parsing failed".to_string()));
    }

    // Phase 2: combined function map across modules
    let mut all_func_names: HashMap<String, String> = HashMap::new(); // mangled -> module
    for fr in &file_results {
        let module = if fr.prog.module.is_empty() {
            "main".to_string()
        } else {
            fr.prog.module.clone()
        };
        for f in &fr.prog.funcs {
            all_func_names.insert(format!("{}.{}", module, f.name), module.clone());
        }
        for sd in &fr.prog.structs {
            for m in &sd.methods {
                all_func_names.insert(format!("{}.{}", module, m.name), module.clone());
            }
        }
    }

    // Same-package type registries
    let mut module_types: HashMap<String, ModuleTypes> = HashMap::new();
    for (i, fr) in file_results.iter().enumerate() {
        let mt = module_types.entry(fr.prog.module.clone()).or_insert(ModuleTypes {
            enums: Vec::new(),
            structs: Vec::new(),
            traits: Vec::new(),
        });
        for (ei, en) in fr.prog.enums.iter().enumerate() {
            mt.enums.push((en.name.clone(), i * 1000 + ei));
        }
        for (si, sd) in fr.prog.structs.iter().enumerate() {
            mt.structs.push((sd.name.clone(), i, si));
        }
        for (ti, td) in fr.prog.traits.iter().enumerate() {
            mt.traits.push((td.name.clone(), i, ti));
        }
    }

    // Resolve all files
    let name_set: std::collections::HashSet<String> = all_func_names.keys().cloned().collect();
    for i in 0..file_results.len() {
        let external_names = external_type_names(&file_results, i, &module_types);
        let mut res = Resolver::new();
        res.set_all_funcs(name_set.clone());
        res.set_external_type_names(&external_names);
        let diags = res.resolve(&mut file_results[i].prog);
        if diags.has_errors() {
            all_diags.extend(&diags);
        }
    }
    if all_diags.has_errors() {
        return (None, all_diags, Some("compilation failed".to_string()));
    }

    // Build cross-module signature snapshots (after resolution)
    let mut all_func_sigs: HashMap<String, ExternalFunc> = HashMap::new();
    for fr in &file_results {
        let module = if fr.prog.module.is_empty() {
            "main".to_string()
        } else {
            fr.prog.module.clone()
        };
        let mut add_fn = |f: &ast::Function| {
            let mut params = Vec::new();
            let mut variadic = false;
            for p in &f.parameters {
                params.push(p.ty.resolved());
                if p.variadic {
                    variadic = true;
                }
            }
            let ret = if f.return_types.len() == 1 {
                f.return_types[0].resolved()
            } else {
                types::t_void()
            };
            all_func_sigs.insert(
                format!("{}.{}", module, f.name),
                ExternalFunc { params, variadic, ret },
            );
        };
        for f in &fr.prog.funcs {
            add_fn(f);
        }
        for sd in &fr.prog.structs {
            for m in &sd.methods {
                add_fn(m);
            }
        }
    }

    // Pre-register same-package types (shared instances across files)
    let mut package_type_map: HashMap<String, HashMap<String, Rc<Type>>> = HashMap::new();
    for (module, mt) in &module_types {
        let mut registry: HashMap<String, Rc<Type>> = HashMap::new();
        // Enums
        let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
        for (name, _key) in &mt.enums {
            if seen.contains(name) {
                continue;
            }
            seen.insert(name.clone());
            // find decl
            if let Some((fi, ei)) = find_enum(&file_results, module, name) {
                let en = &file_results[fi].prog.enums[ei];
                let mut next_val: i64 = 0;
                let mut values: HashMap<String, i64> = HashMap::new();
                for v in &en.variants {
                    let val = match v.value {
                        Some(x) => {
                            next_val = x + 1;
                            x
                        }
                        None => {
                            let x = next_val;
                            next_val += 1;
                            x
                        }
                    };
                    values.insert(v.name.clone(), val);
                }
                registry.insert(name.clone(), types::enum_type(name, values));
            }
        }
        // Traits
        let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
        for (name, _, _) in &mt.traits {
            if seen.contains(name) {
                continue;
            }
            seen.insert(name.clone());
            if let Some((fi, ti)) = find_trait(&file_results, module, name) {
                // Resolve trait method annotations with a scope-independent pass
                let td = &file_results[fi].prog.traits[ti];
                let mut methods = HashMap::new();
                for m in &td.methods {
                    let mut param_types = Vec::new();
                    for p in &m.parameters {
                        // Builtin types resolve via the standard resolver;
                        // same-package named types resolve via the registry.
                        let ty = resolve_annotation_with(p.ty.clone(), &registry);
                        param_types.push(ty);
                    }
                    let ret_type = if m.return_types.len() == 1 {
                        resolve_annotation_with(m.return_types[0].clone(), &registry)
                    } else {
                        types::t_void()
                    };
                    methods.insert(
                        m.name.clone(),
                        Rc::new(TraitMethodInfo {
                            signature: types::function_type(param_types, Some(ret_type)),
                            is_pub: true,
                            is_mut: m.is_mut,
                        }),
                    );
                }
                registry.insert(name.clone(), types::trait_type(name, methods));
            }
        }
        // Structs
        let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
        for (name, _, _) in &mt.structs {
            if seen.contains(name) {
                continue;
            }
            seen.insert(name.clone());
            if let Some((fi, si)) = find_struct(&file_results, module, name) {
                let sd = &file_results[fi].prog.structs[si];
                let mut fields = Vec::new();
                for f in &sd.fields {
                    let ft = resolve_annotation_with(f.ty.clone(), &registry);
                    fields.push(StructFieldInfo {
                        name: f.name.clone(),
                        ty: ft,
                        is_mut: f.is_mut,
                        is_pub: f.is_pub,
                    });
                }
                let struct_ty = types::struct_type(name, fields);
                // Register method signatures (FuncIndex resolved by the owning
                // file's checker later)
                for m in &sd.methods {
                    let mut param_types = Vec::new();
                    for p in &m.parameters {
                        param_types.push(resolve_annotation_with(p.ty.clone(), &registry));
                    }
                    let ret_type = if m.return_types.len() == 1 {
                        resolve_annotation_with(m.return_types[0].clone(), &registry)
                    } else {
                        types::t_void()
                    };
                    let short = &m.name[name.len() + 1..];
                    struct_ty.struct_methods.borrow_mut().insert(
                        short.to_string(),
                        Rc::new(StructMethodInfo {
                            func_index: -1,
                            signature: types::function_type(param_types, Some(ret_type)),
                            is_pub: m.is_pub,
                            is_mut: m.is_mut,
                        }),
                    );
                }
                registry.insert(name.clone(), struct_ty);
            }
        }
        package_type_map.insert(module.clone(), registry);
    }

    // Check all files
    for i in 0..file_results.len() {
        // Library files must not define main
        if !entry_file.is_empty() && file_results[i].src.name != entry_file {
            let funcs = std::mem::take(&mut file_results[i].prog.funcs);
            for f in &funcs {
                if f.name == "main" {
                    all_diags.add_error(
                        "C093",
                        &format!(
                            "library file {} cannot define main; only the entry file may define the program entry point",
                            file_results[i].src.name
                        ),
                        f.span.clone(),
                    );
                }
            }
            file_results[i].prog.funcs = funcs;
        }

        let mut chk = Checker::new();
        chk.set_all_funcs(all_func_sigs.clone());
        let package_types = package_type_map
            .get(&file_results[i].prog.module)
            .cloned()
            .unwrap_or_default();
        chk.set_package_types(package_types);
        chk.set_skip_main_check(entry_file.is_empty() || file_results[i].src.name != entry_file);
        let diags = chk.check(&mut file_results[i].prog);
        if diags.has_errors() {
            all_diags.extend(&diags);
        }
    }

    if all_diags.has_errors() {
        return (None, all_diags, Some("compilation failed".to_string()));
    }

    // Phase 3: combine all files into one program
    let mut combined = Program::default();
    for fr in &file_results {
        if combined.module.is_empty() {
            combined.module = fr.prog.module.clone();
        }
    }
    for fr in file_results.into_iter() {
        for en in fr.prog.enums {
            combined.enums.push(en);
        }
        for td in fr.prog.traits {
            combined.traits.push(td);
        }
        for sd in fr.prog.structs {
            combined.structs.push(sd);
        }
        let module = fr.prog.module;
        for mut f in fr.prog.funcs {
            f.module = module.clone();
            combined.funcs.push(f);
        }
    }

    let mut comp = Compiler::new();
    let (bc_prog, comp_diags) = comp.compile(&mut combined);
    if comp_diags.has_errors() {
        all_diags.extend(&comp_diags);
        return (None, all_diags, Some("compilation failed".to_string()));
    }

    let bc_prog = match bc_prog {
        Some(p) => p,
        None => {
            return (None, all_diags, Some("compilation failed".to_string()));
        }
    };

    if let Err(e) = verifier::verify(&bc_prog) {
        return (None, all_diags, Some(format!("verification failed: {}", e)));
    }

    (Some(Rc::new(bc_prog)), all_diags, None)
}

/// Resolves a copy of a type annotation against a registry of shared
/// same-package types (plus the standard builtin resolution).
fn resolve_annotation_with(mut ta: ast::TypeAnnotation, registry: &HashMap<String, Rc<Type>>) -> Rc<Type> {
    match ta.kind {
        Kind::Invalid if !ta.type_name.is_empty() => {
            if let Some(t) = registry.get(&ta.type_name) {
                if ta.nullable {
                    return types::nullable_of(t);
                }
                return t.clone();
            }
            types::t_invalid()
        }
        Kind::List => {
            let elem = ta.element.take().map(|e| resolve_annotation_with(*e, registry));
            match elem {
                Some(t) => {
                    let mut lt = types::list_of(t);
                    if ta.nullable {
                        lt = types::nullable_of(&lt);
                    }
                    lt
                }
                None => types::t_invalid(),
            }
        }
        Kind::Stack => {
            let elem = ta.element.take().map(|e| resolve_annotation_with(*e, registry));
            match elem {
                Some(t) => {
                    let mut st = types::stack_of(t);
                    if ta.nullable {
                        st = types::nullable_of(&st);
                    }
                    st
                }
                None => types::t_invalid(),
            }
        }
        Kind::Map => {
            let key = ta.key_type.take().map(|k| resolve_annotation_with(*k, registry));
            let value = ta.value_type.take().map(|v| resolve_annotation_with(*v, registry));
            match (key, value) {
                (Some(k), Some(v)) => {
                    let mut mt = types::map_of(k, v);
                    if ta.nullable {
                        mt = types::nullable_of(&mt);
                    }
                    mt
                }
                _ => types::t_invalid(),
            }
        }
        _ => {
            resolve_type_annotation(&mut ta);
            ta.resolved()
        }
    }
}

fn external_type_names(
    file_results: &[FileResult],
    index: usize,
    reg: &HashMap<String, ModuleTypes>,
) -> Vec<String> {
    let module = &file_results[index].prog.module;
    let mt = match reg.get(module) {
        Some(m) => m,
        None => return Vec::new(),
    };
    let mut names = Vec::new();
    let own_enums: std::collections::HashSet<String> =
        file_results[index].prog.enums.iter().map(|e| e.name.clone()).collect();
    let own_structs: std::collections::HashSet<String> = file_results[index]
        .prog
        .structs
        .iter()
        .map(|s| s.name.clone())
        .collect();
    let own_traits: std::collections::HashSet<String> = file_results[index]
        .prog
        .traits
        .iter()
        .map(|t| t.name.clone())
        .collect();

    let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
    for (name, _) in &mt.enums {
        if !own_enums.contains(name) && seen.insert(name.clone()) {
            names.push(name.clone());
        }
    }
    for (name, _, _) in &mt.structs {
        if !own_structs.contains(name) && seen.insert(name.clone()) {
            names.push(name.clone());
        }
    }
    for (name, _, _) in &mt.traits {
        if !own_traits.contains(name) && seen.insert(name.clone()) {
            names.push(name.clone());
        }
    }
    names
}

fn find_enum(file_results: &[FileResult], module: &str, name: &str) -> Option<(usize, usize)> {
    for (i, fr) in file_results.iter().enumerate() {
        if fr.prog.module != module {
            continue;
        }
        for (ei, en) in fr.prog.enums.iter().enumerate() {
            if en.name == name {
                return Some((i, ei));
            }
        }
    }
    None
}

fn find_struct(file_results: &[FileResult], module: &str, name: &str) -> Option<(usize, usize)> {
    for (i, fr) in file_results.iter().enumerate() {
        if fr.prog.module != module {
            continue;
        }
        for (si, sd) in fr.prog.structs.iter().enumerate() {
            if sd.name == name {
                return Some((i, si));
            }
        }
    }
    None
}

fn find_trait(file_results: &[FileResult], module: &str, name: &str) -> Option<(usize, usize)> {
    for (i, fr) in file_results.iter().enumerate() {
        if fr.prog.module != module {
            continue;
        }
        for (ti, td) in fr.prog.traits.iter().enumerate() {
            if td.name == name {
                return Some((i, ti));
            }
        }
    }
    None
}

/// Runs a compiled bytecode program.
pub fn execute(prog: Rc<crate::bytecode::Program>, limits: Limits, deadline: Option<Instant>) -> Result<Value, crate::vm::RuntimeError> {
    GLOBAL_REGISTRY.with(|registry| {
        // The registry is shared and immutable after init; clone handlers out
        // under the lock-free borrow.
        let mut reg = NativeRegistry::new();
        for (name, handler) in registry.iter() {
            reg.register(name, *handler);
        }
        let mut machine = Vm::new(reg, limits);
        machine.set_deadline(deadline);
        machine.execute(prog)
    })
}

/// Compiles the semantic AST into native semantic bytecode and executes it.
/// This entry point is deliberately independent of the historical typed-AST
/// bytecode format; no host callback or compatibility opcode is involved.
pub fn execute_semantic_bytecode(path: &str, args: &[String]) -> Result<Value, crate::vm::RuntimeError> {
    crate::semantic_runtime::run_file(path, args)
        .map(|code| Value::Int(code as i64))
        .map_err(|error| crate::vm::RuntimeError {
            code: error.code,
            message: error.message,
            function: "main".into(),
            offset: 0,
            line: 0,
            column: 0,
            stack: Vec::new(),
        })
}

/// Compiles and runs source code (single file).
pub fn compile_and_execute(name: &str, source_text: &str, limits: Limits) -> Result_ {
    let (bc_prog, diags, err) = compile(name, source_text);
    if err.is_some() || diags.has_errors() {
        return Result_ {
            program: bc_prog,
            diags,
            value: Value::Null,
            error: err,
        };
    }
    let prog = bc_prog.unwrap();
    match execute(prog.clone(), limits, None) {
        Ok(v) => Result_ {
            program: Some(prog),
            diags,
            value: v,
            error: None,
        },
        Err(e) => Result_ {
            program: Some(prog),
            diags,
            value: Value::Null,
            error: Some(e.error_string()),
        },
    }
}
