//! Managed heap: atomic reference counting with bounded cycle collection.
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
//! - Cycle-collector graph snapshots run only when the program has a single
//!   active thread, so the heap-outer and collection-outer orders never
//!   interleave on live threads. Atomic retain/release remains thread-safe.

use std::collections::{HashMap, HashSet, VecDeque};
use std::io::Read;
use std::process::{Child, ChildStdin};
use std::sync::atomic::{AtomicU8, AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::JoinHandle;

use super::value::Value;

/// A heap handle.
pub type GcRef = u32;

const LIVE: u8 = 0;
const RECLAIMING: u8 = 1;

/// Runtime counters used by tests, diagnostics, and the benchmark harness.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct HeapStats {
    pub allocations: usize,
    pub frees: usize,
    pub arc_frees: usize,
    pub cycle_frees: usize,
    pub cycle_candidates: usize,
    pub cycle_runs: usize,
    pub live_objects: usize,
    pub shard_count: usize,
}

#[derive(Debug, Default)]
struct Shard {
    free: Vec<GcRef>,
    allocations: usize,
    frees: usize,
}

/// Smallest accepted cycle-collection budget (`-X1MB`).
pub const MIN_GC_BUDGET_BYTES: usize = 1024 * 1024;

/// Upper bound on the ergonomic default budget, so the candidate backlog
/// cannot grow without limit on very large machines.
pub const MAX_GC_BUDGET_BYTES: usize = 32 * 1024 * 1024 * 1024;

/// Physical memory size in bytes when the host can report it.
fn physical_memory_bytes() -> Option<u64> {
    let text = std::fs::read_to_string("/proc/meminfo").ok()?;
    for line in text.lines() {
        if let Some(rest) = line.strip_prefix("MemTotal:") {
            let kib: u64 = rest.split_whitespace().next()?.parse().ok()?;
            return kib.checked_mul(1024);
        }
    }
    None
}

/// Ergonomic default cycle-collection budget: one quarter of physical memory,
/// clamped to [`MIN_GC_BUDGET_BYTES`]`..=`[`MAX_GC_BUDGET_BYTES`], mirroring
/// how the JVM chooses its default maximum heap. Falls back to 512 MiB when
/// the host memory size cannot be read. The value is computed once.
pub fn default_gc_budget_bytes() -> usize {
    static CACHE: std::sync::OnceLock<usize> = std::sync::OnceLock::new();
    *CACHE.get_or_init(|| {
        let total = physical_memory_bytes().unwrap_or(512 * 1024 * 1024);
        (total / 4).clamp(MIN_GC_BUDGET_BYTES as u64, MAX_GC_BUDGET_BYTES as u64) as usize
    })
}

/// A slot is stable while the handle is live. The atomic count is deliberately
/// independent of the heap mutex: the last-release decision remains safe if a
/// future allocator lets release operations arrive from different threads.
struct HeapSlot {
    object: HeapObject,
    strong: AtomicUsize,
    state: AtomicU8,
    shard: usize,
    /// Set while this handle is waiting in the cycle-candidate queue. A live
    /// object is decremented on every borrow/release, so without this flag it
    /// would be re-queued on every operation and the collector would rescan
    /// its whole subgraph repeatedly. Always touched under the heap lock.
    cycle_queued: bool,
}

// ---------------------------------------------------------------------------
// Per-collection state (each guarded by its own mutex)
// ---------------------------------------------------------------------------

/// Contiguous vector storage: amortized O(1) append, O(1) indexed access.
#[derive(Debug, Default)]
pub struct ListData {
    pub items: Vec<Value>,
    /// True once an object value has ever been stored. A list that has only
    /// ever held primitives cannot lie on a reference cycle. Set-only, so it
    /// is conservative after removals and never needs clearing.
    pub seen_object: bool,
}

