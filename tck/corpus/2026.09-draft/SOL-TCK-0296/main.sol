class W { W() {
    }

    func opt(): String? {
        return "z"
    }
}
val w = W()
val v = w.opt()
    ?.hashCode()
print("v" .. (v != null))
