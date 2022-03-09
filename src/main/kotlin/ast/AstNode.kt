package ast
import Either
import tokenizer.Token
import passes.TypeChecker.Datatype
import tokenizer.TokenType

abstract class AstNode {

    var type: Datatype = Datatype.Void()

    abstract fun swap(orig: AstNode, to: AstNode)

    class CantSwapException : RuntimeException()

    abstract fun <T> accept(visitor: AstNodeVisitor<T>): T


    class ExpressionStatement(var exp: AstNode) : AstNode() { //TODO: necessary?

        override fun swap(orig: AstNode, to: AstNode) {
            if (exp !== orig) throw CantSwapException()
            exp = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Function(var statements: Block, val name: Token, val modifiers: List<Token>) : AstNode() {

        var amountLocals: Int = 0
        var argTokens: MutableList<Pair<Token, Token>> = mutableListOf()
        var returnTypeToken: Token? = null

        lateinit var functionDescriptor: FunctionDescriptor

        var clazz: ArtClass? = null

        val isStatic: Boolean
            get() {
                for (modifier in modifiers) if (modifier.tokenType == TokenType.K_STATIC) return true
                return false
            }

        val isPrivate: Boolean
            get() {
                for (modifier in modifiers) if (modifier.tokenType == TokenType.K_PUBLIC) return false
                return true
            }

        override fun swap(orig: AstNode, to: AstNode) {
            if (statements !== orig || to !is Block) throw CantSwapException()
            statements = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Program(val funcs: Array<Function>, val classes: Array<ArtClass>) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (to is Function) for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            if (to is ArtClass) for (i in classes.indices) if (classes[i] === orig) {
                classes[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class ArtClass(val name: Token, val staticFuncs: Array<Function>, val funcs: Array<Function>) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (to !is Function) throw CantSwapException()
            for (i in staticFuncs.indices) if (staticFuncs[i] === orig) {
                staticFuncs[i] = to
                return
            }
            for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Print(var toPrint: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== toPrint) throw CantSwapException()
            toPrint = orig
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Block(val statements: Array<AstNode>) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            for (i in statements.indices) if (statements[i] === orig) {
                statements[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VariableDeclaration(val name: Token, var initializer: AstNode, val isConst: Boolean) : AstNode() {

        var index: Int = 0
        var typeToken: Token? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== initializer) throw CantSwapException()
            initializer = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VariableAssignment(val name: Token, var toAssign: AstNode) : AstNode() {

        var index: Int = 0

        override fun swap(orig: AstNode, to: AstNode) {
            if (toAssign !== orig) throw CantSwapException()
            toAssign = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Loop(var body: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (body !== orig) throw CantSwapException()
            body = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class If(var ifStmt: AstNode, var elseStmt: AstNode?, var condition: AstNode) : AstNode() {

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

    class While(var body: AstNode, var condition: AstNode) : AstNode() {

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

    class Return(var toReturn: AstNode?) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (toReturn !== orig) throw CantSwapException()
            toReturn = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VarIncrement(val name: Token, val toAdd: Byte) : AstNode() {

        var index: Int = 0

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Binary(var left: AstNode, val operator: Token, var right: AstNode) : AstNode() {

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

    class Literal(val literal: Token) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Variable(val name: Token) : AstNode() {

        var index: Int = -1

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Group(var grouped: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (grouped !== orig) throw CantSwapException()
            grouped = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Unary(var on: AstNode, val operator: Token) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (on !== orig) throw CantSwapException()
            on = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class FunctionCall(var func: Either<AstNode, Token>, val arguments: MutableList<AstNode>) : AstNode() {

        lateinit var definition: Function

        fun getFullName(): String {
            return if (func is Either.Left) (func as Either.Left<AstNode>).value.accept(ASTPrinter())
            else (func as Either.Right).value.lexeme
        }

        override fun swap(orig: AstNode, to: AstNode) {
            if (func is Either.Left && (func as Either.Left).value === orig) {
                func = Either.Left(to)
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

    class WalrusAssign(val name: Token, var toAssign: AstNode) : AstNode() {

        var index: Int = 0

        override fun swap(orig: AstNode, to: AstNode) {
            if (toAssign !== orig) throw CantSwapException()
            toAssign = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Get(var from: AstNode, val name: Token) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (from !== orig) throw CantSwapException()
            from = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Set(var from: AstNode, var to: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
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

    class WalrusSet(var from: AstNode, var to: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (from === orig) {
                from = to
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

    class Break : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Continue : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class ConstructorCall(var clazz: ArtClass, val arguments: MutableList<AstNode>) : AstNode() {

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

}

data class FunctionDescriptor(val args: MutableList<Pair<String, Datatype>>, val returnType: Datatype) {

    fun getDescriptorString(): String {
        val builder = StringBuilder()
        builder.append("(")
        for (arg in args) builder.append(arg.second.descriptorType)
        builder.append(")")
        builder.append(returnType.descriptorType)
        return builder.toString()
    }

}
