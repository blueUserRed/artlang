package ast

import tokenizer.Token
import Datatype
import Datakind
import tokenizer.TokenType

/**
 * The **A**bstract **S**yntax **T**ree is an abstract representation of the program in form of tree. All nodes of the
 * tree inherit from this class
 */
abstract class AstNode(val relevantTokens: List<Token>) {

    /**
     * the type of this expression (can be void if the node doesn't result in a type)
     */
    var type: Datatype = Datatype.Void()

    /**
     * swaps a child node of this node to another node
     * @param orig the node that is supposed to be swapped
     * @param to the node [orig] is swapped to
     * @throws CantSwapException if the child node can't be swapped (this node has no child nodes, if none of the
     * child nodes matches [orig])
     */
    abstract fun swap(orig: AstNode, to: AstNode)

    /**
     * thrown by [swap] if the node cant' be swapped
     */
    class CantSwapException : RuntimeException()

    /**
     * @see ast.AstNodeVisitor
     */
    abstract fun <T> accept(visitor: AstNodeVisitor<T>): T

    /**
     * wraps an expression (= leaves something on the stack) in a statement (= leaves nothing on the stack).
     * Probably not necessary anymore because the [type] of a node can just be void
     */
    class ExpressionStatement(var exp: AstNode,relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (exp !== orig) throw CantSwapException()
            exp = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a function
     */
    abstract class Function(relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * the name of the Function
         */
        abstract val name: String

        /**
         * The descriptor of the function containing the actual arg-types and the return type
         */
        abstract val functionDescriptor: FunctionDescriptor

        /**
         * true if the function is static
         */
        abstract val isStatic: Boolean

        /**
         * true if the function is defined in the topLevel
         */
        abstract val isTopLevel: Boolean

        /**
         * true if the function is private
         */
        abstract val isPrivate: Boolean

        /**
         * the class containing the function; null if the function is defined in the topLevel
         */
        abstract var clazz: ArtClass?
    }

    /**
     * represents a function definition
     * @param statements the body of the function
     * @param modifiers the modifiers of the function
     * @param isTopLevel true if the function is defined in the toplevel
     */
    class FunctionDeclaration(
        var statements: Block,
        val nameToken: Token,
        val modifiers: List<Token>,
        override val isTopLevel: Boolean,
        relevantTokens: List<Token>
    ) : Function(relevantTokens) {

        override val name: String = nameToken.lexeme

        /**
         * used to set [functionDescriptor]
         */
        lateinit var _functionDescriptor: FunctionDescriptor

        override val functionDescriptor: FunctionDescriptor
            get() = _functionDescriptor

        /**
         * the maximum amount of locals used by the function (corresponds to the maxLocals property on the jvm)
         *
         * includes function arguments, long/double count as two locals
         *
         * set by the Variable resolver
         */
        var amountLocals: Int = 0

        /**
         * List of all arguments for the function containing their names and type-nodes
         *
         * set by the parser
         */
        var args: MutableList<Pair<Token, DatatypeNode>> = mutableListOf()

        /**
         * the type-node corresponding to the return type of the function
         *
         * set by the parser
         */
        var returnType: DatatypeNode? = null

        /**
         * the class in which the function is defined. null if the function is defined in the top level
         *
         * set by the variable resolver
         */
        override var clazz: ArtClass? = null

        /**
         * true if the function is static
         */
        override val isStatic: Boolean
            get() {
                if (isTopLevel) return true
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "static") return true
                }
                return false
            }

