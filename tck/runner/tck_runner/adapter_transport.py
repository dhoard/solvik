"""Subprocess supervision for one adapter session.

The runner owns exactly one adapter process per test. The adapter is launched
with:

* a fresh, runner-created workspace directory as its working directory;
* an *allowlisted* environment (section 12), never the runner's full environment;
* piped stdin/stdout carrying the protocol and a piped stderr captured to a
  bounded buffer by a reader thread (guest stdout/stderr travel inside response
  fields, so the adapter's own stderr is only diagnostic logging).

The adapter is started in its own process group so a timeout can terminate the
whole adapter/IUT tree, not just the direct child. On timeout the runner waits a
bounded grace period, then force-kill; a surviving process is an infrastructure
error. Cleanup runs on every path -- pass, fail, timeout, or interruption -- and
never deletes paths outside the runner-created workspace.
"""

from __future__ import annotations

import errno
import os
import signal
import subprocess
import threading
import time

from . import events, protocol


class Transport:
    """Owns one adapter subprocess and its protocol channel."""

    def __init__(self, argv, workspace, env, stderr_limit=protocol.MAX_MESSAGE_BYTES,
                 cancel_grace_ms=protocol.DEFAULT_CANCEL_GRACE_MS):
        self._argv = list(argv)
        self._workspace = workspace
        self._env = dict(env)
        self._stderr_limit = stderr_limit
        self._cancel_grace_ms = cancel_grace_ms
        self._proc = None
        self._stderr_buf = bytearray()
        self._stderr_truncated = False
        self._stderr_thread = None
        self._closed = False
        self._infra_events = []

    # -- lifecycle ---------------------------------------------------------
    def start(self):
        try:
            self._proc = subprocess.Popen(
                self._argv,
                cwd=self._workspace,
                env=self._env,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                bufsize=0,
                preexec_fn=os.setsid if hasattr(os, "setsid") else None,
            )
        except OSError as exc:
            raise _LaunchFailure(str(exc)) from exc
        self._stderr_thread = threading.Thread(target=self._drain_stderr, daemon=True)
        self._stderr_thread.start()

    def _drain_stderr(self):
        read = 0
        while True:
            try:
                chunk = self._proc.stderr.read(4096)
            except (ValueError, OSError):
                # Handle closed during teardown after a forced kill; not an error.
                break
            if not chunk:
                break
            room = self._stderr_limit - read
            if room <= 0:
                self._stderr_truncated = True
                continue
            self._stderr_buf.extend(chunk[:room])
            read += min(len(chunk), room)
            if len(chunk) > room:
                self._stderr_truncated = True

    # -- protocol exchange -------------------------------------------------
    def send(self, request_bytes: bytes):
        if self._proc is None or self._proc.stdin is None:
            raise protocol.ProtocolError("adapter stdin unavailable")
        try:
            self._proc.stdin.write(request_bytes)
            self._proc.stdin.flush()
        except (BrokenPipeError, OSError) as exc:
            self._note(events.ADAPTER_LOST, "stdin write failed: %s" % exc)
            raise protocol.ProtocolError("adapter stdin closed during send") from exc

    def read_line(self, timeout_ms):
        """Read exactly one newline-terminated stdout line within *timeout_ms*.

        Returns the raw line bytes, ``None`` on clean EOF, or raises a timeout
        infrastructure condition via a returned :class:`Timeout` sentinel object
        so the caller can distinguish "no data yet / timed out" from EOF.
        """
        deadline = time.monotonic() + timeout_ms / 1000.0
        buf = bytearray()
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise _ReadTimeout("no response line within %d ms" % timeout_ms)
            if len(buf) > protocol.MAX_MESSAGE_BYTES:
                raise _ReadTimeout("response exceeds max message size")
            chunk = self._read_some(b"\n", remaining)
            if chunk is None:  # timed out inside _read_some
                raise _ReadTimeout("no response line within %d ms" % timeout_ms)
            if chunk == b"":  # EOF
                if buf:
                    # A final line without a trailing newline is accepted only if
                    # it is the very last thing the adapter wrote before closing;
                    # section 8 requires stdout EOF after the last response, so a
                    # dangling partial line is a protocol violation.
                    raise protocol.ProtocolError("stdout closed mid-response")
                return None
            buf.extend(chunk)
            if buf.endswith(b"\n"):
                return bytes(buf)

    def _read_some(self, stop, timeout_seconds):
        """Read until *stop* byte or *timeout_seconds*, using a selector thread."""
        result = {}

        def _worker():
            out = bytearray()
            try:
                while True:
                    byte = self._proc.stdout.read(1)
                    if byte == b"":
                        if out:
                            result["partial"] = bytes(out)
                        else:
                            result["data"] = b""  # clean EOF -> b""
                        return
                    out.extend(byte)
                    if byte == stop:
                        result["data"] = bytes(out)
                        return
            except (ValueError, OSError):
                # The handle was closed during teardown (e.g. after a timeout
                # forced a kill). This is not an error; the session is ending.
                result.setdefault("data", b"")

        t = threading.Thread(target=_worker, daemon=True)
        t.start()
        t.join(timeout_seconds)
        if t.is_alive():
            return None  # timeout; the reader thread remains but the session is
            # being torn down, so its orphaned reads are discarded on cleanup.
        if "partial" in result:
            # EOF after some data but no newline: surface the partial by
            # returning it without the terminator, caller sees EOF-mid-data.
            self._eof_partial = result["partial"]
            return b""
        return result.get("data", b"")

    # -- teardown ----------------------------------------------------------
    def close(self):
        """Close stdin, require stdout EOF and adapter exit 0 (section 8)."""
        if self._proc is None or self._closed:
            return
        self._closed = True
        try:
            if self._proc.stdin:
                self._proc.stdin.close()
        except OSError:
            pass
        # Wait for graceful exit within the cancel grace period.
        rc = self._wait(self._cancel_grace_ms)
        if rc is None:
            self._terminate_tree()
            rc = self._wait(self._cancel_grace_ms)
            if rc is None:
                self._kill_tree()
                rc = self._wait(1000)
        if rc is None:
            self._note(events.ADAPTER_LOST, "adapter still running after kill; surviving process")
            self._surviving = True
            return
        # Drain/close remaining handles.
        try:
            if self._proc.stdout:
                self._proc.stdout.close()
        except OSError:
            pass
        if self._stderr_thread:
            self._stderr_thread.join(timeout=2.0)
        try:
            if self._proc.stderr:
                self._proc.stderr.close()
        except OSError:
            pass
        if rc != 0:
            self._note(events.ADAPTER_EXIT_NONZERO, "adapter exit code %r" % rc, returncode=rc)
        self._returncode = rc

    def _wait(self, timeout_ms):
        deadline = time.monotonic() + timeout_ms / 1000.0
        while time.monotonic() < deadline:
            rc = self._proc.poll()
            if rc is not None:
                return rc
            time.sleep(0.005)
        return self._proc.poll()

    def _signal_tree(self, sig):
        if self._proc is None:
            return
        try:
            if hasattr(os, "killpg"):
                os.killpg(os.getpgid(self._proc.pid), sig)
            else:
                self._proc.send_signal(sig)
        except OSError as exc:
            if exc.errno != errno.ESRCH:
                pass  # best-effort; surviving-process check covers real failures

    def _terminate_tree(self):
        self._signal_tree(signal.SIGTERM)

    def _kill_tree(self):
        self._signal_tree(signal.SIGKILL)

    # -- accessors ---------------------------------------------------------
    def note_event(self, ev):
        self._infra_events.append(ev)

    def _note(self, kind, detail, **extra):
        self._infra_events.append(events.event(kind, detail, **extra))

    @property
    def infra_events(self):
        return list(self._infra_events)

    @property
    def returncode(self):
        return getattr(self, "_returncode", None)

    @property
    def stderr_text(self):
        return bytes(self._stderr_buf).decode("utf-8", errors="replace")

    @property
    def stderr_truncated(self):
        return self._stderr_truncated


class _LaunchFailure(Exception):
    pass


class _ReadTimeout(Exception):
    pass
