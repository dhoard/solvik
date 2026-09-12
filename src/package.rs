//! Self-contained executable package format ("SOLVPKG").
//!
//! A Solvik package is a native `solvik-runtime` executable image with an
//! encoded Solvik bytecode payload and a fixed-size footer appended at EOF:
//!
//! ```text
//! +--------------------------------+
//! | native Solvik runtime image    |
//! +--------------------------------+
//! | encoded Solvik bytecode        |
//! +--------------------------------+
//! | fixed-size package footer      |
//! +--------------------------------+
//! EOF
//! ```
//!
//! The payload is located exclusively through the footer at EOF; the loader
//! MUST NOT scan the binary for the bytecode magic (`SOLV`), because a valid
//! native executable may independently contain those bytes. See `PACKAGE.md`
//! for the normative specification.

use std::fmt;
use std::fs;
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

/// Package container magic: `SOLVPKG\0`. Deliberately distinct from the
/// Solvik bytecode magic (`SOLV`).
pub const MAGIC: &[u8; 8] = b"SOLVPKG\0";

/// Current package format version. This is an independent version domain:
/// it is NOT the Solvik bytecode version (`CodeModule::FORMAT_VERSION`).
pub const FORMAT_VERSION: u32 = 1;

/// Fixed footer size in bytes: magic (8) + format_version (4) +
/// payload_length (8) + payload_sha256 (32).
pub const FOOTER_LEN: usize = 52;

/// Environment variable overriding runtime image discovery.
pub const RUNTIME_ENV: &str = "SOLVIK_RUNTIME";

/// Base name of the runtime binary (platform suffix added by callers).
pub const RUNTIME_NAME: &str = "solvik-runtime";

/// Structured errors for packaging and package loading.
#[derive(Debug)]
pub enum PackageError {
    /// I/O failure while reading or writing `path`.
    Io { path: PathBuf, source: io::Error },
    /// `path` exists but is not a regular file.
    NotRegularFile(PathBuf),
    /// `path` is shorter than the fixed footer.
    TooShort { path: PathBuf, len: u64 },
    /// The footer magic does not match; not a Solvik package.
    BadMagic(PathBuf),
    /// The footer declares an unsupported package format version.
    UnsupportedVersion { path: PathBuf, version: u32 },
    /// The declared payload length exceeds the bytes preceding the footer.
    BadPayloadLength {
        path: PathBuf,
        payload_length: u64,
        available: u64,
    },
    /// The payload's SHA-256 does not match the footer hash.
    CorruptPayload(PathBuf),
    /// The payload decoded but is not acceptable Solvik bytecode.
    InvalidBytecode { path: PathBuf, message: String },
    /// The output destination already exists.
    AlreadyExists(PathBuf),
    /// No usable runtime image could be found.
    RuntimeNotFound { considered: Vec<PathBuf> },
}

impl fmt::Display for PackageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            PackageError::Io { path, source } => {
                write!(f, "I/O error on {}: {}", path.display(), source)
            }
            PackageError::NotRegularFile(path) => {
                write!(f, "{} is not a regular file", path.display())
            }
            PackageError::TooShort { path, len } => write!(
                f,
                "{} is too short to be a Solvik package ({} bytes, footer needs {})",
                path.display(),
                len,
                FOOTER_LEN
            ),
            PackageError::BadMagic(path) => {
                write!(f, "{} is not a Solvik package (bad magic)", path.display())
            }
            PackageError::UnsupportedVersion { path, version } => write!(
                f,
                "{} uses unsupported Solvik package version {} (supported: {})",
                path.display(),
                version,
                FORMAT_VERSION
            ),
            PackageError::BadPayloadLength {
                path,
                payload_length,
                available,
            } => write!(
                f,
                "{} has impossible payload length {} (only {} bytes precede the footer)",
                path.display(),
                payload_length,
                available
            ),
            PackageError::CorruptPayload(path) => {
                write!(
                    f,
                    "{} payload hash mismatch (corrupted package)",
                    path.display()
                )
            }
            PackageError::InvalidBytecode { path, message } => {
                write!(
                    f,
                    "{} contains invalid Solvik bytecode: {}",
                    path.display(),
                    message
                )
            }
            PackageError::AlreadyExists(path) => {
                write!(
                    f,
                    "{} already exists; refusing to overwrite",
                    path.display()
                )
            }
            PackageError::RuntimeNotFound { considered } => {
                write!(f, "Solvik runtime image not found")?;
                if !considered.is_empty() {
                    write!(f, " (considered:")?;
                    for p in considered {
                        write!(f, " {}", p.display())?;
                    }
                    write!(f, ")")?;
                }
                write!(
                    f,
                    " (set {} to point at a solvik-runtime binary)",
                    RUNTIME_ENV
                )
            }
        }
    }
}

