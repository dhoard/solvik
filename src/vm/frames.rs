//! Call frames and try-region bookkeeping.//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]

/// One active call frame. Locals live in the shared value stack at `base`.
#[derive(Debug)]
pub struct CallFrame {
    /// Function id in the module.
    pub fid: u32,
    /// Next instruction offset to execute.
    pub ip: u32,
    /// Stack index of the first local slot.
    pub base: usize,
    /// Number of arguments (for stack cleanup on return).
    pub args_count: u16,
    /// When set (inherited constructor call), NewObject allocates this
    /// class instead of the one named in the instruction.
    pub construct_as: Option<u16>,
}

/// An active `try` region.
#[derive(Debug)]
pub struct TryRegion {
    /// Number of active frames when this region began.
    pub frame_depth: usize,
    /// Offset of the catch handler (pops the exception value).
    pub catch_ip: u32,
    /// Offset of the finally handler (no exception on stack), or None.
    pub finally_ip: Option<u32>,
    /// Stack height when the region began.
    pub stack_base: usize,
}
