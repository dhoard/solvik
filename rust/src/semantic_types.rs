//! Type model used by the native semantic frontend.

use std::fmt;

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct TypeRef {
    pub name: String,
    pub args: Vec<TypeRef>,
    pub nullable: bool,
}

impl TypeRef {
    pub fn named(name: impl Into<String>) -> Self { Self { name: name.into(), args: Vec::new(), nullable: false } }
    pub fn generic(name: impl Into<String>, args: Vec<TypeRef>) -> Self { Self { name: name.into(), args, nullable: false } }
    pub fn nullable(mut self) -> Self { self.nullable = true; self }
    pub fn nonnull(mut self) -> Self { self.nullable = false; self }
    pub fn is(&self, name: &str) -> bool { self.name == name && self.args.is_empty() }
    pub fn function(args: Vec<TypeRef>) -> Self { Self::generic("func", args) }
}

impl fmt::Display for TypeRef {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.name)?;
        if !self.args.is_empty() {
            write!(f, "<")?;
            for (i, arg) in self.args.iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}", arg)?; }
            write!(f, ">")?;
        }
        if self.nullable { write!(f, "?")?; }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TypeParam {
    pub name: String,
    pub constraints: Vec<TypeRef>,
}

impl TypeParam {
    pub fn new(name: impl Into<String>, constraints: Vec<TypeRef>) -> Self { Self { name: name.into(), constraints } }
}

/// Bind generic variables in a declared type against a concrete type.
/// Nullability is checked separately: null never infers a type parameter.
pub fn bind(pattern: &TypeRef, actual: &TypeRef, bindings: &mut Vec<(String, TypeRef)>) -> bool {
    if pattern.name.len() == 1 && pattern.name.chars().next().is_some_and(|c| c.is_ascii_uppercase()) && pattern.args.is_empty() {
        if actual.name == "null" { return false; }
        if let Some((_, prior)) = bindings.iter().find(|(name, _)| name == &pattern.name) { return prior == actual; }
        bindings.push((pattern.name.clone(), actual.clone()));
        return true;
    }
    if pattern.name != actual.name || pattern.args.len() != actual.args.len() { return false; }
    if pattern.nullable != actual.nullable && !(pattern.nullable && !actual.nullable) { return false; }
    pattern.args.iter().zip(&actual.args).all(|(p, a)| bind(p, a, bindings))
}

pub fn substitute(typ: &TypeRef, bindings: &[(String, TypeRef)]) -> TypeRef {
    if let Some((_, value)) = bindings.iter().find(|(name, _)| name == &typ.name) {
        let mut value = value.clone();
        if typ.nullable { value.nullable = true; }
        return value;
    }
    TypeRef { name: typ.name.clone(), args: typ.args.iter().map(|a| substitute(a, bindings)).collect(), nullable: typ.nullable }
}

pub fn assignable(actual: &TypeRef, expected: &TypeRef) -> bool {
    if actual.is("null") { return expected.nullable; }
    if actual.name == expected.name && actual.args.len() == expected.args.len() {
        if actual.nullable && !expected.nullable { return false; }
        return actual.args.iter().zip(&expected.args).all(|(a, e)| a == e) || actual.args.iter().zip(&expected.args).all(|(a, e)| assignable(a, e));
    }
    matches!((actual.name.as_str(), expected.name.as_str()), ("byte", "int") | ("byte", "float") | ("int", "float"))
}

#[cfg(test)]
mod tests {
    use super::{assignable, bind, substitute, TypeRef};

    #[test]
    fn formats_generic_nullable_and_function_types() {
        let typ = TypeRef::generic("map", vec![TypeRef::named("string"), TypeRef::named("int")]).nullable();
        assert_eq!(typ.to_string(), "map<string, int>?");
        assert_eq!(TypeRef::function(vec![TypeRef::named("int"), TypeRef::named("string")]).to_string(), "func<int, string>");
    }

    #[test]
    fn infers_and_substitutes_type_parameters() {
        let pattern = TypeRef::generic("list", vec![TypeRef::named("T")]);
        let actual = TypeRef::generic("list", vec![TypeRef::named("int")]);
        let mut bindings = Vec::new();
        assert!(bind(&pattern, &actual, &mut bindings));
        assert_eq!(substitute(&pattern, &bindings), actual);
    }

    #[test]
    fn enforces_nullability_and_numeric_widening() {
        assert!(assignable(&TypeRef::named("int"), &TypeRef::named("float")));
        assert!(!assignable(&TypeRef::named("null"), &TypeRef::named("int")));
        assert!(assignable(&TypeRef::named("null"), &TypeRef::named("int").nullable()));
    }
}
