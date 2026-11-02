// include-extra.sol — a second imported file, reached directly by example.sol. It imports
// include.sol itself, so include.sol is also reached transitively from here. Its declarations
// merge into the `showcase` module opened by include.sol; only the entry file contributes
// statements.
//
// The import below is resolved relative to this file. Because example.sol also imports
// include.sol itself (both as "include.sol" and "./include.sol"), this is a diamond: the loader
// follows each physical file once and merges the declarations without duplication.

import "include.sol"

module showcase {

    // A trait declared in an imported file. A concrete type in the entry file implements it and
    // composes its default through the module-qualified form `showcase::Echo.echo(self, value)`.
    pub trait Echo {
        pub func echo(value: String) -> String {
            return "echo:" .. value
        }
    }

    // A second contributor to the merged module. `showcase::extraTag` is visible to the entry
    // file even though include.sol never mentions it.
    pub func extraTag() -> String {
        return "extra"
    }
}
