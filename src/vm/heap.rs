//! Managed heap: handle-based allocation with tracing mark-and-sweep GC.
//!
//! Collections (`List`, `Map`, `Stack`, `Set`) store their payload in a
//! per-collection `Arc<Mutex<_>>`. The heap lock protects object slots and
//! metadata; each collection's own mutex protects its elements. This lets
//! unrelated collections progress concurrently while every operation on one
//! collection stays linearizable under its own lock.
//!
//! Lock-ordering rule (single, normative):
//! - The heap lock is the outermost lock. Code that holds it may briefly take
//!   collection locks (GC marking, structural equality/hashing of collection
//!   values, JSON serialization).
//! - Collection operations may take the heap lock *while holding their
//!   collection lock*, solely to dereference/compare element values
//!   (equality, hashing, comparison). They never hold the heap lock while
//!   acquiring another collection lock.
//! - GC marking runs only when the program has a single active thread, so the
//!   heap-outer and collection-outer orders never interleave on live threads.

use std::collections::{HashMap, HashSet, VecDeque};
use std::io::Read;
use std::process::{Child, ChildStdin};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::JoinHandle;

use super::value::Value;

/// A heap handle.
pub type GcRef = u32;

// ---------------------------------------------------------------------------
// Per-collection state (each guarded by its own mutex)
// ---------------------------------------------------------------------------

/// Contiguous vector storage: amortized O(1) append, O(1) indexed access.
#[derive(Debug, Default)]
pub struct ListData {
    pub items: Vec<Value>,
}

/// Hash-indexed entries: `buckets` maps a value hash to the entries whose
/// key hashes to it; equality is verified at lookup time so hash collisions
/// stay correct. Expected O(1) get/put/remove/contains. Retrieval order is
/// unspecified (hash layout); programs must not depend on it.
#[derive(Debug)]
pub struct MapData {
    pub buckets: HashMap<i64, Vec<(Value, Value)>, FnvBuildHasher>,
    pub len: usize,
}

impl Default for MapData {
    fn default() -> Self {
        MapData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
        }
    }
}

impl MapData {
    pub fn with_capacity(capacity: usize) -> Self {
        let mut d = Self::default();
        d.buckets.reserve(capacity);
        d
    }

    /// Snapshot of all entries (handle copies; retrieval order unspecified).
    pub fn entries(&self) -> Vec<(Value, Value)> {
        let mut out = Vec::with_capacity(self.len);
        for bucket in self.buckets.values() {
            out.extend(bucket.iter().copied());
        }
        out
    }
}

/// Contiguous deque storage: O(1) push/pop/peek at both ends.
#[derive(Debug, Default)]
pub struct StackData {
    pub items: VecDeque<Value>,
}

impl StackData {
    pub fn with_capacity(capacity: usize) -> Self {
        StackData {
            items: VecDeque::with_capacity(capacity),
        }
    }
}

/// Hash-indexed unique elements. Membership is unordered; expected O(1)
/// add/remove/contains.
#[derive(Debug)]
pub struct SetData {
    pub buckets: HashMap<i64, Vec<Value>, FnvBuildHasher>,
    pub len: usize,
}

impl Default for SetData {
    fn default() -> Self {
        SetData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
        }
    }
}

impl SetData {
    pub fn with_capacity(capacity: usize) -> Self {
        let mut d = Self::default();
        d.buckets.reserve(capacity);
        d
    }

    /// Snapshot of all elements (handle copies; order unspecified).
    pub fn items(&self) -> Vec<Value> {
        let mut out = Vec::with_capacity(self.len);
        for bucket in self.buckets.values() {
            out.extend(bucket.iter().copied());
        }
        out
    }
}

/// Deterministic FNV-1a hasher so bucket layout (and therefore retrieval
/// order) is stable across runs.
pub struct FnvBuildHasher;

impl std::hash::BuildHasher for FnvBuildHasher {
    type Hasher = FnvHasher;
    fn build_hasher(&self) -> FnvHasher {
        FnvHasher(0xcbf2_9ce4_8422_2325u64)
    }
}

#[derive(Default)]
pub struct FnvHasher(u64);

impl std::hash::Hasher for FnvHasher {
    fn finish(&self) -> u64 {
        self.0
    }
    fn write(&mut self, bytes: &[u8]) {
        for b in bytes {
            self.0 ^= u64::from(*b);
            self.0 = self.0.wrapping_mul(0x0000_0100_0000_01b3);
        }
    }
}

#[derive(Debug, Default)]
struct ProcessOutputState {
    bytes: VecDeque<u8>,
    eof: bool,
}