/// Hash-indexed entries: `buckets` maps a value hash to the entries whose
/// key hashes to it; equality is verified at lookup time so hash collisions
/// stay correct. Expected O(1) get/put/remove/contains. Retrieval order is
/// unspecified (hash layout); programs must not depend on it.
#[derive(Debug)]
pub struct MapData {
    pub buckets: HashMap<i64, Vec<(Value, Value)>, FnvBuildHasher>,
    pub len: usize,
    /// See `ListData::seen_object`.
    pub seen_object: bool,
}

impl Default for MapData {
    fn default() -> Self {
        MapData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
            seen_object: false,
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
    /// See `ListData::seen_object`.
    pub seen_object: bool,
}

impl StackData {
    pub fn with_capacity(capacity: usize) -> Self {
        StackData {
            items: VecDeque::with_capacity(capacity),
            seen_object: false,
        }
    }
}

/// Hash-indexed unique elements. Membership is unordered; expected O(1)
/// add/remove/contains.
#[derive(Debug)]
pub struct SetData {
    pub buckets: HashMap<i64, Vec<Value>, FnvBuildHasher>,
    pub len: usize,
    /// See `ListData::seen_object`.
    pub seen_object: bool,
}

impl Default for SetData {
    fn default() -> Self {
        SetData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
            seen_object: false,
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
        struct_id: u16,
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
                seen_object: false,
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

    /// Append this object's outgoing object references to `out` without
    /// allocating a temporary.
    ///
    /// Only object values are appended. Primitives can never be children in
    /// the reference graph, and appending them would force every visited
    /// instance to allocate (the quadratic cost this method exists to avoid).
    /// Collection payloads are independently locked, so each snapshot is
    /// short-lived and never exposes a collection guard to the caller.
    fn references_into(&self, out: &mut Vec<Value>) {
        match self {
            HeapObject::Instance { fields, .. } => {
                out.extend(
                    fields
                        .iter()
                        .filter(|v| matches!(v, Value::Object(_)))
                        .copied(),
                );
            }
            HeapObject::List { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                if g.seen_object {
                    out.extend(
                        g.items
                            .iter()
                            .filter(|v| matches!(v, Value::Object(_)))
                            .copied(),
                    );
                }
            }
            HeapObject::Stack { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                if g.seen_object {
                    out.extend(
                        g.items
                            .iter()
                            .filter(|v| matches!(v, Value::Object(_)))
                            .copied(),
                    );
                }
            }
            HeapObject::Set { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                if g.seen_object {
                    for bucket in g.buckets.values() {
                        out.extend(
                            bucket
                                .iter()
                                .filter(|v| matches!(v, Value::Object(_)))
                                .copied(),
                        );
                    }
                }
            }
            HeapObject::Map { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                if g.seen_object {
                    for bucket in g.buckets.values() {
                        for (k, v) in bucket.iter() {
                            if matches!(k, Value::Object(_)) {
                                out.push(*k);
                            }
                            if matches!(v, Value::Object(_)) {
                                out.push(*v);
                            }
                        }
                    }
                }
            }
            HeapObject::Enum {
                payload: Some(p), ..
            } => {
                if matches!(p, Value::Object(_)) {
                    out.push(*p);
                }
            }
            HeapObject::Thread {
                runnable: Some(r), ..
            } => out.push(Value::Object(*r)),
            HeapObject::ProcessStream { process, .. } => out.push(Value::Object(*process)),
            _ => {}
        }
    }

    /// Snapshot of all strong references stored by this object (used by the
    /// test-only tracing collector).
    fn references(&self) -> Vec<Value> {
        let mut out = Vec::new();
        self.references_into(&mut out);
        out
    }

    /// Whether this object can hold a reference to another heap object.
    ///
    /// An object with no outgoing object references cannot lie on a reference
    /// cycle, so it never needs to enter the trial-deletion candidate queue.
    /// This runs while the caller holds the heap lock, so it must not block on
    /// any collection lock (collection operations take the heap lock while
    /// holding their own lock, and the reverse order would deadlock). It uses
    /// a non-blocking `try_lock`: if a collection is busy, it is conservatively
    /// treated as reference-holding. Instance and enum scans are bounded by
    /// the declaration's field count. Collections report whether an object
    /// value has ever been stored; an empty or primitive-only collection is
    /// provably cycle-free.
    fn has_references(&self) -> bool {
        match self {
            HeapObject::Instance { fields, .. } => {
                fields.iter().any(|v| matches!(v, Value::Object(_)))
            }
            HeapObject::Enum { payload, .. } => matches!(payload, Some(Value::Object(_))),
            HeapObject::List { data } => data.try_lock().map_or(true, |g| g.seen_object),
            HeapObject::Map { data } => data.try_lock().map_or(true, |g| g.seen_object),
            HeapObject::Stack { data } => data.try_lock().map_or(true, |g| g.seen_object),
            HeapObject::Set { data } => data.try_lock().map_or(true, |g| g.seen_object),
            HeapObject::Thread { .. } | HeapObject::ProcessStream { .. } => true,
            _ => false,
        }
    }

    /// Values reachable from this object (for the compatibility tracing
    /// collector). Newly marked handles are appended to `queue` for worklist
    /// traversal.
    fn mark_into(&self, marked: &mut HashSet<GcRef>, queue: &mut Vec<GcRef>) {
        for value in self.references() {
            mark_value(&value, marked, queue);
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
            HeapObject::Instance { struct_id, .. } => format!("<instance #{}>", struct_id),
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
    objects: Vec<Option<HeapSlot>>,
    shards: Vec<Shard>,
    allocs_since_maintenance: usize,
    candidates: VecDeque<GcRef>,
    /// Byte budget controlling how much deferred cycle-collection work may
    /// accumulate before the collector runs. See `DEFAULT_GC_BUDGET_BYTES`.
    gc_budget_bytes: usize,
    stats: HeapStats,
    /// Per-struct static field storage (one slot vector per struct), shared
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
        let shard_count = std::env::var("SOLVIK_HEAP_SHARDS")
            .ok()
            .and_then(|s| s.parse::<usize>().ok())
            .filter(|n| *n > 0)
            .unwrap_or_else(|| {
                std::thread::available_parallelism()
                    .map(|n| n.get().min(8))
                    .unwrap_or(1)
            });
        Self::with_shards(shard_count)
    }

    /// Construct a heap with an explicit cycle-collection budget in bytes.
    /// The budget is clamped to `MIN_GC_BUDGET_BYTES`.
    pub fn with_budget(budget_bytes: usize) -> Self {
        let mut heap = Self::new();
        heap.gc_budget_bytes = budget_bytes.max(MIN_GC_BUDGET_BYTES);
        heap
    }

    /// Override the cycle-collection budget without clamping. Used by tests
    /// and embedding code that needs to force maintenance timing; the CLI and
    /// packaged runtime route through `with_budget`.
    pub fn set_gc_budget(&mut self, budget_bytes: usize) {
        self.gc_budget_bytes = budget_bytes;
    }

    /// Construct a heap with an explicit allocator-shard count. This is used
    /// by deterministic tests and benchmarks; a count of one is always valid.
    pub fn with_shards(shard_count: usize) -> Self {
        let shard_count = shard_count.max(1);
        Heap {
            objects: vec![None],
            shards: (0..shard_count).map(|_| Shard::default()).collect(),
            allocs_since_maintenance: 0,
            candidates: VecDeque::new(),
            gc_budget_bytes: default_gc_budget_bytes(),
            stats: HeapStats {
                shard_count,
                ..HeapStats::default()
            },
            statics: vec![],
        }
    }

    /// Allocate a new object; the returned handle owns one strong reference.
    /// The allocation is assigned to a preferred shard selected from the
    /// current thread. Shards are allocator bookkeeping only; references can
    /// cross them freely.
    pub fn alloc(&mut self, obj: HeapObject) -> GcRef {
        let shard = self.preferred_shard();
        let ref_ = if let Some(ref_) = self.shards[shard].free.pop() {
            self.objects[ref_ as usize] = Some(HeapSlot {
                object: obj,
                strong: AtomicUsize::new(1),
                state: AtomicU8::new(LIVE),
                shard,
                cycle_queued: false,
            });
            ref_
        } else {
            let ref_ = self.objects.len() as GcRef;
            self.objects.push(Some(HeapSlot {
                object: obj,
                strong: AtomicUsize::new(1),
                state: AtomicU8::new(LIVE),
                shard,
                cycle_queued: false,
            }));
            ref_
        };
        // The newly returned strong reference is the caller's ownership. The
        // object's stored edges are additional ownerships.
        let children = self
            .objects
            .get(ref_ as usize)
            .and_then(|slot| slot.as_ref())
            .map(|slot| slot.object.references())
            .unwrap_or_default();
        for value in children {
            self.retain_value(value);
        }
        self.shards[shard].allocations += 1;
        self.stats.allocations += 1;
        self.stats.live_objects += 1;
        self.allocs_since_maintenance += 1;
        ref_
    }

    pub fn get(&self, ref_: GcRef) -> Option<&HeapObject> {
        self.objects
            .get(ref_ as usize)?
            .as_ref()
            .filter(|slot| slot.state.load(Ordering::Acquire) == LIVE)
            .map(|slot| &slot.object)
    }

    pub fn get_mut(&mut self, ref_: GcRef) -> Option<&mut HeapObject> {
        self.objects
            .get_mut(ref_ as usize)?
            .as_mut()
            .filter(|slot| slot.state.load(Ordering::Acquire) == LIVE)
            .map(|slot| &mut slot.object)
    }

    /// Increment a strong reference. Returns false for an invalid or already
    /// reclaiming handle; a caller must never revive such a handle.
    pub fn retain(&self, ref_: GcRef) -> bool {
        let Some(Some(slot)) = self.objects.get(ref_ as usize) else {
            return false;
        };
        if slot.state.load(Ordering::Acquire) != LIVE {
            return false;
        }
        let result = slot
            .strong
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                count.checked_add(1)
            });
        if result.is_err() {
            panic!("Solvik reference-count overflow for handle {}", ref_);
        }
        true
    }

