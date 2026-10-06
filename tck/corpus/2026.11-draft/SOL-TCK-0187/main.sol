enum Result<T, E> {
    Ok(T)
    Err(E)
}
var r: Result<Integer, String> = Result.Ok(42)
var m = match r {
    Ok(v) => "ok" .. v
    Err(e) => "err" .. e
}
print(m)
