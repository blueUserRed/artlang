## Artlang (work in progress)

![logo](https://raw.githubusercontent.com/blueUserRed/artlang/master/logo.png)

<br>

###### _Note: like the language, this readme is work in progress too_

### _Introduction_
Artlang is a programming language that compiles to 
java bytecode. It is object-oriented has a static weak typesystem. It is easy to understand 
and doesn't get in your way while coding.

<br><br>

### _Features_

<br>

##### The main function
The main function gets called automatically when the program is started.
It must be defined in the top level of the program. It can either take no
parameters or a string-array, which corresponds to the commandline
parameters.
```rust
fn main() {
    //put code here
}

fn main(args: str[]) {
    print args[0]
}
```

<br>

##### Comments
Line comments start with `//` and go until the end of the line.
Block comments are started with `/*` and end with `*/`. It
is not a syntax error to leave a block comment unterminated.

<br>

##### Variables
Variables can be declared as mutable (using `let`) or as constant
(using `const`). The type is inferred by the compiler, but can also be
given explicitly. An initializer must be present.

```rust
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
```

<br>

#### If & else
If/else statements can be used to contol the flow of the program.
```rust
fn main() {
    let num1 = 10
    let num2 = 20
    
    if (num1 > num2) {
        //this code gets executed if the condition in the brackets is true
        print "num1 is greater"
    } else {
        //this code gets executed if the condition is false
        print "num1 is not greater"
    }
}
```
The else-branch can be omitted if it is not needed. Additionaly, the
curly braces can be omittet if the branch only consits of one statement.

Ifs can also be used as expressions if both an if and an else branch are
present and both result in a compatible type.
```rust
fn main() {
    let num1 = 10
    let num2 = 20
    
    print if (num1 > num2) "num1 is greater" else "num1 is not greater"
}
```
<br>

#### Loops
Artlang currently supports two loops, the `while`-loop and the
`loop`-loop. `while` loops until a condition is false, `loop`
loops forever. Like if-statements, curly braces are optional if the
body of the loop only consists of one statement.
```rust
fn main() {
    let i = 0
    while (i < 100) {
        print i
        i++
    }
    
    loop print "Hello World!" //loops forever
}
```


<br>

<!-- TODO: continue -->

<br>


### _Examples_

<br>

##### Hello World
```rust
fn main() {
    print "Hello World!"
}
```

<br>

##### FizzBuzz
```rust
fn fizzBuzz() {
    let i = 0
    while ((i := i + 1) <= 100) {
        let output = if (i % 3 == 0) "Fizz" else ""
        if (i % 5 == 0) output += "Buzz"
        if (output.equals("")) print i else print output
    }
}
```
