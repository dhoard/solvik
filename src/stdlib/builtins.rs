//! Built-in type methods and static namespaces.//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]

//!
//! Every entry maps a source-level name (`Type.method`) to a native function
//! id plus its static signature. The checker resolves these at compile time;
//! the VM dispatches by native id only.

use crate::types::{BaseType, Ty};

#[derive(Debug, Clone)]
pub struct BuiltinSig {
    pub native: u16,
    /// Parameter types; when `variadic`, the last element is the element type.
    pub params: Vec<Ty>,
    pub ret: Ty,
    pub variadic: bool,
}

impl BuiltinSig {
    fn of(native: u16, params: Vec<Ty>, ret: BaseType) -> Self {
        BuiltinSig {
            native,
            params,
            ret: Ty::non_null(ret),
            variadic: false,
        }
    }
}

const fn t(b: BaseType) -> Ty {
    Ty {
        base: b,
        nullable: false,
    }
}

// --- native ids ------------------------------------------------------------
pub mod nat {
    // universal
    pub const TO_STRING: u16 = 0;
    // string instance
    pub const STR_LEN: u16 = 1;
    pub const STR_SUBSTR: u16 = 2;
    pub const STR_CONTAINS: u16 = 3;
    pub const STR_STARTS_WITH: u16 = 4;
    pub const STR_ENDS_WITH: u16 = 5;
    pub const STR_SPLIT: u16 = 6;
    pub const STR_REPLACE: u16 = 7;
    pub const STR_TRIM: u16 = 8;
    pub const STR_UPPER: u16 = 9;
    pub const STR_LOWER: u16 = 10;
    pub const STR_INDEX_OF: u16 = 11;
    pub const STR_CHAR_AT: u16 = 12;
    // list instance
    pub const LIST_SIZE: u16 = 20;
    pub const LIST_ADD: u16 = 21;
    pub const LIST_GET: u16 = 22;
    pub const LIST_SET: u16 = 23;
    pub const LIST_REMOVE: u16 = 24;
    pub const LIST_CONTAINS: u16 = 25;
    pub const LIST_INDEX_OF: u16 = 26;
    pub const LIST_REVERSE: u16 = 27;
    pub const LIST_SORT: u16 = 28;
    pub const LIST_JOIN: u16 = 29;
    pub const LIST_CLEAR: u16 = 30;
    pub const LIST_IS_EMPTY: u16 = 31;
    // map instance
    pub const MAP_PUT: u16 = 40;
    pub const MAP_GET: u16 = 41;
    pub const MAP_REMOVE: u16 = 42;
    pub const MAP_CONTAINS_KEY: u16 = 43;
    pub const MAP_SIZE: u16 = 44;
    pub const MAP_KEYS: u16 = 45;
    pub const MAP_VALUES: u16 = 46;
    pub const MAP_CLEAR: u16 = 47;
    pub const MAP_IS_EMPTY: u16 = 48;
    // stack instance
    pub const STACK_PUSH: u16 = 50;
    pub const STACK_POP: u16 = 51;
    pub const STACK_PEEK: u16 = 52;
    pub const STACK_SIZE: u16 = 53;
    pub const STACK_IS_EMPTY: u16 = 54;
    // set instance
    pub const SET_ADD: u16 = 133;
    pub const SET_REMOVE: u16 = 134;
    pub const SET_CONTAINS: u16 = 135;
    pub const SET_SIZE: u16 = 136;
    pub const SET_IS_EMPTY: u16 = 137;
    pub const SET_CLEAR: u16 = 138;
    // writer / reader
    pub const WRITE: u16 = 60;
    pub const PRINT: u16 = 61;
    pub const PRINTLN: u16 = 62;
    pub const REDIRECT: u16 = 63;
    pub const RESET_STREAM: u16 = 64;
    pub const READLN: u16 = 65;
    pub const READ_ALL: u16 = 66;
    // concurrency / processes
    pub const THREAD_NEW: u16 = 70;
    pub const THREAD_START: u16 = 71;
    pub const THREAD_JOIN: u16 = 72;
    pub const MUTEX_NEW: u16 = 73;
    pub const MUTEX_LOCK: u16 = 74;
    pub const MUTEX_UNLOCK: u16 = 75;
    pub const SEM_NEW: u16 = 76;
    pub const SEM_ACQUIRE: u16 = 77;
    pub const SEM_RELEASE: u16 = 78;
    pub const PROC_NEW: u16 = 79;
    pub const PROC_START: u16 = 80;
    pub const PROC_WAIT: u16 = 81;
    pub const PROC_EXIT_CODE: u16 = 82;
    pub const PROC_STDIN: u16 = 83;
    pub const PROC_STDOUT: u16 = 84;
    pub const PROC_STDERR: u16 = 85;
    // regex
    pub const REGEX_NEW: u16 = 90;
    pub const REGEX_MATCHES: u16 = 91;
    pub const REGEX_FIND: u16 = 92;
    pub const REGEX_ALL: u16 = 93;
    pub const REGEX_REPLACE: u16 = 94;
    // math
    pub const MATH_SQRT: u16 = 100;
    pub const MATH_ABS: u16 = 101;
    pub const MATH_FLOOR: u16 = 102;
    pub const MATH_CEIL: u16 = 103;
    pub const MATH_ROUND: u16 = 104;
    pub const MATH_POW: u16 = 105;
    pub const MATH_MIN: u16 = 106;
    pub const MATH_MAX: u16 = 107;
    // type introspection + conversions
    pub const TYPE_OF: u16 = 110;
    pub const TYPE_IS_TYPE: u16 = 111;
    pub const CONV_INT: u16 = 112;
    pub const CONV_FLOAT: u16 = 113;
    pub const CONV_BYTE: u16 = 114;
    pub const CONV_BOOL: u16 = 115;
    pub const CONV_STRING: u16 = 116;
    pub const CONV_CHAR: u16 = 117;
    // data services
    pub const BASE64_ENCODE: u16 = 120;
    pub const BASE64_DECODE: u16 = 121;
    pub const HASH_MD5: u16 = 122;
    pub const HASH_SHA1: u16 = 123;
    pub const HASH_SHA256: u16 = 124;
    pub const JSON_PARSE: u16 = 125;
    pub const JSON_STRINGIFY: u16 = 126;
    pub const TIME_NOW: u16 = 127;
    pub const TIME_SLEEP: u16 = 128;
    pub const RANDOM_NEXT_INT: u16 = 129;
    pub const RANDOM_NEXT_FLOAT: u16 = 130;
    pub const RANDOM_SEED: u16 = 131;
    // filesystem
    pub const FILE_READ: u16 = 140;
    pub const FILE_WRITE: u16 = 141;
    pub const FILE_EXISTS: u16 = 142;
    pub const FILE_DELETE: u16 = 143;
    pub const FILE_LIST_DIR: u16 = 144;
    // testing
    pub const TEST_ASSERT: u16 = 150;
    pub const TEST_ASSERT_EQUAL: u16 = 151;
    // collection constructors
    pub const LIST_NEW: u16 = 160;
    pub const MAP_NEW: u16 = 161;
    pub const STACK_NEW: u16 = 162;
    pub const SET_NEW: u16 = 163;
}

