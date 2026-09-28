val s: String? = "ab"
val r: Integer? = s?.hashCode()
val n: String? = null
val q: Integer? = n?.hashCode()
print("hc" .. (r != null) .. (q == null))
