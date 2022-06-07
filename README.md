## Artlang (work in progress)

![logo](https://raw.githubusercontent.com/blueUserRed/artlang/master/logo.png)

<br>

###### _Note: like the language, this readme is work in progress too_

* [Introduction](#_introduction_)
* [Features](#_features_)
  * [The main function](#the-main-function)
  * [Comments](#comments)
  * [Variables](#variables)
  * [Variable Assignments](#variable-assignments)
  * [Primitive Types](#primitive-types)
  * [If & else](#if--else)
  * [Loops](#loops)
  * [Block Expressions](#block-expressions)
* [Examples](#_examples_)
  * [Hello World](#hello-world)
  * [FizzBuzz](#fizzbuzz)


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
    // declare a variable named x with a value of 0
    // the type is inferred to be int
    let x = 0
    
    // Use an explicit type
    let y: str = "hello"
    
    // declare a constant variable
    const myConst = 4
    
    x = 4 // assign to variable x
    y = true // syntax error because type is inferred to be int
    myConst = 4 // syntax error because myConst is a constant
}
```

<br>

#### Variable Assignments
Additionally to the standard assingments using ``=``, there are also 
assign-shorhands, increment/decrement operators and the walrus-assignment.
```rust
fn main() {
    let x = 2
    
    x++ // increment x by one
    x-- // decrement x by one
    x *= 2 // multiply x by two
    
    // unlike other languages, artlang treats an
    // assignment as a statement, not an expression
    print x = 3 // error, because the type of x = 3 is void
    
    // if you want an assignment to return the assigned
    // value, use the walrus-operator instead
    print x := 3
}
```

<br>

#### Primitive Types
Primitive types are basic types used to represent e.g. numbers.
* byte: 8-bit signed integer
* short: 16-bit signed integer
* int: 32-bit signed integer
* long: 64-bit signed integer
* float: 32-bit floating point number
* double: 64-bit floating point number
* bool: a value that can either be true or false
* str: a string of characters (secretly an object, not a primitive)

```rust
fn main() {
    let a: int = 2323 // numbers without comma are automatically ints
    let b: float = 23.23 // numbers with comma are automatically floats
    
    // number literals for different types
    
    123#B // byte
    123#S // short
    123#I // int (redundant)
    123#L // long
    123#F // float
    123#D // double
    
    // Also works for hexadecimal/binary/octal representation
    
    0xFFEE51#L // long
    0b1111_0011#B // byte
    0o711#I // int
    
    // convert between different number-types
    
    let myInt: int = 123
    
    let myShort: short = myInt.short // converts myInt to a short
    
    
}
```

<br>

#### If & else
If/else statements can be used to control the flow of the program.
```rust
fn main() {
    let num1 = 10
    let num2 = 20
    
    if (num1 > num2) {
        // this code gets executed if the condition in the brackets is true
        print "num1 is greater"
    } else {
        // this code gets executed if the condition is false
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
    
    loop print "Hello World!" // loops forever
}
```

<br>

#### Block Expressions
In Artlang, code-blocks can be used as Expressions. This is done by
putting an arrow `=>` with an expression at the end of the block.

```rust
fn main() {
  
  let x = {
    const a = someFn()
    const b = someFn()
    => a * b + 3 // this will get assigned to x
  }
  
  // the arrow must be the last statement in the block and cant be
  // contained in an if, while or similar
  
  let y = {
    => 3 // syntax error, because '=>' is not the last statement
    print "hi"
  }
  
  // Block-expressions can be combined with if-expressions
  
  let z = if (someCondition) {
    // ... some code ...
    => value
  } else {
    // ... some code ...
    => some_other_value
  }
  
}
```

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
