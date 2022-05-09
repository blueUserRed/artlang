## Artlang (work in progress)

![logo](https://raw.githubusercontent.com/blueUserRed/artlang/master/logo.png)

<br>

###### _Note: like the language, this readme is work in progress too_

### _Introduction_
Artlang is a programming language that compiles to 
java bytecode. It is object-oriented has a static weak typesystem.

<br><br>

### _Features_

<br>

###### The main function
The main function must be defined in the top level of the program and
must not take any arguments. It is the function that gets automatically
called when the program is started.
```rust
fn main() {
    //put code here
}
```

<br>

###### Comments
Line comments start with `//` and go until the end of the line.
Block comments are started with `/*` and end with `*/`. It
is not a syntax error to leave a block comment unterminated.

<br>

###### Variables
Variables are declared using the `let` or `const` keywords. A variable
declared using the const keyword cannot be changed. The keyword is
followed by the name of the variable. After the name follows an 
equals and the initializer. The type of the variable is inferred
by the compiler from the initializer. Optionally, a type can be 
given explicitly by adding a colon and the type after the name.

````rust
fn main() {
    //declare a variable named x with a value of 0
    //the type is inferred to be int
    let x = 0
    
    //Use an explicit type
    let y: str = "hello"
    
    //declare a constant variable
    const myConst = 4
    
    x = 4 //assign to variable x
    y = true //syntax error because type is inferred to be int
    myConst = 4 //syntax error because myConst is a constant
}
````

<br>

###### Functions
A function is declared using the fn keyword. It is followed by the
name of the function, parentheses containing the arguments, and then
a code block containing the code of the function. An Argument
consists of the name of the argument followed by a colon and the type.
Multiple arguments are separated by commas. Trailing
commas are allowed. If the function has a return type, it is put
after a colon at the end of the function.

Functions can be 
called by writing the name of the function followed by parentheses
containing the arguments.

````rust

fn main() {
    addTwoNums(2, 4)
}

fn addTwoNums(a: int, b: int): int {
    return a + b
}
````

<!-- TODO: continue -->

<br>


### _Examples_

<br>

###### Hello World
```rust
fn main() {
    print "Hello World!"
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
