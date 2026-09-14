package mapkvtypes

struct Main {

    pub func run(args: String...): Integer {
        let m: Map<String, Long> = { "a": 1 }
        // keys() refines to the key type, values() to the value type.
        let ks: List<String> = m.keys()
        let vs: List<Long> = m.values()
        System.getOut().println(ks.get(0))
        System.getOut().println(vs.get(0))
        return 0
    }
}