impl std::error::Error for PackageError {}

fn io_err(path: &Path, source: io::Error) -> PackageError {
    PackageError::Io {
        path: path.to_path_buf(),
        source,
    }
}

fn is_regular_file(path: &Path) -> bool {
    fs::metadata(path).map(|m| m.is_file()).unwrap_or(false)
}

/// Read a regular file into memory, with structured errors.
fn read_regular_file(path: &Path) -> Result<Vec<u8>, PackageError> {
    let meta = fs::metadata(path).map_err(|e| io_err(path, e))?;
    if !meta.is_file() {
        return Err(PackageError::NotRegularFile(path.to_path_buf()));
    }
    fs::read(path).map_err(|e| io_err(path, e))
}

/// Serialize the fixed-size footer for `payload`.
///
/// Layout (all integers little-endian):
/// `magic(8) | format_version(4) | payload_length(8) | payload_sha256(32)`.
pub fn build_footer(payload: &[u8]) -> [u8; FOOTER_LEN] {
    let mut out = [0u8; FOOTER_LEN];
    out[0..8].copy_from_slice(MAGIC);
    out[8..12].copy_from_slice(&FORMAT_VERSION.to_le_bytes());
    out[12..20].copy_from_slice(&(payload.len() as u64).to_le_bytes());
    out[20..FOOTER_LEN].copy_from_slice(&Sha256::digest(payload));
    out
}

/// Compose a self-contained executable from a prebuilt runtime image and
/// verified Solvik bytecode.
///
/// The output is written to a temporary file in the destination directory
/// and renamed into place atomically; a failed operation leaves no partial
/// output behind. Fails if `output_path` already exists.
pub fn create_package(
    runtime_path: &Path,
    output_path: &Path,
    bytecode: &[u8],
) -> Result<(), PackageError> {
    let image = read_regular_file(runtime_path)?;
    if output_path
        .try_exists()
        .map_err(|e| io_err(output_path, e))?
    {
        return Err(PackageError::AlreadyExists(output_path.to_path_buf()));
    }

    let footer = build_footer(bytecode);
    let tmp = temp_path_for(output_path);
    let write = || -> io::Result<()> {
        let mut f = fs::File::create(&tmp)?;
        f.write_all(&image)?;
        f.write_all(bytecode)?;
        f.write_all(&footer)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            // Preserve the runtime image's permission bits; fall back to a
            // plain user/rwx group/other-executable mode when it has none.
            let mode = fs::metadata(runtime_path)?.permissions().mode() & 0o7777;
            let mode = if mode & 0o111 != 0 { mode } else { 0o755 };
            fs::set_permissions(&tmp, fs::Permissions::from_mode(mode))?;
        }
        f.sync_all()
    };
    if let Err(e) = write() {
        let _ = fs::remove_file(&tmp);
        return Err(io_err(output_path, e));
    }
    if let Err(e) = fs::rename(&tmp, output_path) {
        let _ = fs::remove_file(&tmp);
        return Err(io_err(output_path, e));
    }
    Ok(())
}

/// Temporary output name in the same directory as `output_path`, so the
/// final rename is atomic on the same filesystem.
fn temp_path_for(output_path: &Path) -> PathBuf {
    let dir = output_path
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    let name = output_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("solvik-package");
    dir.join(format!(".{name}.solvik-pkg.tmp"))
}

