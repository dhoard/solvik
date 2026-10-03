enum Result<T, E> {
    Ok(T)
    Err(E)
}
val r: Result<Integer, String> = Result.Err("bad")
val m = match r {
    Ok(v) => "ok" .. v
    Err(e) => "err" .. e
}
print(m)
