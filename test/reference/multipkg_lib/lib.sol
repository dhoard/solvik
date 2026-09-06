package lib

pub struct User {
    pub name: String
    pub mut age: Int
    password: String
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
    func measure() -> Int
}

pub func makeUser(n: String) -> User {
    return User { name: n, age: 0, password: "secret" }
}

pub func makeBox<T>(v: T) -> Box<T> {
    return Box { value: v }
}

pub func two() -> Int {
    return 2
}

struct Internal {
    pub x: Int
}