/// Read and validate the embedded bytecode payload of a packaged executable.
///
/// Performs footer validation, bounds checking, and SHA-256 integrity
/// verification only. The caller must still decode and verify the returned
/// bytecode before executing it (see `solvik_rs::load_verified_module`).
pub fn read_embedded_bytecode(executable_path: &Path) -> Result<Vec<u8>, PackageError> {
    let meta = fs::metadata(executable_path).map_err(|e| io_err(executable_path, e))?;
    let len = meta.len();
    if len < FOOTER_LEN as u64 {
        return Err(PackageError::TooShort {
            path: executable_path.to_path_buf(),
            len,
        });
    }

    let mut f = fs::File::open(executable_path).map_err(|e| io_err(executable_path, e))?;
    f.seek(SeekFrom::Start(len - FOOTER_LEN as u64))
        .map_err(|e| io_err(executable_path, e))?;
    let mut footer = [0u8; FOOTER_LEN];
    f.read_exact(&mut footer)
        .map_err(|e| io_err(executable_path, e))?;

    if &footer[0..8] != MAGIC {
        return Err(PackageError::BadMagic(executable_path.to_path_buf()));
    }
    let version = u32::from_le_bytes(footer[8..12].try_into().unwrap());
    if version != FORMAT_VERSION {
        return Err(PackageError::UnsupportedVersion {
            path: executable_path.to_path_buf(),
            version,
        });
    }
    let payload_length = u64::from_le_bytes(footer[12..20].try_into().unwrap());

    // Checked boundary computation: payload occupies
    // [payload_start, payload_end) where payload_end = len - FOOTER_LEN.
    let payload_end = len - FOOTER_LEN as u64; // safe: len >= FOOTER_LEN
    let payload_start =
        payload_end
            .checked_sub(payload_length)
            .ok_or_else(|| PackageError::BadPayloadLength {
                path: executable_path.to_path_buf(),
                payload_length,
                available: payload_end,
            })?;

    f.seek(SeekFrom::Start(payload_start))
        .map_err(|e| io_err(executable_path, e))?;
    // Bounded by the file size by construction of payload_start/payload_end.
    let mut payload = vec![0u8; payload_length as usize];
    f.read_exact(&mut payload)
        .map_err(|e| io_err(executable_path, e))?;

    let digest = Sha256::digest(&payload);
    if digest.as_slice() != &footer[20..FOOTER_LEN] {
        return Err(PackageError::CorruptPayload(executable_path.to_path_buf()));
    }
    Ok(payload)
}

/// Locate the prebuilt runtime image used for packaging.
///
/// Search order:
/// 1. `$SOLVIK_RUNTIME` (if set, it is used exclusively; an invalid value
///    is an error rather than a fall-through);
/// 2. `solvik-runtime` beside the running `solvik` executable;
/// 3. `../libexec/solvik/solvik-runtime` relative to the running executable.
pub fn find_runtime() -> Result<PathBuf, PackageError> {
    let name = runtime_binary_name();
    let mut considered: Vec<PathBuf> = Vec::new();

    if let Ok(custom) = std::env::var(RUNTIME_ENV) {
        if !custom.is_empty() {
            let p = PathBuf::from(&custom);
            considered.push(p.clone());
            if is_regular_file(&p) {
                return Ok(p);
            }
            return Err(PackageError::RuntimeNotFound { considered });
        }
    }

    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            let beside = dir.join(name);
            considered.push(beside.clone());
            if is_regular_file(&beside) {
                return Ok(beside);
            }
            let libexec = dir.join("../libexec/solvik").join(name);
            considered.push(libexec.clone());
            if is_regular_file(&libexec) {
                return Ok(libexec);
            }
        }
    }

    Err(PackageError::RuntimeNotFound { considered })
}

