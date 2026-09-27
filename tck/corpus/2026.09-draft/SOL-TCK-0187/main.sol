enum Result<T, E> {
    Ok(T)
    Err(E)
}
val r: Result<Integer, String> = Result.Ok(42)
val m = match r {
    Ok(v) => "ok" .. v
    Err(e) => "err" .. e
}
print(m)
