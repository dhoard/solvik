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
"""Generate an adapter configuration for the independent reference front end.

Asks the adapter for its own fingerprint (``--fingerprint``) rather than recomputing
it here, so a config and the adapter's ``describe`` cannot drift; a mismatch would be
a hard preflight failure for reasons unrelated to the program under test.

Usage:
    python3 make-reference-config.py <out-config.json> [name]
"""

import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ADAPTER = os.path.abspath(os.path.join(HERE, "..", "adapters", "reference_subset_adapter.py"))


def main(argv):
    if len(argv) not in (2, 3):
        sys.stderr.write(__doc__)
        return 2
    out_path = argv[1]
    name = argv[2] if len(argv) == 3 else "reference-subset"
    fingerprint = subprocess.run([sys.executable, ADAPTER, "--fingerprint"],
                                 capture_output=True, text=True)
    if fingerprint.returncode != 0:
        sys.stderr.write("reference adapter refused to report a fingerprint\n")
        return 2
    cfg = {"name": name,
           "argv": [sys.executable, ADAPTER],
           "fingerprint": fingerprint.stdout.strip(),
           "env": {}}
    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(cfg, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
