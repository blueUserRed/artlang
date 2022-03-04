## Artlang (work in progress)

<br>
<br>

#### _Introduction_
Artlang is a programming language that compiles to 
java bytecode. It is object-oriented has a static weak typesystem.

#### _Examples_
###### Hello World
```rust
fn main() {
    print "Hello World!";
}
```

<br>

###### FizzBuzz (this going to get simpler)
```rust
fn fizzBuzz() {
    let i = 0
    while ((i := i + 1) <= 100) {
        let output = ""
        let isEmpty = true
        if (i % 3 == 0) {
            output = "Fizz"
            isEmpty = false
        }
        if (i % 5 == 0) {
            output += "Buzz"
            isEmpty = false
        }
        if (isEmpty) print i else print output
    }
}
```
