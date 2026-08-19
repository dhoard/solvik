//! Symbol table for scope management.
//!
//! Port of internal/symbol/symbol.go.

use crate::types::Type;
use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::rc::Rc;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SymbolKind {
    Variable,
    Function,
    Module,
    NativeFunction,
    Struct,
    Trait,
}

pub struct Symbol {
    pub name: String,
    pub kind: SymbolKind,
    pub ty: Option<Rc<Type>>,
    pub slot: i32,
    pub parameter: bool,
    pub module_name: String,
    pub defined: Cell<bool>,
    pub mut_flag: bool,
    // Struct field tracking
    pub is_struct_field: bool,
    pub field_index: usize,
    pub field_of_slot: i32,
}

impl Symbol {
    pub fn new_variable(name: &str, ty: Option<Rc<Type>>, slot: i32) -> Rc<Symbol> {
        Rc::new(Symbol {
            name: name.to_string(),
            kind: SymbolKind::Variable,
            ty,
            slot,
            parameter: false,
            module_name: String::new(),
            defined: Cell::new(false),
            mut_flag: false,
            is_struct_field: false,
            field_index: 0,
            field_of_slot: 0,
        })
    }

    pub fn new_module(name: &str) -> Rc<Symbol> {
        Rc::new(Symbol {
            name: name.to_string(),
            kind: SymbolKind::Module,
            ty: None,
            slot: -1,
            parameter: false,
            module_name: name.to_string(),
            defined: Cell::new(true),
            mut_flag: false,
            is_struct_field: false,
            field_index: 0,
            field_of_slot: 0,
        })
    }
}

pub struct Scope {
    pub parent: Option<Rc<Scope>>,
    symbols: RefCell<HashMap<String, Rc<Symbol>>>,
    pub func_type: Option<Rc<Type>>,
    pub depth: usize,
}

impl Scope {
    pub fn new(parent: Option<Rc<Scope>>, func_type: Option<Rc<Type>>) -> Rc<Scope> {
        let depth = parent.as_ref().map(|p| p.depth + 1).unwrap_or(0);
        Rc::new(Scope {
            parent,
            symbols: RefCell::new(HashMap::new()),
            func_type,
            depth,
        })
    }

    pub fn declare(&self, sym: Rc<Symbol>) {
        self.symbols.borrow_mut().insert(sym.name.clone(), sym);
    }

    pub fn resolve(&self, name: &str) -> Option<Rc<Symbol>> {
        let mut scope: Option<&Scope> = Some(self);
        while let Some(s) = scope {
            if let Some(sym) = s.symbols.borrow().get(name) {
                return Some(sym.clone());
            }
            scope = s.parent.as_deref();
        }
        None
    }
}