// --- instance method tables ------------------------------------------------

struct Entry {
    sig: BuiltinSig,
}

fn e(native: u16, params: Vec<Ty>, ret: BaseType) -> Entry {
    Entry {
        sig: BuiltinSig::of(native, params, ret),
    }
}

/// Like `e`, but the return type is nullable (e.g. readln at EOF).
fn en(native: u16, params: Vec<Ty>, ret: BaseType) -> Entry {
    Entry {
        sig: BuiltinSig {
            native,
            params,
            ret: Ty::nullable(ret),
            variadic: false,
        },
    }
}

/// Instance methods keyed by (receiver type name, method name).
pub fn instance_method(type_name: &str, method: &str) -> Option<BuiltinSig> {
    let entry: Entry = match (type_name, method) {
        // String
        ("String", "length") => e(nat::STR_LEN, vec![], BaseType::Int),
        ("String", "substring") => e(
            nat::STR_SUBSTR,
            vec![t(BaseType::Int), t(BaseType::Int)],
            BaseType::String,
        ),
        ("String", "contains") => e(nat::STR_CONTAINS, vec![t(BaseType::String)], BaseType::Bool),
        ("String", "startsWith") => e(
            nat::STR_STARTS_WITH,
            vec![t(BaseType::String)],
            BaseType::Bool,
        ),
        ("String", "endsWith") => e(
            nat::STR_ENDS_WITH,
            vec![t(BaseType::String)],
            BaseType::Bool,
        ),
        ("String", "split") => e(
            nat::STR_SPLIT,
            vec![t(BaseType::String)],
            BaseType::List(Box::new(BaseType::String)),
        ),
        ("String", "replace") => e(
            nat::STR_REPLACE,
            vec![t(BaseType::String), t(BaseType::String)],
            BaseType::String,
        ),
        ("String", "trim") => e(nat::STR_TRIM, vec![], BaseType::String),
        ("String", "toUpperCase") => e(nat::STR_UPPER, vec![], BaseType::String),
        ("String", "toLowerCase") => e(nat::STR_LOWER, vec![], BaseType::String),
        ("String", "indexOf") => e(nat::STR_INDEX_OF, vec![t(BaseType::String)], BaseType::Int),
        ("String", "charAt") => e(nat::STR_CHAR_AT, vec![t(BaseType::Int)], BaseType::Char),
        // List
        ("List", "size") => e(nat::LIST_SIZE, vec![], BaseType::Int),
        ("List", "add") => e(nat::LIST_ADD, vec![t(BaseType::Object)], BaseType::Void),
        ("List", "get") => e(nat::LIST_GET, vec![t(BaseType::Int)], BaseType::Object),
        ("List", "set") => e(
            nat::LIST_SET,
            vec![t(BaseType::Int), t(BaseType::Object)],
            BaseType::Void,
        ),
        ("List", "remove") => e(nat::LIST_REMOVE, vec![t(BaseType::Int)], BaseType::Void),
        ("List", "contains") => e(
            nat::LIST_CONTAINS,
            vec![t(BaseType::Object)],
            BaseType::Bool,
        ),
        ("List", "indexOf") => e(nat::LIST_INDEX_OF, vec![t(BaseType::Object)], BaseType::Int),
        ("List", "reverse") => e(nat::LIST_REVERSE, vec![], BaseType::Void),
        ("List", "sort") => e(nat::LIST_SORT, vec![], BaseType::Void),
        ("List", "join") => e(nat::LIST_JOIN, vec![t(BaseType::String)], BaseType::String),
        ("List", "clear") => e(nat::LIST_CLEAR, vec![], BaseType::Void),
        ("List", "isEmpty") => e(nat::LIST_IS_EMPTY, vec![], BaseType::Bool),
        // Map
        ("Map", "put") => e(
            nat::MAP_PUT,
            vec![t(BaseType::Object), t(BaseType::Object)],
            BaseType::Void,
        ),
        ("Map", "get") => e(nat::MAP_GET, vec![t(BaseType::Object)], BaseType::Object),
        ("Map", "remove") => e(nat::MAP_REMOVE, vec![t(BaseType::Object)], BaseType::Void),
        ("Map", "containsKey") => e(
            nat::MAP_CONTAINS_KEY,
            vec![t(BaseType::Object)],
            BaseType::Bool,
        ),
        ("Map", "size") => e(nat::MAP_SIZE, vec![], BaseType::Int),
        ("Map", "keys") => e(
            nat::MAP_KEYS,
            vec![],
            BaseType::List(Box::new(BaseType::Object)),
        ),
        ("Map", "values") => e(
            nat::MAP_VALUES,
            vec![],
            BaseType::List(Box::new(BaseType::Object)),
        ),
        ("Map", "clear") => e(nat::MAP_CLEAR, vec![], BaseType::Void),
        ("Map", "isEmpty") => e(nat::MAP_IS_EMPTY, vec![], BaseType::Bool),
        // Stack
        ("Stack", "push") => e(nat::STACK_PUSH, vec![t(BaseType::Object)], BaseType::Void),
        ("Stack", "pop") => e(nat::STACK_POP, vec![], BaseType::Object),
        ("Stack", "peek") => e(nat::STACK_PEEK, vec![], BaseType::Object),
        ("Stack", "size") => e(nat::STACK_SIZE, vec![], BaseType::Int),
        ("Stack", "isEmpty") => e(nat::STACK_IS_EMPTY, vec![], BaseType::Bool),
        // Set
        ("Set", "add") => e(nat::SET_ADD, vec![t(BaseType::Object)], BaseType::Void),
        ("Set", "remove") => e(nat::SET_REMOVE, vec![t(BaseType::Object)], BaseType::Void),
        ("Set", "contains") => e(nat::SET_CONTAINS, vec![t(BaseType::Object)], BaseType::Bool),
        ("Set", "size") => e(nat::SET_SIZE, vec![], BaseType::Int),
        ("Set", "clear") => e(nat::SET_CLEAR, vec![], BaseType::Void),
        ("Set", "isEmpty") => e(nat::SET_IS_EMPTY, vec![], BaseType::Bool),
        // Writer / Reader
        // Accepts Object; the value is rendered via its toString() at runtime.
        ("Writer", "write") => e(nat::WRITE, vec![t(BaseType::Object)], BaseType::Void),
        ("Writer", "print") => e(nat::PRINT, vec![t(BaseType::Object)], BaseType::Void),
        ("Writer", "println") => e(nat::PRINTLN, vec![t(BaseType::Object)], BaseType::Void),
        ("Writer", "redirect") => e(
            nat::REDIRECT,
            vec![t(BaseType::Interface(
                crate::resolve::builtin::WRITER,
                vec![],
            ))],
            BaseType::Void,
        ),
        ("Writer", "reset") => e(nat::RESET_STREAM, vec![], BaseType::Void),
        ("Reader", "readln") => en(nat::READLN, vec![], BaseType::String),
        ("Reader", "readAll") => e(nat::READ_ALL, vec![], BaseType::String),
        // concurrency / processes
        ("Thread", "start") => e(nat::THREAD_START, vec![], BaseType::Void),
        ("Thread", "join") => e(nat::THREAD_JOIN, vec![], BaseType::Void),
        ("Mutex", "lock") => e(nat::MUTEX_LOCK, vec![], BaseType::Void),
        ("Mutex", "unlock") => e(nat::MUTEX_UNLOCK, vec![], BaseType::Void),
        ("Semaphore", "acquire") => e(nat::SEM_ACQUIRE, vec![], BaseType::Void),
        ("Semaphore", "release") => e(nat::SEM_RELEASE, vec![], BaseType::Void),
        ("Process", "start") => e(nat::PROC_START, vec![], BaseType::Void),
        ("Process", "wait") => e(nat::PROC_WAIT, vec![], BaseType::Int),
        ("Process", "exitCode") => e(nat::PROC_EXIT_CODE, vec![], BaseType::Int),
        ("Process", "stdin") => e(
            nat::PROC_STDIN,
            vec![],
            BaseType::Interface(crate::resolve::builtin::WRITER, vec![]),
        ),
        ("Process", "stdout") => e(
            nat::PROC_STDOUT,
            vec![],
            BaseType::Interface(crate::resolve::builtin::READER, vec![]),
        ),
        ("Process", "stderr") => e(
            nat::PROC_STDERR,
            vec![],
            BaseType::Interface(crate::resolve::builtin::READER, vec![]),
        ),
        // regex
        ("Regex", "matches") => e(
            nat::REGEX_MATCHES,
            vec![t(BaseType::String)],
            BaseType::Bool,
        ),
        ("Regex", "find") => en(nat::REGEX_FIND, vec![t(BaseType::String)], BaseType::String),
        ("Regex", "all") => e(
            nat::REGEX_ALL,
            vec![t(BaseType::String)],
            BaseType::List(Box::new(BaseType::String)),
        ),
        ("Regex", "replace") => e(
            nat::REGEX_REPLACE,
            vec![t(BaseType::String), t(BaseType::String)],
            BaseType::String,
        ),
        _ => return None,
    };
    Some(entry.sig)
}

