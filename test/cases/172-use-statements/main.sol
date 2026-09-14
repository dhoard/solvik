package usestmts

// Dependency metadata: parsed and preserved, never loaded (single-file).
use file:vendor.stringkit
use file:vendor.textkit as tk
use url:registry.example.com.remotekit as remote

struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println("ok")
        return 0
    }
}
