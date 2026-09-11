package usestmts

// Dependency metadata: parsed and preserved, never loaded (single-file).
use file:vendor.stringkit
use file:vendor.textkit as tk
use url:registry.example.com.remotekit as remote

class Main {

    public static run(args: String...): Long {
        System.out().println("ok")
        return 0
    }
}