/// Static members keyed by (namespace type name, member name).
pub fn static_member(type_name: &str, name: &str) -> Option<BuiltinSig> {
    let entry: Entry = match (type_name, name) {
        ("Math", "sqrt") => e(nat::MATH_SQRT, vec![t(BaseType::Float)], BaseType::Float),
        ("Math", "abs") => e(nat::MATH_ABS, vec![t(BaseType::Object)], BaseType::Object),
        ("Math", "floor") => e(nat::MATH_FLOOR, vec![t(BaseType::Float)], BaseType::Float),
        ("Math", "ceil") => e(nat::MATH_CEIL, vec![t(BaseType::Float)], BaseType::Float),
        ("Math", "round") => e(nat::MATH_ROUND, vec![t(BaseType::Float)], BaseType::Float),
        ("Math", "pow") => e(
            nat::MATH_POW,
            vec![t(BaseType::Float), t(BaseType::Float)],
            BaseType::Float,
        ),
        ("Math", "min") => e(
            nat::MATH_MIN,
            vec![t(BaseType::Object), t(BaseType::Object)],
            BaseType::Object,
        ),
        ("Math", "max") => e(
            nat::MATH_MAX,
            vec![t(BaseType::Object), t(BaseType::Object)],
            BaseType::Object,
        ),
        ("Type", "of") => e(nat::TYPE_OF, vec![t(BaseType::Object)], BaseType::String),
        ("Type", "isType") => e(
            nat::TYPE_IS_TYPE,
            vec![t(BaseType::Object), t(BaseType::String)],
            BaseType::Bool,
        ),
        ("Int", "from") => e(nat::CONV_INT, vec![t(BaseType::Object)], BaseType::Int),
        ("Float", "from") => e(nat::CONV_FLOAT, vec![t(BaseType::Object)], BaseType::Float),
        ("Byte", "from") => e(nat::CONV_BYTE, vec![t(BaseType::Object)], BaseType::Byte),
        ("Bool", "from") => e(nat::CONV_BOOL, vec![t(BaseType::Object)], BaseType::Bool),
        ("String", "from") => e(
            nat::CONV_STRING,
            vec![t(BaseType::Object)],
            BaseType::String,
        ),
        ("Char", "from") => e(nat::CONV_CHAR, vec![t(BaseType::Object)], BaseType::Char),
        ("Base64", "encode") => e(
            nat::BASE64_ENCODE,
            vec![t(BaseType::String)],
            BaseType::String,
        ),
        ("Base64", "decode") => e(
            nat::BASE64_DECODE,
            vec![t(BaseType::String)],
            BaseType::String,
        ),
        ("Hash", "md5") => e(nat::HASH_MD5, vec![t(BaseType::String)], BaseType::String),
        ("Hash", "sha1") => e(nat::HASH_SHA1, vec![t(BaseType::String)], BaseType::String),
        ("Hash", "sha256") => e(
            nat::HASH_SHA256,
            vec![t(BaseType::String)],
            BaseType::String,
        ),
        ("Json", "parse") => e(nat::JSON_PARSE, vec![t(BaseType::String)], BaseType::Object),
        ("Json", "stringify") => e(
            nat::JSON_STRINGIFY,
            vec![t(BaseType::Object)],
            BaseType::String,
        ),
        ("Time", "now") => e(nat::TIME_NOW, vec![], BaseType::Int),
        ("Time", "sleep") => e(nat::TIME_SLEEP, vec![t(BaseType::Int)], BaseType::Void),
        ("Random", "nextInt") => e(nat::RANDOM_NEXT_INT, vec![t(BaseType::Int)], BaseType::Int),
        ("Random", "nextFloat") => e(nat::RANDOM_NEXT_FLOAT, vec![], BaseType::Float),
        ("Random", "seed") => e(nat::RANDOM_SEED, vec![t(BaseType::Int)], BaseType::Void),
        ("File", "read") => e(nat::FILE_READ, vec![t(BaseType::String)], BaseType::String),
        ("File", "write") => e(
            nat::FILE_WRITE,
            vec![t(BaseType::String), t(BaseType::String)],
            BaseType::Void,
        ),
        ("File", "exists") => e(nat::FILE_EXISTS, vec![t(BaseType::String)], BaseType::Bool),
        ("File", "delete") => e(nat::FILE_DELETE, vec![t(BaseType::String)], BaseType::Void),
        ("File", "listDir") => e(
            nat::FILE_LIST_DIR,
            vec![t(BaseType::String)],
            BaseType::List(Box::new(BaseType::String)),
        ),
        ("Test", "assert") => e(
            nat::TEST_ASSERT,
            vec![t(BaseType::Bool), Ty::nullable(BaseType::String)],
            BaseType::Void,
        ),
        ("Test", "assertEqual") => e(
            nat::TEST_ASSERT_EQUAL,
            vec![
                t(BaseType::Object),
                t(BaseType::Object),
                Ty::nullable(BaseType::String),
            ],
            BaseType::Void,
        ),
        ("List", "new") => e(
            nat::LIST_NEW,
            vec![],
            BaseType::List(Box::new(BaseType::Object)),
        ),
        ("Map", "new") => e(
            nat::MAP_NEW,
            vec![],
            BaseType::Map(Box::new(BaseType::Object), Box::new(BaseType::Object)),
        ),
        ("Stack", "new") => e(
            nat::STACK_NEW,
            vec![],
            BaseType::Stack(Box::new(BaseType::Object)),
        ),
        ("Set", "new") => e(
            nat::SET_NEW,
            vec![],
            BaseType::Set(Box::new(BaseType::Object)),
        ),
        ("Thread", "new") => e(
            nat::THREAD_NEW,
            vec![t(BaseType::Interface(
                crate::resolve::builtin::RUNNABLE,
                vec![],
            ))],
            BaseType::native(crate::types::native_kind::THREAD),
        ),
        ("Mutex", "new") => e(
            nat::MUTEX_NEW,
            vec![],
            BaseType::native(crate::types::native_kind::MUTEX),
        ),
        ("Semaphore", "new") => e(
            nat::SEM_NEW,
            vec![t(BaseType::Int)],
            BaseType::native(crate::types::native_kind::SEMAPHORE),
        ),
        ("Process", "new") => e(
            nat::PROC_NEW,
            vec![
                t(BaseType::String),
                t(BaseType::List(Box::new(BaseType::String))),
            ],
            BaseType::native(crate::types::native_kind::PROCESS),
        ),
        ("Regex", "new") => e(
            nat::REGEX_NEW,
            vec![t(BaseType::String)],
            BaseType::native(crate::types::native_kind::REGEX),
        ),
        _ => return None,
    };
    Some(entry.sig)
}