fn runtime_binary_name() -> &'static str {
    if cfg!(windows) {
        "solvik-runtime.exe"
    } else {
        "solvik-runtime"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const FAKE_RUNTIME: &[u8] = b"\x7fELF fake solvik runtime image";
    const PAYLOAD: &[u8] = b"SOLV fake verified bytecode payload";

    /// A unique working directory; removed on drop so repeated runs do not
    /// accumulate directories in the system temp dir.
    struct TmpDir(PathBuf);

    impl TmpDir {
        fn new(label: &str) -> Self {
            static COUNTER: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
            let n = COUNTER.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            let dir = std::env::temp_dir().join(format!(
                "solvik-pkg-unit-{}-{}-{label}",
                std::process::id(),
                n
            ));
            fs::create_dir_all(&dir).unwrap();
            TmpDir(dir)
        }
    }

    impl std::ops::Deref for TmpDir {
        type Target = PathBuf;
        fn deref(&self) -> &PathBuf {
            &self.0
        }
    }

    impl AsRef<Path> for TmpDir {
        fn as_ref(&self) -> &Path {
            &self.0
        }
    }

    impl Drop for TmpDir {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    fn tmpdir(label: &str) -> TmpDir {
        TmpDir::new(label)
    }

    fn make_runtime(dir: &Path) -> PathBuf {
        let p = dir.join("runtime");
        fs::write(&p, FAKE_RUNTIME).unwrap();
        p
    }

    fn package_bytes(runtime: &[u8], payload: &[u8]) -> Vec<u8> {
        let mut out = Vec::with_capacity(runtime.len() + payload.len() + FOOTER_LEN);
        out.extend_from_slice(runtime);
        out.extend_from_slice(payload);
        out.extend_from_slice(&build_footer(payload));
        out
    }

    #[test]
    fn footer_layout_is_fixed_size_and_deterministic() {
        let a = build_footer(PAYLOAD);
        let b = build_footer(PAYLOAD);
        assert_eq!(a, b);
        assert_eq!(a.len(), FOOTER_LEN);
        assert_eq!(&a[0..8], MAGIC);
        assert_eq!(
            u32::from_le_bytes(a[8..12].try_into().unwrap()),
            FORMAT_VERSION
        );
        assert_eq!(
            u64::from_le_bytes(a[12..20].try_into().unwrap()),
            PAYLOAD.len() as u64
        );
        assert_eq!(&a[20..], Sha256::digest(PAYLOAD).as_slice());
    }

    #[test]
    fn package_round_trip() {
        let dir = tmpdir("round_trip");
        let runtime = make_runtime(&dir);
        let out = dir.join("app");
        create_package(&runtime, &out, PAYLOAD).unwrap();
        let data = fs::read(&out).unwrap();
        assert_eq!(data, package_bytes(FAKE_RUNTIME, PAYLOAD));
        assert_eq!(read_embedded_bytecode(&out).unwrap(), PAYLOAD);
    }

    #[test]
    fn package_determinism_same_inputs_identical_bytes() {
        let dir = tmpdir("determinism");
        let runtime = make_runtime(&dir);
        let a = dir.join("a");
        let b = dir.join("b");
        create_package(&runtime, &a, PAYLOAD).unwrap();
        create_package(&runtime, &b, PAYLOAD).unwrap();
        assert_eq!(fs::read(&a).unwrap(), fs::read(&b).unwrap());
    }

    #[test]
    fn create_package_refuses_existing_output() {
        let dir = tmpdir("exists");
        let runtime = make_runtime(&dir);
        let out = dir.join("app");
        create_package(&runtime, &out, PAYLOAD).unwrap();
        let err = create_package(&runtime, &out, PAYLOAD).unwrap_err();
        assert!(matches!(err, PackageError::AlreadyExists(_)));
        // The original package is untouched.
        assert_eq!(read_embedded_bytecode(&out).unwrap(), PAYLOAD);
    }

    #[test]
    fn create_package_requires_regular_runtime() {
        let dir = tmpdir("badruntime");
        let out = dir.join("app");
        let err = create_package(&dir, &out, PAYLOAD).unwrap_err();
        assert!(matches!(err, PackageError::NotRegularFile(_)));
        assert!(!out.exists(), "no output may be left behind");
    }

    #[test]
    fn read_rejects_bad_magic() {
        let dir = tmpdir("badmagic");
        let mut data = package_bytes(FAKE_RUNTIME, PAYLOAD);
        let off = data.len() - FOOTER_LEN;
        data[off] = b'X';
        let p = dir.join("app");
        fs::write(&p, &data).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(err, PackageError::BadMagic(_)));
    }

    #[test]
    fn read_rejects_unsupported_version() {
        let dir = tmpdir("version");
        let mut data = package_bytes(FAKE_RUNTIME, PAYLOAD);
        let off = data.len() - FOOTER_LEN;
        data[off + 8..off + 12].copy_from_slice(&99u32.to_le_bytes());
        let p = dir.join("app");
        fs::write(&p, &data).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(
            err,
            PackageError::UnsupportedVersion { version: 99, .. }
        ));
    }

    #[test]
    fn read_rejects_truncated_footer() {
        let dir = tmpdir("short");
        let p = dir.join("app");
        fs::write(&p, &package_bytes(FAKE_RUNTIME, PAYLOAD)[..FOOTER_LEN - 1]).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(err, PackageError::TooShort { .. }));
    }

    #[test]
    fn read_rejects_truncated_payload() {
        let dir = tmpdir("truncpayload");
        // Declare a payload longer than the pre-footer bytes actually present.
        let mut data = package_bytes(FAKE_RUNTIME, PAYLOAD);
        let declared = (FAKE_RUNTIME.len() + PAYLOAD.len() + 1) as u64;
        let off = data.len() - FOOTER_LEN;
        data[off + 12..off + 20].copy_from_slice(&declared.to_le_bytes());
        let p = dir.join("app");
        fs::write(&p, &data).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(err, PackageError::BadPayloadLength { .. }));
    }

    #[test]
    fn read_rejects_impossible_payload_length() {
        let dir = tmpdir("impossible");
        let mut data = package_bytes(FAKE_RUNTIME, PAYLOAD);
        let off = data.len() - FOOTER_LEN;
        data[off + 12..off + 20].copy_from_slice(&u64::MAX.to_le_bytes());
        let p = dir.join("app");
        fs::write(&p, &data).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(err, PackageError::BadPayloadLength { .. }));
    }

    #[test]
    fn read_rejects_corrupt_payload_hash() {
        let dir = tmpdir("corrupt");
        let mut data = package_bytes(FAKE_RUNTIME, PAYLOAD);
        data[FAKE_RUNTIME.len()] ^= 0xFF; // flip one payload byte
        let p = dir.join("app");
        fs::write(&p, &data).unwrap();
        let err = read_embedded_bytecode(&p).unwrap_err();
        assert!(matches!(err, PackageError::CorruptPayload(_)));
    }

    #[test]
    fn read_accepts_zero_length_payload_with_valid_hash() {
        // A zero-length payload passes footer validation; it is rejected
        // later by the bytecode decoder, which is the caller's job.
        let dir = tmpdir("zeropayload");
        let p = dir.join("app");
        fs::write(&p, package_bytes(FAKE_RUNTIME, b"")).unwrap();
        assert_eq!(read_embedded_bytecode(&p).unwrap(), b"");
    }

    #[test]
    fn checked_arithmetic_boundaries() {
        // payload_length == available pre-footer bytes exactly: the runtime
        // image is empty and the payload starts at offset 0. Valid.
        let dir = tmpdir("boundary");
        let p = dir.join("app");
        fs::write(&p, package_bytes(b"", PAYLOAD)).unwrap();
        assert_eq!(read_embedded_bytecode(&p).unwrap(), PAYLOAD);

        // One byte more than available: rejected without overflow.
        let mut data = package_bytes(b"", PAYLOAD);
        let off = data.len() - FOOTER_LEN;
        data[off + 12..off + 20].copy_from_slice(&((PAYLOAD.len() + 1) as u64).to_le_bytes());
        let p2 = dir.join("app2");
        fs::write(&p2, &data).unwrap();
        assert!(matches!(
            read_embedded_bytecode(&p2).unwrap_err(),
            PackageError::BadPayloadLength { .. }
        ));
    }

    #[test]
    fn find_runtime_uses_env_override_exclusively() {
        let dir = tmpdir("findruntime");
        let good = make_runtime(&dir);
        std::env::set_var(RUNTIME_ENV, &good);
        let found = find_runtime().unwrap();
        assert_eq!(found, good);

        std::env::set_var(RUNTIME_ENV, dir.join("missing"));
        let err = find_runtime().unwrap_err();
        match err {
            PackageError::RuntimeNotFound { considered } => {
                assert_eq!(considered, vec![dir.join("missing")]);
            }
            other => panic!("expected RuntimeNotFound, got {other:?}"),
        }
        std::env::remove_var(RUNTIME_ENV);
    }
}
