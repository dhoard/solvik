"""Solvik TCK portable runner package.

Modules are kept independent of any Solvik/GraalVM/Truffle implementation so the
runner and its self-tests run with only the Python standard library (acceptance
criteria 1 and 2). The package deliberately imports nothing outside ``tck/``.
"""
