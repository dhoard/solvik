# Contributing to Solvik

Read `AGENTS.md` and all documents it marks as authoritative before changing language behavior.

Implement only the phase marked `NEXT` in `docs/STATUS.md`. Every phase must remain buildable, include positive and negative tests, pass focused tests and the build wrappers required by `AGENTS.md`, update status with evidence, and stop before the next phase.

This repository reuses infrastructure from GraalVM SimpleLanguage but does not preserve SimpleLanguage source compatibility or dynamic semantics. Preserve upstream copyright notices and historical attribution where required.

New Solvik files carry the Apache-2.0 notice for Douglas Hoard. Files adapted from GraalVM SimpleLanguage keep the upstream Oracle/UPL notice and add the Douglas Hoard copyright line below it. Do not replace an upstream notice; see `NOTICE` and `LICENSE-APACHE`.