/// Whether a native returns a value (used by the verifier for stack effects).
/// True when the native is an instance method whose receiver occupies
/// args[0] (below the explicit arguments on the VM stack).
pub fn native_takes_receiver(native: u16) -> bool {
    use nat::*;
    !matches!(
        native,
        LIST_NEW
            | MAP_NEW
            | STACK_NEW
            | SET_NEW
            | THREAD_NEW
            | MUTEX_NEW
            | SEM_NEW
            | PROC_NEW
            | REGEX_NEW
            | MATH_SQRT
            | MATH_ABS
            | MATH_FLOOR
            | MATH_CEIL
            | MATH_ROUND
            | MATH_POW
            | MATH_MIN
            | MATH_MAX
            | TYPE_OF
            | TYPE_IS_TYPE
            | CONV_INT
            | CONV_FLOAT
            | CONV_BYTE
            | CONV_BOOL
            | CONV_STRING
            | CONV_CHAR
            | BASE64_ENCODE
            | BASE64_DECODE
            | HASH_MD5
            | HASH_SHA1
            | HASH_SHA256
            | JSON_PARSE
            | JSON_STRINGIFY
            | TIME_NOW
            | TIME_SLEEP
            | RANDOM_NEXT_INT
            | RANDOM_NEXT_FLOAT
            | RANDOM_SEED
            | FILE_READ
            | FILE_WRITE
            | FILE_EXISTS
            | FILE_DELETE
            | FILE_LIST_DIR
            | TEST_ASSERT
            | TEST_ASSERT_EQUAL
    )
}

