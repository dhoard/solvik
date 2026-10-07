class W {
    W() {
    }

    method opt(): String? {
        return "z"
    }
}
var w: W = W()
var v: Integer? = w.opt()
    ?.hashCode()
print("v" .. (v != null))
