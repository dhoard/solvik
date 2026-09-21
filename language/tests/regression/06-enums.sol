enum Result {
    Ok(Integer)
    Error(String)
    Empty
}
func show(r: Result): String {
    return match r {
        Ok(v) => "ok " .. v
        Error(e) => "err " .. e
        Empty => "empty"
    }
}
println(show(Result.Ok(1)))
println(show(Result.Error("bad")))
println(show(Result.Empty))
