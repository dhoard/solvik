class W {
    W() {
    }

    func opt(): String? {
        return "z"
    }
}
var w = W()
var v = w.opt()
    ?.hashCode()
print("v" .. (v != null))