/// Buffered output from a child process. A pump thread fills the buffer so a
/// child cannot block on a full OS pipe while the Solvik program is waiting.
#[derive(Debug, Default)]
pub struct ProcessOutput {
    state: Mutex<ProcessOutputState>,
    ready: Condvar,
}

impl ProcessOutput {
    pub fn pump(self: Arc<Self>, mut reader: impl Read) {
        let mut buf = [0u8; 8192];
        loop {
            match reader.read(&mut buf) {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
                    state.bytes.extend(&buf[..n]);
                    self.ready.notify_all();
                }
            }
        }
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        state.eof = true;
        self.ready.notify_all();
    }

    pub fn read_line(&self) -> Option<String> {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        loop {
            if let Some(pos) = state.bytes.iter().position(|b| *b == b'\n') {
                let mut line: Vec<u8> = state.bytes.drain(..=pos).collect();
                line.pop();
                if line.last() == Some(&b'\r') {
                    line.pop();
                }
                return Some(String::from_utf8_lossy(&line).into_owned());
            }
            if state.eof {
                if state.bytes.is_empty() {
                    return None;
                }
                let line: Vec<u8> = state.bytes.drain(..).collect();
                return Some(String::from_utf8_lossy(&line).into_owned());
            }
            state = self.ready.wait(state).unwrap_or_else(|e| e.into_inner());
        }
    }

    pub fn read_all(&self) -> String {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        while !state.eof {
            state = self.ready.wait(state).unwrap_or_else(|e| e.into_inner());
        }
        let bytes: Vec<u8> = state.bytes.drain(..).collect();
        String::from_utf8_lossy(&bytes).into_owned()
    }
}

/// Everything that lives on the managed heap.
#[derive(Debug)]
pub enum HeapObject {
    Instance {
        class: u16,
        fields: Vec<Value>,
    },
    String {
        text: String,
    },
    List {
        data: Arc<Mutex<ListData>>,
    },
    Map {
        data: Arc<Mutex<MapData>>,
    },
    Stack {
        data: Arc<Mutex<StackData>>,
    },
    Set {
        data: Arc<Mutex<SetData>>,
    },
    Enum {
        enum_id: u16,
        index: u8,
        payload: Option<Value>,
    },
    Exception {
        /// Native kind tag (`native_kind::EXCEPTION`).
        kind: u8,
        message: String,
    },
    /// Arbitrary-precision integer.
    BigInteger {
        value: crate::bignum::BigInt,
    },
    /// Arbitrary-precision decimal.
    BigDecimal {
        value: crate::bignum::Dec,
    },
    Thread {
        /// The Runnable object to execute.
        runnable: Option<GcRef>,
        handle: Option<JoinHandle<()>>,
        done: bool,
    },
    Mutex {
        inner: Arc<(Mutex<bool>, Condvar)>,
    },
    Semaphore {
        inner: Arc<(Mutex<i32>, Condvar)>,
        max: i32,
    },
    Process {
        child: Option<Child>,
        exit_code: Option<i32>,
        /// Command line (used by start()).
        cmd: Option<String>,
        argv: Vec<String>,
        stdin: Option<ChildStdin>,
        stdout: Option<Arc<ProcessOutput>>,
        stderr: Option<Arc<ProcessOutput>>,
    },
    ProcessStream {
        process: GcRef,
        kind: u8,
    },
    Regex {
        re: regex::Regex,
    },
    /// Standard stream handle (kind: 0=stdin 1=stdout 2=stderr).
    Stream {
        kind: u8,
    },
}

impl HeapObject {
    /// Mutable access to an instance's fields (None for non-instances).
    #[cfg(test)]
    fn fields_mut(&mut self) -> Option<&mut Vec<Value>> {
        match self {
            HeapObject::Instance { fields, .. } => Some(fields),
            _ => None,
        }
    }

    /// Fresh empty collections with fresh per-collection locks.
    pub fn list() -> Self {
        HeapObject::List {
            data: Arc::new(Mutex::new(ListData::default())),
        }
    }

    pub fn list_with_capacity(capacity: usize) -> Self {
        HeapObject::List {
            data: Arc::new(Mutex::new(ListData {
                items: Vec::with_capacity(capacity),
            })),
        }
    }

    pub fn map() -> Self {
        HeapObject::Map {
            data: Arc::new(Mutex::new(MapData::default())),
        }
    }

    pub fn map_with_capacity(capacity: usize) -> Self {
        HeapObject::Map {
            data: Arc::new(Mutex::new(MapData::with_capacity(capacity))),
        }
    }

    pub fn stack() -> Self {
        HeapObject::Stack {
            data: Arc::new(Mutex::new(StackData::default())),
        }
    }

    pub fn stack_with_capacity(capacity: usize) -> Self {
        HeapObject::Stack {
            data: Arc::new(Mutex::new(StackData::with_capacity(capacity))),
        }
    }