/// Whether a bytecode native id is implemented by the VM dispatcher.
pub fn native_known(native: u16) -> bool {
    use nat::*;
    matches!(
        native,
        TO_STRING
            | STR_LEN
            | STR_SUBSTR
            | STR_CONTAINS
            | STR_STARTS_WITH
            | STR_ENDS_WITH
            | STR_SPLIT
            | STR_REPLACE
            | STR_TRIM
            | STR_UPPER
            | STR_LOWER
            | STR_INDEX_OF
            | STR_CHAR_AT
            | LIST_SIZE
            | LIST_ADD
            | LIST_GET
            | LIST_SET
            | LIST_REMOVE
            | LIST_CONTAINS
            | LIST_INDEX_OF
            | LIST_REVERSE
            | LIST_SORT
            | LIST_JOIN
            | LIST_CLEAR
            | LIST_IS_EMPTY
            | MAP_PUT
            | MAP_GET
            | MAP_REMOVE
            | MAP_CONTAINS_KEY
            | MAP_SIZE
            | MAP_KEYS
            | MAP_VALUES
            | MAP_CLEAR
            | MAP_IS_EMPTY
            | STACK_PUSH
            | STACK_POP
            | STACK_PEEK
            | STACK_SIZE
            | STACK_IS_EMPTY
            | SET_ADD
            | SET_REMOVE
            | SET_CONTAINS
            | SET_SIZE
            | SET_IS_EMPTY
            | SET_CLEAR
            | WRITE
            | PRINT
            | PRINTLN
            | REDIRECT
            | RESET_STREAM
            | READLN
            | READ_ALL
            | THREAD_NEW
            | THREAD_START
            | THREAD_JOIN
            | MUTEX_NEW
            | MUTEX_LOCK
            | MUTEX_UNLOCK
            | SEM_NEW
            | SEM_ACQUIRE
            | SEM_RELEASE
            | PROC_NEW
            | PROC_START
            | PROC_WAIT
            | PROC_EXIT_CODE
            | PROC_STDIN
            | PROC_STDOUT
            | PROC_STDERR
            | REGEX_NEW
            | REGEX_MATCHES
            | REGEX_FIND
            | REGEX_ALL
            | REGEX_REPLACE
            | MATH_SQRT
            | MATH_ABS
            | MATH_FLOOR
            | MATH_CEIL
            | MATH_ROUND
            | MATH_POW
            | MATH_MIN
            | MATH_MAX
            | TYPE_OF
            | TYPE_IS_TYPE
            | CONV_INT
            | CONV_FLOAT
            | CONV_BYTE
            | CONV_BOOL
            | CONV_STRING
            | CONV_CHAR
            | BASE64_ENCODE
            | BASE64_DECODE
            | HASH_MD5
            | HASH_SHA1
            | HASH_SHA256
            | JSON_PARSE
            | JSON_STRINGIFY
            | TIME_NOW
            | TIME_SLEEP
            | RANDOM_NEXT_INT
            | RANDOM_NEXT_FLOAT
            | RANDOM_SEED
            | FILE_READ
            | FILE_WRITE
            | FILE_EXISTS
            | FILE_DELETE
            | FILE_LIST_DIR
            | TEST_ASSERT
            | TEST_ASSERT_EQUAL
            | LIST_NEW
            | MAP_NEW
            | STACK_NEW
            | SET_NEW
    )
}