        /**
         * true if the function is private
         */
        override val isPrivate: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "public") return false
                }
                return true
            }

        /**
         * true if the function has a this-argument
         */
        val hasThis: Boolean
            get() = !isStatic

        override fun swap(orig: AstNode, to: AstNode) {
            if (statements !== orig || to !is Block) throw CantSwapException()
            statements = to
        }

        fun hasModifier(modifier: String): Boolean {
            for (mod in modifiers) if (mod.tokenType == TokenType.IDENTIFIER && mod.lexeme == modifier) return true
            return false
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents the whole Program; the root of the tree
     * @param funcs the top level function
     * @param classes the classes defined in the top level
     * @param fields the fields defined in the top level
     */
    class Program(
        val funcs: MutableList<Function>,
        val classes: MutableList<ArtClass>,
        val fields: MutableList<Field>,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (to is Function) for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            if (to is ArtClass) for (i in classes.indices) if (classes[i] === orig) {
                classes[i] = to
                return
            }
            if (to is Field) for (i in fields.indices) if (fields[i] === orig) {
                fields[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * @param staticFuncs the static functions that are contained in this class
     * @param funcs the non-static functions that are contained in this class
     * @param staticFields the static fields that are contained in this class
     * @param fields the non-static fields that are contained in this class
     */
    abstract class ArtClass(
        val staticFuncs: MutableList<Function>,
        val funcs: MutableList<Function>,
        val staticFields: MutableList<Field>,
        val fields: MutableList<Field>,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        /**
         * the name of the class
         */
        abstract val name: String

        /**
         * the superClass of this class
         */
        abstract val extends: ArtClass?

        /**
         * the name that is used to refer to the class on the jvm
         */
        abstract val jvmName: String
    }

    /**
     * represents a class
     * @param name the name of the class
     * @param staticFuncs the static functions that are contained in this class
     * @param funcs the non-static functions that are contained in this class
     * @param staticFields the static fields that are contained in this class
     * @param fields the non-static fields that are contained in this class
     * @param extendsToken the with the name of the extending class; null if none
     */
    class ClassDefinition(
        val nameToken: Token,
        staticFuncs: MutableList<Function>,
        funcs: MutableList<Function>,
        staticFields: MutableList<Field>,
        fields: MutableList<Field>,
        val extendsToken: Token?,
        relevantTokens: List<Token>,
        override val jvmName: String = nameToken.lexeme
    ) : ArtClass(staticFuncs, funcs, staticFields, fields, relevantTokens) {

        override val name: String = nameToken.lexeme

        lateinit var _extends: ArtClass // necessary to allow setting extends

        override val extends: ArtClass
            get() = _extends

        override fun swap(orig: AstNode, to: AstNode) {
            if (to is Function) for (i in staticFuncs.indices) if (staticFuncs[i] === orig) {
                staticFuncs[i] = to
                return
            }
            if (to is Function) for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            if (to is Field) for (i in fields.indices) if (fields[i] === orig) {
                fields[i] = to
                return
            }
            if (to is Field) for (i in staticFields.indices) if (staticFields[i] === orig) {
                staticFields[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a print statement
     * @param toPrint the node that should be printed
     * @param printToken the 'print' token
     */
    class Print(var toPrint: AstNode, val printToken: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== toPrint) throw CantSwapException()
            toPrint = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a code block
     * @param statements the statements contained in the block
     */
    class Block(val statements: Array<AstNode>, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            for (i in statements.indices) if (statements[i] === orig) {
                statements[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a variable declaration
     * @param name the name of the variable
     * @param initializer the node that initializes the variable (the part after the '=')
     * @param isConst true if the variable is constant
     * @param decToken the token used to declare the variable (either let or const)
     */
    class VariableDeclaration(
        val name: Token,
        var initializer: AstNode,
        val isConst: Boolean,
        val decToken: Token,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        /**
         * the local variable index; the index into the locals array of the jvm at which the variable can be found
         *
         * set by the parser
         */
        var index: Int = 0

        /**
         * the explicitly stated type (node) of the variable. null if none is present
         */
        var explType: DatatypeNode? = null

        /**
         * the type of the variable
         *
         * set by the type checker
         */
        lateinit var varType: Datatype

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== initializer) throw CantSwapException()
            initializer = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a (walrus-) assignment to a local variable, a field or array
     */
    class Assignment(
        var from: AstNode?,
        var name: Token,
        var toAssign: AstNode,
        val isWalrus: Boolean,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        var fieldDef: Field? = null

        /**
         * the local variable index; the index into the locals array of the jvm at which the variable can be found;
         * if the assignment target is not a local, the index is -1
         */
        var index: Int = -1

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
                return
            }
            if (toAssign === orig) {
                toAssign = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents the loop-statement
     * @param body the body of the loop
     */
    class Loop(var body: AstNode, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (body !== orig) throw CantSwapException()
            body = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents an if/else statement
     * @param ifStmt the if-branch of the statement
     * @param elseStmt the else-branch of the statement; null if there is none
     * @param condition the condition that determines the path of execution
     */
    class If(
        var ifStmt: AstNode,
        var elseStmt: AstNode?,
        var condition: AstNode,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (ifStmt === orig) {
                ifStmt = to
                return
            }
            if (elseStmt === orig) {
                elseStmt = to
                return
            }
            if (condition === orig) {
                condition = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a while loop
     * @param body the body of the loop
     * @param condition the condition
     */
    class While(var body: AstNode, var condition: AstNode, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (body === orig) {
                body = to
                return
            }
            if (condition === orig) {
                condition = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a return statement
     * @param toReturn the value that is returned; null if there is none
     * @param returnToken the 'return' token
     */
    class Return(var toReturn: AstNode?, val returnToken: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (toReturn !== orig) throw CantSwapException()
            toReturn = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VarAssignShorthand(
        var from: AstNode?,
        var name: Token,
        val operator: Token,
        var toAdd: AstNode,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        var index: Int = -1
        var fieldDef: Field? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (this.from === orig) {
                this.from = to
                return
            }
            if (toAdd === orig) {
                toAdd = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a variable increment; only increments by a constant value
     * @param name the variable name (or get)
     * @param toAdd the constant value to add
     */
    class VarIncrement(var name: Get, val toAdd: Byte, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * the local variable index; the index into the locals array of the jvm at which the variabl can be found;
         * -1 if the increment target is not a local variable
         */
        var index: Int = 0

        override fun swap(orig: AstNode, to: AstNode) {
            if (name !== orig || to !is Get) throw CantSwapException()
            name = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a binary operation (e.g. '+', '/', '&&', '>=')
     * @param left the left side of the operation
     * @param operator the operator of the operation
     * @param right the right side of the operation
     */
    class Binary(
        var left: AstNode,
        val operator: Token,
        var right: AstNode,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (left === orig) {
                left = to
                return
            }
            if (right === orig) {
                right = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a literal value (e.g. a constant number, a constant string)
     */
    class Literal(val literal: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a reference to local variable; the parser only emits gets, the variable node is swapped in by the
     * VariableResolver if a get references a local variable
     */
    class Variable(val name: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * the local variable index; the index into the locals array of the jvm at which the variabl can be found
         */
        var index: Int = -1

        override fun swap(orig: AstNode, to: AstNode){
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a group (an expression wrapped in parentheses)
     * @param grouped the expression in parentheses
     */
    class Group(var grouped: AstNode, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (grouped !== orig) throw CantSwapException()
            grouped = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a unary expression (e.g. '-', '!')
     * @param on the expression the operator operates on
     * @param operator the operator
     */
    class Unary(var on: AstNode, val operator: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (on !== orig) throw CantSwapException()
            on = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a function call
     * @param func the get for the function
     * @param arguments the arguments provided
     */
    class FunctionCall(
        var from: AstNode?,
        val name: Token,
        val arguments: MutableList<AstNode>,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        /**
         * the definition of the referenced function
         *
         * set by the TypeChecker
         */
        lateinit var definition: Function

        /**
         * returns the full name of the function (using the [AstPrinter])
         */
        fun getFullName(): String {
            return if (from == null) "${name.lexeme}()" else "${from!!.accept(AstPrinter())}.${name.lexeme}()"
        }

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
                return
            }
            for (i in arguments.indices) if (arguments[i] === orig) {
                arguments[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * gets something from an array
     * @param from the expression that results in the array
     * @param arrIndex the expression that results in the index into the array
     */
    class ArrGet(var from: AstNode, var arrIndex: AstNode, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
                return
            }
            if (arrIndex === orig) {
                arrIndex = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a set into some index in an array
     * @param from the expression that results in the array
     * @param arrIndex the expression that results in the index into the array
     * @param isWalrus true if a walrus-assign is used
     */
    class ArrSet(
        var from: AstNode,
        var arrIndex: AstNode,
        var to: AstNode,
        val isWalrus: Boolean,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
                return
            }
            if (arrIndex === orig) {
                arrIndex = to
                return
            }
            if (this.to === orig) {
                this.to = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represent a get expression. Used to get something from an Object or a class (e.g. `SomeClazz.someStaticField`)
     * @param name the name of the thing to get
     * @param from where to get [name] from. Null if [name] should be looked up in the top-level
     */
    class Get(val name: Token, var from: AstNode?, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * if the get corresponds to field, fieldDef is set to the definition of the field
         *
         * set by the typeChecker
         */
        var fieldDef: Field? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a break-statement
     * @param breakToken the 'break' token
     */
    class Break(val breakToken: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a continue-statement
     * @param continueToken the 'continue' token
     */
    class Continue(val continueToken: Token, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a call to the constructor of an object. The Parser only emits FunctionCalls, because it can't
     * distinguish between functions and constructors. The TypeChecker swaps the FunctionCalls that refer to Constructors
     * with this node
     * @param clazz the class to which the constructor refers to
     * @param arguments the list of arguments with which the constructor is called
     */
    class ConstructorCall(
        var clazz: ArtClass,
        val arguments: MutableList<AstNode>,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            if (clazz === orig && to is ArtClass) {
                clazz = to
                return
            }
            for (i in arguments.indices) if (arguments[i] === orig) {
                arguments[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents a field either in the top-level or in a class
     */
    abstract class Field(relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * the name of the field
         */
        abstract val name: String

        /**
         * true if the field is static
         */
        abstract val isStatic: Boolean

        /**
         * true if the field is private
         */
        abstract val isPrivate: Boolean

        /**
         * true if the field is defined in the topLevel
         */
        abstract val isTopLevel: Boolean

        /**
         * the type of the value stored in the field
         */
        abstract val fieldType: Datatype

        /**
         * true if the field is const and can't be reassigned
         */
        abstract val isConst: Boolean

        /**
         * the class in which the field is defined; null if it is defined in the top level
         */
        abstract var clazz: ArtClass?
    }

    /**
     * represents a field declaration
     * @param explType the explicit type (node) of the field
     * @param initializer the initializer that initializes the field with a value
     * @param isConst true if the field is constant
     * @param modifiers the modifiers for this field
     * @param isTopLevel true if the field was declared in the top level
     */
    class FieldDeclaration(
        val nameToken: Token,
        val explType: DatatypeNode,
        var initializer: AstNode,
        override val isConst: Boolean,
        val modifiers: List<Token>,
        override val isTopLevel: Boolean,
        relevantTokens: List<Token>
    ) : Field(relevantTokens) {

        override val name: String = nameToken.lexeme

        override val isStatic: Boolean
            get() {
                if (isTopLevel) return true
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "static") return true
                }
                return false
            }

        override val isPrivate: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "public") return false
                }
                return true
            }

        /**
         * used to set fieldType
         */
        lateinit var _fieldType: Datatype

        override val fieldType: Datatype
            get() = _fieldType

        override var clazz: ArtClass? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (initializer !== orig) throw CantSwapException()
            initializer = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents an array-creation statement (e.g. int[4]). The Parser only emits array-gets, because it can't
     * distinguish between array-accesses and array creations. This node is swapped into the tree by the TypeChecker
     * @param of the type of the array
     * @param amounts the size of the array in each dimension. The first value is the size of the array in the firs
     * dimension, the second value the size in the second dimension, etc.
     */
    class ArrayCreate(val of: Datatype, var amounts: Array<AstNode>, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            for (i in amounts.indices) if (amounts[i] === orig) {
                amounts[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represents an array literal (e.g. [1, 2, 3 ,4])
     * @param elements the elements of the literal
     * @param startToken the '[' token
     * @param endToken the ']' token
     */
    class ArrayLiteral(
        val elements: MutableList<AstNode>,
        relevantTokens: List<Token>
    ) : AstNode(relevantTokens) {

        override fun swap(orig: AstNode, to: AstNode) {
            for (i in elements.indices) if (elements[i] === orig) {
                elements[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * represent the yield-arrow in a block-expression (e.g. `let x = { => 45 }`)
     * @param expr the expression that results in the value of the block
     */
    class YieldArrow(var expr: AstNode, relevantTokens: List<Token>) : AstNode(relevantTokens) {

        /**
         * the type of [expr] and the type of the surrounding block
         */
        lateinit var yieldType: Datatype

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== expr) throw CantSwapException()
            expr = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    /**
     * the parser-representation of a datatype. Converted to a proper type by the TypeChecker
     * @param kind the kind of data
     */
    abstract class DatatypeNode(val kind: Datakind) {

//        /**
//         * true if the type is an array
//         */
//        var isArray: Boolean = false

        var arrayDims: Int = 0

        abstract override fun toString(): String
    }

    /**
     * represents a primitive type
     * @param kind the kind of primitive type
     */
    class PrimitiveTypeNode(kind: Datakind) : DatatypeNode(kind) {
        override fun toString(): String = kind.toString()
    }

    /**
     * represents an object type
     * @param identifier the identifier that refers to the object
     */
    class ObjectTypeNode(val identifier: Token) : DatatypeNode(Datakind.OBJECT) {
        override fun toString(): String = identifier.lexeme
    }

}

/**
 * represents the descriptor of a function
 * @param args the arguments of the function
 * @param returnType the returnType of the function
 */
data class FunctionDescriptor(val args: MutableList<Pair<String, Datatype>>, val returnType: Datatype) {

    /**
     * returns the [descriptor](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3)
     * of the function
     */
    fun getDescriptorString(): String {
        val builder = StringBuilder()
        builder.append("(")
        for (arg in args) if (arg.first != "this") builder.append(arg.second.descriptorType)
        builder.append(")")
        builder.append(returnType.descriptorType)
        return builder.toString()
    }

    /**
     * checks if this descriptor matches another descriptor (excluding return type)
     */
    fun matches(desc: FunctionDescriptor): Boolean {
        if (desc.args.size != args.size) return false
        for (i in desc.args.indices) if (args[i].first != "this" && desc.args[i].second != args[i].second) return false
        return true
    }

    /**
     * checks if a list of datatypes is compatible to the function-descriptor. If the function-descriptor has a `this`-parameter,
     * it is ignored. Assumes [other] has no `this`
     * @param other the list of datatypes
     */
    fun isCompatibleWith(other: List<Datatype>): Boolean {
        var argsNoThis: MutableList<Pair<String, Datatype>> = args
        if (args.isNotEmpty() && args[0].first == "this") {
            argsNoThis = args.toMutableList()
            argsNoThis.removeAt(0)
        }
        if (other.size != argsNoThis.size) return false
        for (i in other.indices) {
            if (other[i].kind != argsNoThis[i].second.kind) return false
            if (other[i].kind == Datakind.OBJECT) {
                return Datatype.StatClass((argsNoThis[i].second as Datatype.Object).clazz).isSuperClassOf((other[i] as Datatype.Object).clazz)
            }
        }
        return true
    }

}