    pub fn set() -> Self {
        HeapObject::Set {
            data: Arc::new(Mutex::new(SetData::default())),
        }
    }

    pub fn set_with_capacity(capacity: usize) -> Self {
        HeapObject::Set {
            data: Arc::new(Mutex::new(SetData::with_capacity(capacity))),
        }
    }

    /// Values reachable from this object (for GC marking). Newly marked
    /// handles are appended to `queue` for worklist traversal.
    fn mark_into(&self, marked: &mut HashSet<GcRef>, queue: &mut Vec<GcRef>) {
        match self {
            HeapObject::Instance { fields, .. } => {
                for v in fields {
                    mark_value(v, marked, queue);
                }
            }
            // Collection payloads live behind per-collection locks; taking
            // them here is the documented heap-outer / collection-inner order
            // (GC marking runs only on the sole active thread).
            HeapObject::List { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                for v in &g.items {
                    mark_value(v, marked, queue);
                }
            }
            HeapObject::Stack { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                for v in &g.items {
                    mark_value(v, marked, queue);
                }
            }
            HeapObject::Set { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                for bucket in g.buckets.values() {
                    for v in bucket {
                        mark_value(v, marked, queue);
                    }
                }
            }
            HeapObject::Map { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                for bucket in g.buckets.values() {
                    for (k, v) in bucket {
                        mark_value(k, marked, queue);
                        mark_value(v, marked, queue);
                    }
                }
            }
            HeapObject::Enum {
                payload: Some(p), ..
            } => mark_value(p, marked, queue),
            HeapObject::Thread {
                runnable: Some(r), ..
            } => {
                mark_value(&Value::Object(*r), marked, queue);
            }
            HeapObject::ProcessStream { process, .. } => {
                mark_value(&Value::Object(*process), marked, queue);
            }
            _ => {}
        }
    }

    /// Display form used by `toString` and diagnostics.
    pub fn string(&self) -> String {
        match self {
            HeapObject::String { text } => text.clone(),
            HeapObject::List { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                format!(
                    "[{}]",
                    g.items
                        .iter()
                        .map(value_repr)
                        .collect::<Vec<_>>()
                        .join(", ")
                )
            }
            HeapObject::Map { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                let mut pairs: Vec<String> = Vec::with_capacity(g.len);
                for bucket in g.buckets.values() {
                    for (k, v) in bucket {
                        pairs.push(format!("{}: {}", value_repr(k), value_repr(v)));
                    }
                }
                format!("{{ {} }}", pairs.join(", "))
            }
            HeapObject::Stack { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                format!(
                    "Stack[{}]",
                    g.items
                        .iter()
                        .map(value_repr)
                        .collect::<Vec<_>>()
                        .join(", ")
                )
            }
            HeapObject::Set { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                let mut reprs: Vec<String> = Vec::with_capacity(g.len);
                for bucket in g.buckets.values() {
                    for v in bucket {
                        reprs.push(value_repr(v));
                    }
                }
                format!("Set[{}]", reprs.join(", "))
            }
            HeapObject::Enum { index, .. } => format!("Enum#{}", index),
            HeapObject::Exception { message, .. } => message.clone(),
            HeapObject::BigInteger { value } => value.to_string(),
            HeapObject::BigDecimal { value } => value.to_string(),
            HeapObject::Instance { class, .. } => format!("<instance #{}>", class),
            HeapObject::Thread { done, .. } => {
                if *done {
                    "Thread(done)".to_string()
                } else {
                    "Thread(running)".to_string()
                }
            }
            HeapObject::Mutex { .. } => "Mutex".to_string(),
            HeapObject::Semaphore { .. } => "Semaphore".to_string(),
            HeapObject::Process { exit_code, cmd, .. } => match exit_code {
                Some(c) => format!("Process({} exit={})", cmd.as_deref().unwrap_or("?"), c),
                None => format!("Process({} running)", cmd.as_deref().unwrap_or("?")),
            },
            HeapObject::ProcessStream { kind, .. } => match kind {
                0 => "process.stdin".to_string(),
                1 => "process.stdout".to_string(),
                _ => "process.stderr".to_string(),
            },
            HeapObject::Regex { re } => format!("Regex({})", re.as_str()),
            HeapObject::Stream { kind } => match kind {
                0 => "stdin".to_string(),
                1 => "stdout".to_string(),
                _ => "stderr".to_string(),
            },
        }
    }
}

