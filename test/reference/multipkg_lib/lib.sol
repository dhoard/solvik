package lib

pub struct User {
    pub name: string
    pub mut age: int
    password: string
}

pub struct Box<T> {
    pub value: T
}

pub enum Status {
    Active
    Inactive
}

pub enum Outcome<T, E> {
    Good(T)
    Bad(E)
}

pub trait Measurer {
    func measure() -> int
}

pub func makeUser(n: string) -> User {
    return User { name: n, age: 0, password: "secret" }
}

pub func makeBox<T>(v: T) -> Box<T> {
    return Box { value: v }
}

pub func two() -> int {
    return 2
}

struct Internal {
    pub x: int
}