    pub fn retain_value(&self, value: Value) {
        if let Value::Object(ref_) = value {
            if ref_ != 0 {
                assert!(self.retain(ref_), "retaining an invalid Solvik handle");
            }
        }
    }

    /// Release one strong reference. A zero transition takes the sole
    /// reclamation winner and drains child releases iteratively, so a long
    /// object chain cannot overflow the native stack.
    pub fn release(&mut self, ref_: GcRef) {
        let Some(Some(slot)) = self.objects.get(ref_ as usize) else {
            return;
        };
        let previous = slot
            .strong
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                if count == 0 {
                    None
                } else {
                    Some(count - 1)
                }
            });
        let Ok(previous) = previous else {
            debug_assert!(
                false,
                "Solvik reference-count underflow for handle {}",
                ref_
            );
            return;
        };
        // An object with no outgoing object references cannot be on a cycle,
        // and an object already waiting in the candidate queue is not queued
        // again: a live object is borrowed and released on every operation, so
        // re-queuing it would rescan its whole subgraph per operation. The
        // last reference (previous == 1) is always queued so zero-count slots
        // are reclaimed by the worklist below.
        let should_queue = previous != 1 && !slot.cycle_queued && slot.object.has_references();
        if previous == 1 {
            self.candidates.push_back(ref_);
            self.reclaim_zero_worklist(false);
        } else if should_queue {
            if let Some(Some(slot)) = self.objects.get_mut(ref_ as usize) {
                slot.cycle_queued = true;
            }
            self.candidates.push_back(ref_);
        }
    }

    pub fn release_value(&mut self, value: Value) {
        if let Value::Object(ref_) = value {
            if ref_ == 0 {
                return;
            }
            self.release(ref_);
        }
    }

    /// Force-free a slot for the legacy explicit tracing test hook. Normal VM
    /// code uses `release`; this method is intentionally not an exposed
    /// language operation.
    pub fn free(&mut self, ref_: GcRef) {
        if ref_ == 0 {
            return;
        }
        if let Some(slot) = self.take_slot(ref_) {
            self.shards[slot.shard].free.push(ref_);
            self.shards[slot.shard].frees += 1;
            self.stats.frees += 1;
            self.stats.live_objects = self.stats.live_objects.saturating_sub(1);
        }
    }

    fn take_slot(&mut self, ref_: GcRef) -> Option<HeapSlot> {
        let slot = self.objects.get_mut(ref_ as usize)?.take()?;
        slot.state.store(RECLAIMING, Ordering::Release);
        Some(slot)
    }

    fn reclaim_zero_worklist(&mut self, cycle: bool) {
        let mut work = VecDeque::new();
        while let Some(ref_) = self.candidates.pop_front() {
            let zero = self
                .objects
                .get(ref_ as usize)
                .and_then(|slot| slot.as_ref())
                .is_some_and(|slot| slot.strong.load(Ordering::Acquire) == 0);
            // Dropped from the queue, so clear the dedup mark.
            if let Some(Some(slot)) = self.objects.get_mut(ref_ as usize) {
                slot.cycle_queued = false;
            }
            if zero {
                work.push_back(ref_);
            }
        }
        while let Some(ref_) = work.pop_front() {
            let Some(slot) = self.take_slot(ref_) else {
                continue;
            };
            let children = slot.object.references();
            self.shards[slot.shard].free.push(ref_);
            self.shards[slot.shard].frees += 1;
            self.stats.frees += 1;
            self.stats.arc_frees += usize::from(!cycle);
            self.stats.cycle_frees += usize::from(cycle);
            self.stats.live_objects = self.stats.live_objects.saturating_sub(1);
            for value in children {
                let Value::Object(child) = value else {
                    continue;
                };
                let Some(Some(child_slot)) = self.objects.get(child as usize) else {
                    continue;
                };
                // Trial deletion marks every member of an unreachable cycle
                // zero before the worklist runs. Internal cycle edges are
                // already accounted for by that batch and must not underflow
                // when the individual slots are removed.
                if child_slot.strong.load(Ordering::Acquire) == 0 {
                    continue;
                }
                let Ok(previous) =
                    child_slot
                        .strong
                        .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                            count.checked_sub(1)
                        })
                else {
                    debug_assert!(false, "child reference-count underflow");
                    continue;
                };
                if previous == 1 {
                    work.push_back(child);
                }
            }
        }
    }

    fn preferred_shard(&self) -> usize {
        use std::hash::{Hash, Hasher};
        let mut hasher = std::collections::hash_map::DefaultHasher::new();
        std::thread::current().id().hash(&mut hasher);
        (hasher.finish() as usize) % self.shards.len().max(1)
    }

    /// Compatibility tracing hook used by the existing unit tests and for
    /// explicit debug diagnostics. Runtime maintenance uses ARC and the
    /// cycle collector below; ordinary VM execution does not scan the heap.
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
        self.allocs_since_maintenance = 0;
    }

    /// Run bounded trial-deletion cycle collection. The heap mutex is the
    /// coordination point for this initial implementation; VM callers invoke
    /// it only when no other Solvik thread is executing. The logical graph is
    /// not partitioned by allocator shard.
    pub fn collect_cycles(&mut self, budget: usize) -> usize {
        let mut candidates = HashSet::new();
        while candidates.len() < budget {
            let Some(r) = self.candidates.pop_front() else {
                break;
            };
            // Leaving the queue clears the dedup mark so a later decrement can
            // queue the object again.
            if let Some(Some(slot)) = self.objects.get_mut(r as usize) {
                slot.cycle_queued = false;
                if slot.state.load(Ordering::Acquire) == LIVE {
                    candidates.insert(r);
                }
            }
        }
        if candidates.is_empty() {
            return 0;
        }
        self.stats.cycle_runs += 1;
        let mut graph = candidates.clone();
        let mut queue: VecDeque<GcRef> = candidates.iter().copied().collect();
        // Reused across every traversal so a live collection is not
        // re-allocated once per child per pass.
        let mut children: Vec<Value> = Vec::new();
        while let Some(r) = queue.pop_front() {
            children.clear();
            if let Some(obj) = self.get(r) {
                obj.references_into(&mut children);
            }
            for child in children.iter().filter_map(|v| v.as_object()) {
                if graph.insert(child) {
                    queue.push_back(child);
                }
            }
        }
        let mut trial: HashMap<GcRef, usize> = graph
            .iter()
            .filter_map(|r| {
                self.objects
                    .get(*r as usize)
                    .and_then(|slot| slot.as_ref())
                    .map(|slot| (*r, slot.strong.load(Ordering::Acquire)))
            })
            .collect();
        for r in &graph {
            children.clear();
            if let Some(obj) = self.get(*r) {
                obj.references_into(&mut children);
            }
            for child in children.iter().filter_map(|v| v.as_object()) {
                if let Some(count) = trial.get_mut(&child) {
                    *count = count.saturating_sub(1);
                }
            }
        }
        // A candidate with an external owner keeps its entire reachable
        // subgraph alive. Trial counts identify the externally-rooted entry
        // points; they are not a per-node liveness decision by themselves.
        let mut live = HashSet::new();
        let mut live_queue: VecDeque<GcRef> = trial
            .iter()
            .filter_map(|(r, count)| (*count > 0).then_some(*r))
            .collect();
        while let Some(r) = live_queue.pop_front() {
            if !live.insert(r) {
                continue;
            }
            children.clear();
            if let Some(obj) = self.get(r) {
                obj.references_into(&mut children);
            }
            for child in children.iter().filter_map(|v| v.as_object()) {
                if graph.contains(&child) && !live.contains(&child) {
                    live_queue.push_back(child);
                }
            }
        }
        let dead: HashSet<GcRef> = graph.difference(&live).copied().collect();
        self.stats.cycle_candidates += graph.len();
        for r in &dead {
            if let Some(Some(slot)) = self.objects.get_mut(*r as usize) {
                slot.strong.store(0, Ordering::Release);
                slot.cycle_queued = true;
            }
            self.candidates.push_back(*r);
        }
        self.reclaim_zero_worklist(true);
        self.allocs_since_maintenance = 0;
        dead.len()
    }

    /// True when bounded maintenance work is due: the candidate backlog or
    /// the allocation backlog has reached roughly the configured byte budget.
    pub fn maintenance_due(&self) -> bool {
        let candidate_limit = self.gc_budget_bytes / std::mem::size_of::<GcRef>();
        let alloc_limit = self.gc_budget_bytes / 64;
        self.candidates.len() > candidate_limit || self.allocs_since_maintenance > alloc_limit
    }

    /// Backwards-compatible name for callers that used the tracing GC hint.
    pub fn gc_due(&self) -> bool {
        self.maintenance_due()
    }

    /// Number of live objects.
    #[allow(dead_code)]
    pub fn live_count(&self) -> usize {
        self.objects.iter().filter(|s| s.is_some()).count()
    }

    pub fn stats(&self) -> HeapStats {
        HeapStats {
            live_objects: self.live_count(),
            ..self.stats
        }
    }

    pub fn shard_count(&self) -> usize {
        self.shards.len()
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
            struct_id: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);
        heap.get_mut(b).unwrap().fields_mut().unwrap()[0] = Value::Object(a);
        let c = heap.alloc(HeapObject::Instance {
            struct_id: 0,
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
            struct_id: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            struct_id: 0,
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

    #[test]
    fn arc_reclaims_an_unreachable_cycle() {
        let mut heap = Heap::new();
        let a = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);
        heap.get_mut(b).unwrap().fields_mut().unwrap()[0] = Value::Object(a);
        heap.retain_value(Value::Object(a));
        heap.retain_value(Value::Object(b));
        // The two field stores above are the graph edges; release the two
        // external construction handles and leave only the cycle.
        heap.release(a);
        heap.release(b);
        assert_eq!(heap.collect_cycles(16), 2);
        assert!(heap.get(a).is_none());
        assert!(heap.get(b).is_none());
    }

    #[test]
    fn release_skips_cycle_candidates_without_object_references() {
        let mut heap = Heap::new();
        let a = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Long(1), Value::Boolean(true)],
        });
        // Two strong references, then one release: the object stays live. It
        // has no outgoing object references, so it cannot be on a cycle and
        // must not be queued for trial deletion.
        heap.retain_value(Value::Object(a));
        heap.release(a);
        assert!(
            heap.candidates.is_empty(),
            "reference-free object must not be a cycle candidate"
        );
        assert!(heap.get(a).is_some(), "object is still live");
    }

    #[test]
    fn release_enqueues_cycle_candidates_with_object_references() {
        let mut heap = Heap::new();
        let child = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![],
        });
        let parent = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Object(child)],
        });
        heap.retain_value(Value::Object(parent));
        heap.release(parent);
        assert!(
            heap.candidates.iter().any(|r| *r == parent),
            "object with an outgoing reference is a cycle candidate"
        );
    }

    #[test]
    fn primitive_collection_is_not_a_cycle_candidate() {
        let mut heap = Heap::new();
        let l = heap.alloc(HeapObject::list());
        if let Some(HeapObject::List { data }) = heap.get(l) {
            data.lock().unwrap().items.push(Value::Long(1));
        }
        // Two strong references, then a release: the list stays live and has
        // never held an object, so it cannot be on a cycle.
        heap.retain_value(Value::Object(l));
        heap.release(l);
        assert!(
            heap.candidates.is_empty(),
            "primitive-only list must not be a cycle candidate"
        );
    }

    #[test]
    fn arc_reclaims_an_unreachable_collection_cycle() {
        let mut heap = Heap::new();
        // A List that contains itself. The self edge is an uncounted reference
        // in this fixture; the extra retain models the edge's ownership.
        let l = heap.alloc(HeapObject::list());
        if let Some(HeapObject::List { data }) = heap.get(l) {
            let mut g = data.lock().unwrap();
            g.items.push(Value::Object(l));
            g.seen_object = true;
        }
        heap.retain_value(Value::Object(l));
        heap.release(l);
        assert_eq!(heap.collect_cycles(16), 1);
        assert!(
            heap.get(l).is_none(),
            "self-referential list must be reclaimed"
        );
    }

    #[test]
    fn release_queues_a_live_object_once() {
        let mut heap = Heap::new();
        let child = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![],
        });
        let parent = heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![Value::Object(child)],
        });
        heap.retain_value(Value::Object(parent));
        heap.retain_value(Value::Object(parent));
        heap.release(parent);
        heap.release(parent);
        assert_eq!(
            heap.candidates.iter().filter(|r| **r == parent).count(),
            1,
            "a live object must not be queued more than once"
        );
    }

    #[test]
    fn maintenance_due_tracks_budget() {
        let mut heap = Heap::new();
        heap.set_gc_budget(0);
        assert!(!heap.maintenance_due());
        heap.alloc(HeapObject::Instance {
            struct_id: 0,
            fields: vec![],
        });
        assert!(heap.maintenance_due());
    }

    #[test]
    fn arc_release_worklist_handles_a_deep_chain() {
        let mut heap = Heap::new();
        let mut refs = Vec::with_capacity(20_000);
        for _ in 0..20_000 {
            refs.push(heap.alloc(HeapObject::Instance {
                struct_id: 0,
                fields: vec![Value::Null],
            }));
        }
        for pair in refs.windows(2) {
            heap.get_mut(pair[0]).unwrap().fields_mut().unwrap()[0] = Value::Object(pair[1]);
            heap.retain(pair[1]);
        }
        for r in refs.iter().copied() {
            heap.release(r);
        }
        assert_eq!(heap.live_count(), 0);
        assert!(heap.stats().arc_frees >= 20_000);
    }
}