fn value_repr(v: &Value) -> String {
    match v {
        Value::Null => "null".to_string(),
        Value::Boolean(b) => b.to_string(),
        Value::Byte(i) => i.to_string(),
        Value::Short(i) => i.to_string(),
        Value::Integer(i) => i.to_string(),
        Value::Long(i) => i.to_string(),
        Value::Float(f) => f.to_string(),
        Value::Double(f) => f.to_string(),
        Value::Char(c) => format!("'{}'", c),
        Value::Object(_) => "<object>".to_string(),
    }
}

fn mark_value(v: &Value, marked: &mut HashSet<GcRef>, queue: &mut Vec<GcRef>) {
    if let Value::Object(r) = v {
        if marked.insert(*r) {
            queue.push(*r);
        }
    }
}

/// The managed heap.
pub struct Heap {
    objects: Vec<Option<HeapObject>>,
    free: Vec<u32>,
    /// Allocation counter used to trigger GC.
    allocs_since_gc: usize,
    /// Per-class static field storage (one slot vector per class), shared
    /// by all instances and all threads. Static slots are GC roots.
    pub statics: Vec<Vec<Value>>,
}

impl Default for Heap {
    fn default() -> Self {
        Self::new()
    }
}

impl Heap {
    pub fn new() -> Self {
        Heap {
            objects: vec![None],
            free: vec![],
            allocs_since_gc: 0,
            statics: vec![],
        }
    }

    /// Allocate a new object; returns its handle.
    pub fn alloc(&mut self, obj: HeapObject) -> GcRef {
        self.allocs_since_gc += 1;
        if let Some(ref_) = self.free.pop() {
            self.objects[ref_ as usize] = Some(obj);
            return ref_;
        }
        let ref_ = self.objects.len() as GcRef;
        self.objects.push(Some(obj));
        ref_
    }

    pub fn get(&self, ref_: GcRef) -> Option<&HeapObject> {
        self.objects.get(ref_ as usize)?.as_ref()
    }

    pub fn get_mut(&mut self, ref_: GcRef) -> Option<&mut HeapObject> {
        self.objects.get_mut(ref_ as usize)?.as_mut()
    }

    /// Free an object (used by GC and explicit deallocation).
    pub fn free(&mut self, ref_: GcRef) {
        if ref_ == 0 {
            return;
        }
        if self.objects.get(ref_ as usize).is_some_and(|o| o.is_some()) {
            self.objects[ref_ as usize] = None;
            self.free.push(ref_);
        }
    }

    /// Tracing mark-and-sweep. `roots` are the live values.
    pub fn collect(&mut self, roots: &[Value]) {
        // Worklist traversal over the object graph (linear in the number of
        // reachable objects).
        let mut marked: HashSet<GcRef> = HashSet::new();
        let mut queue: Vec<GcRef> = Vec::new();
        for r in roots {
            mark_value(r, &mut marked, &mut queue);
        }
        while let Some(r) = queue.pop() {
            if let Some(obj) = self.get(r) {
                obj.mark_into(&mut marked, &mut queue);
            }
        }
        // Sweep.
        let mut dead = Vec::new();
        for (i, slot) in self.objects.iter().enumerate() {
            if slot.is_some() && !marked.contains(&(i as GcRef)) {
                dead.push(i as GcRef);
            }
        }
        for r in dead {
            self.free(r);
        }
        self.allocs_since_gc = 0;
    }

    /// True when a collection is due.
    pub fn gc_due(&self) -> bool {
        self.allocs_since_gc > 4096
    }

    /// Number of live objects.
    #[allow(dead_code)]
    pub fn live_count(&self) -> usize {
        self.objects.iter().filter(|s| s.is_some()).count()
    }

    /// Display string for an object handle (for `toString`).
    pub fn object_string(&self, ref_: GcRef) -> String {
        match self.get(ref_) {
            Some(o) => o.string(),
            None => "<freed>".to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gc_collects_unreachable_cycle() {
        let mut heap = Heap::new();
        // Two instances that reference each other (a cycle), plus one live
        // root that does not reference the cycle.
        let a = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);
        heap.get_mut(b).unwrap().fields_mut().unwrap()[0] = Value::Object(a);
        let c = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![],
        });

        // Only C is rooted; the A<->B cycle is unreachable.
        heap.collect(&[Value::Object(c)]);

        assert!(heap.get(a).is_none(), "cycle member A should be collected");
        assert!(heap.get(b).is_none(), "cycle member B should be collected");
        assert!(heap.get(c).is_some(), "rooted object C must survive");
    }

    #[test]
    fn gc_preserves_reachable_objects() {
        let mut heap = Heap::new();
        let a = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);

        // Root A; B is reachable through A.
        heap.collect(&[Value::Object(a)]);

        assert!(heap.get(a).is_some());
        assert!(
            heap.get(b).is_some(),
            "B is reachable via A and must survive"
        );
    }
}
