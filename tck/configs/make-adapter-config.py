#!/usr/bin/env python3
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generate a Solvik TCK adapter configuration for a launcher distribution.

The runner's ``run`` command takes an adapter-config JSON of the shape::

    {"name","argv","fingerprint","env"}

whose ``fingerprint`` must equal the value the adapter's own ``describe`` advertises
so the report binds the exact distribution it verified. Rather than duplicate the
fingerprint algorithm here (a drift risk), this script *asks the adapter itself*
(``solvik_launcher_adapter.py --fingerprint <exe>``) for the value, guaranteeing the
two agree by construction.

Usage:
    python3 make-jvm-config.py <launcher-executable> <adapter-name> <out-config.json>

The produced ``argv`` always invokes the shared adapter script with the current
Python interpreter (the same interpreter that runs the runner), and passes the
launcher identity through the ``SOLVIK_TCK_LAUNCHER_CONFIG`` environment entry,
which the runner forwards verbatim to the adapter process (TCK.md section 12).
"""

import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ADAPTER = os.path.abspath(os.path.join(HERE, "..", "adapters", "solvik_launcher_adapter.py"))


def main(argv):
    if len(argv) != 4:
        sys.stderr.write(__doc__)
        return 2
    launcher, name, out_path = argv[1], argv[2], argv[3]
    launcher = os.path.abspath(launcher)
    if not os.path.exists(launcher):
        sys.stderr.write("launcher does not exist: %s\n" % launcher)
        return 2
    result = subprocess.run([sys.executable, ADAPTER, "--fingerprint", launcher],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write("fingerprint computation failed: %s\n" % result.stderr)
        return 2
    fingerprint = result.stdout.strip()
    if len(fingerprint) != 64 or any(c not in "0123456789abcdef" for c in fingerprint):
        sys.stderr.write("adapter returned a non-hex fingerprint\n")
        return 2

    cfg = {
        "name": name,
        "argv": [sys.executable, ADAPTER],
        "fingerprint": fingerprint,
        "env": {
            "SOLVIK_TCK_LAUNCHER_CONFIG": json.dumps(
                {"argv": [launcher], "name": name, "version": "0.1.0-jvm-native"},
                separators=(",", ":"), sort_keys=True),
        },
    }
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(cfg, handle, separators=(",", ":"), sort_keys=True)
        handle.write("\n")
    sys.stderr.write("wrote %s (fingerprint %s)\n" % (out_path, fingerprint))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