/// Minimum and maximum number of explicit arguments accepted by a native
/// call. The receiver, when present, is encoded separately in bytecode.
pub fn native_arity(native: u16) -> Option<(usize, usize)> {
    use nat::*;
    let arity = match native {
        TO_STRING => 1,
        STR_LEN | STR_TRIM | STR_UPPER | STR_LOWER => 0,
        STR_SUBSTR => 2,
        STR_CONTAINS | STR_STARTS_WITH | STR_ENDS_WITH | STR_SPLIT | STR_INDEX_OF | STR_CHAR_AT => {
            1
        }
        STR_REPLACE => 2,
        LIST_SIZE | LIST_REVERSE | LIST_SORT | LIST_CLEAR | LIST_IS_EMPTY => 0,
        LIST_ADD | LIST_GET | LIST_REMOVE | LIST_CONTAINS | LIST_INDEX_OF => 1,
        LIST_SET => 2,
        LIST_JOIN => 1,
        MAP_SIZE | MAP_KEYS | MAP_VALUES | MAP_CLEAR | MAP_IS_EMPTY => 0,
        MAP_PUT => 2,
        MAP_GET | MAP_REMOVE | MAP_CONTAINS_KEY => 1,
        STACK_POP | STACK_PEEK | STACK_SIZE | STACK_IS_EMPTY => 0,
        STACK_PUSH => 1,
        SET_SIZE | SET_IS_EMPTY | SET_CLEAR => 0,
        SET_ADD | SET_REMOVE | SET_CONTAINS => 1,
        WRITE | PRINT | PRINTLN => 1,
        REDIRECT => 1,
        RESET_STREAM | READLN | READ_ALL => 0,
        THREAD_NEW => 1,
        THREAD_START | THREAD_JOIN | MUTEX_LOCK | MUTEX_UNLOCK | SEM_ACQUIRE | SEM_RELEASE
        | PROC_START | PROC_WAIT | PROC_EXIT_CODE | PROC_STDIN | PROC_STDOUT | PROC_STDERR => 0,
        MUTEX_NEW => 0,
        SEM_NEW => 1,
        PROC_NEW => 2,
        REGEX_NEW => 1,
        REGEX_MATCHES | REGEX_FIND | REGEX_ALL => 1,
        REGEX_REPLACE => 2,
        MATH_SQRT | MATH_ABS | MATH_FLOOR | MATH_CEIL | MATH_ROUND => 1,
        MATH_POW | MATH_MIN | MATH_MAX => 2,
        TYPE_OF => 1,
        TYPE_IS_TYPE => 2,
        CONV_INT | CONV_FLOAT | CONV_BYTE | CONV_BOOL | CONV_STRING | CONV_CHAR => 1,
        BASE64_ENCODE | BASE64_DECODE | HASH_MD5 | HASH_SHA1 | HASH_SHA256 | JSON_PARSE => 1,
        JSON_STRINGIFY => 1,
        TIME_NOW => 0,
        TIME_SLEEP | RANDOM_NEXT_INT | RANDOM_SEED => 1,
        RANDOM_NEXT_FLOAT => 0,
        FILE_READ | FILE_EXISTS | FILE_DELETE | FILE_LIST_DIR => 1,
        FILE_WRITE => 2,
        TEST_ASSERT => 2,
        TEST_ASSERT_EQUAL => 3,
        LIST_NEW | MAP_NEW | STACK_NEW | SET_NEW => 0,
        _ => return None,
    };
    Some(match native {
        TEST_ASSERT => (1, 2),
        TEST_ASSERT_EQUAL => (2, 3),
        _ => (arity, arity),
    })
}

