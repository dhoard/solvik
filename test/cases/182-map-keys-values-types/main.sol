package mapkvtypes

class Main {

    public static run(args: String...): Long {
        let m: Map<String, Long> = { "a": 1 }
        // keys() refines to the key type, values() to the value type.
        let ks: List<String> = m.keys()
        let vs: List<Long> = m.values()
        System.out().println(ks.get(0))
        System.out().println(vs.get(0))
        return 0
    }
}
