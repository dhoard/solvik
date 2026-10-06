var s: String? = "ab"
var r: Integer? = s?.hashCode()
var n: String? = null
var q: Integer? = n?.hashCode()
print("hc" .. (r != null) .. (q == null))