pub fn native_returns_value(native: u16) -> bool {
    use nat::*;
    matches!(
        native,
        LIST_NEW
            | MAP_NEW
            | STACK_NEW
            | SET_NEW
            | THREAD_NEW
            | MUTEX_NEW
            | SEM_NEW
            | PROC_NEW
            | REGEX_NEW
            | TO_STRING
            | STR_LEN
            | STR_SUBSTR
            | STR_CONTAINS
            | STR_STARTS_WITH
            | STR_ENDS_WITH
            | STR_SPLIT
            | STR_REPLACE
            | STR_TRIM
            | STR_UPPER
            | STR_LOWER
            | STR_INDEX_OF
            | STR_CHAR_AT
            | LIST_SIZE
            | LIST_GET
            | LIST_CONTAINS
            | LIST_INDEX_OF
            | LIST_JOIN
            | LIST_IS_EMPTY
            | MAP_GET
            | MAP_CONTAINS_KEY
            | MAP_SIZE
            | MAP_KEYS
            | MAP_VALUES
            | MAP_IS_EMPTY
            | STACK_POP
            | STACK_PEEK
            | STACK_SIZE
            | STACK_IS_EMPTY
            | SET_CONTAINS
            | SET_SIZE
            | SET_IS_EMPTY
            | READLN
            | READ_ALL
            | PROC_WAIT
            | PROC_EXIT_CODE
            | PROC_STDIN
            | PROC_STDOUT
            | PROC_STDERR
            | REGEX_MATCHES
            | REGEX_FIND
            | REGEX_ALL
            | REGEX_REPLACE
            | MATH_SQRT
            | MATH_ABS
            | MATH_FLOOR
            | MATH_CEIL
            | MATH_ROUND
            | MATH_POW
            | MATH_MIN
            | MATH_MAX
            | TYPE_OF
            | TYPE_IS_TYPE
            | CONV_INT
            | CONV_FLOAT
            | CONV_BYTE
            | CONV_BOOL
            | CONV_STRING
            | CONV_CHAR
            | BASE64_ENCODE
            | BASE64_DECODE
            | HASH_MD5
            | HASH_SHA1
            | HASH_SHA256
            | JSON_PARSE
            | JSON_STRINGIFY
            | TIME_NOW
            | RANDOM_NEXT_INT
            | RANDOM_NEXT_FLOAT
            | FILE_READ
            | FILE_EXISTS
            | FILE_LIST_DIR
    )
}
