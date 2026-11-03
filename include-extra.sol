// include-extra.sol — a second included file, reached directly by example.sol. It includes
// include.sol itself, so include.sol is also reached transitively from here. Its declarations
// merge into the `showcase` module opened by include.sol; only the entry file contributes
// statements.
//
// The include below is resolved relative to this file. Because example.sol also includes
// include.sol itself (both as "include.sol" and "./include.sol"), this is a diamond: the loader
// follows each physical file once and merges the declarations without duplication.
//
// Its alias block is private to this file: `LocalEcho` names the trait this file declares, and
// neither include.sol nor example.sol can see that spelling.

include {
    "include.sol"
}

alias { showcase::Echo LocalEcho }

module showcase {

    // A trait declared in an included file. A concrete type in the entry file implements it and
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

    // A trait alias is usable where the trait is: `implements` here, and the qualified default
    // form `LocalEcho.echo(self, value)` inside an overriding body.
    pub type EchoBox implements LocalEcho {
        pub func EchoBox() { }
    }
}
