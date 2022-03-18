package ast

import tokenizer.Token
import passes.TypeChecker.Datatype
import tokenizer.TokenType
import passes.TypeChecker.Datakind

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

    class Function(
        var statements: Block,
        val name: Token,
        val modifiers: List<Token>,
        val isTopLevel: Boolean
    ) : AstNode() {

        var amountLocals: Int = 0
        var args: MutableList<Pair<Token, DatatypeNode>> = mutableListOf()
        var returnType: DatatypeNode? = null

        lateinit var functionDescriptor: FunctionDescriptor

        var clazz: ArtClass? = null

        val isStatic: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "static") return true
                }
                return false
            }

        val isPrivate: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "public") return false
                }
                return true
            }

        val hasThis: Boolean
            get() = !isStatic && !isTopLevel

        override fun swap(orig: AstNode, to: AstNode) {
            if (statements !== orig || to !is Block) throw CantSwapException()
            statements = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Program(
        val funcs: MutableList<Function>,
        val classes: MutableList<ArtClass>,
        val fields: MutableList<FieldDeclaration>
    ) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (to is Function) for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            if (to is ArtClass) for (i in classes.indices) if (classes[i] === orig) {
                classes[i] = to
                return
            }
            if (to is FieldDeclaration) for (i in fields.indices) if (fields[i] === orig) {
                fields[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class ArtClass(
        val name: Token,
        val staticFuncs: MutableList<Function>,
        val funcs: MutableList<Function>,
        val fields: MutableList<FieldDeclaration>,
        val staticFields: MutableList<FieldDeclaration>
    ) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (to is Function) for (i in staticFuncs.indices) if (staticFuncs[i] === orig) {
                staticFuncs[i] = to
                return
            }
            if (to is Function) for (i in funcs.indices) if (funcs[i] === orig) {
                funcs[i] = to
                return
            }
            if (to is FieldDeclaration) for (i in fields.indices) if (fields[i] === orig) {
                fields[i] = to
                return
            }
            if (to is FieldDeclaration) for (i in staticFields.indices) if (staticFields[i] === orig) {
                staticFields[i] = to
                return
            }
            throw CantSwapException()
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Print(var toPrint: AstNode) : AstNode() {

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== toPrint) throw CantSwapException()
            toPrint = to
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
//        var typeToken: Token? = null
        var explType: DatatypeNode? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (orig !== initializer) throw CantSwapException()
            initializer = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Assignment(val name: Get, var toAssign: AstNode) : AstNode() {

        var index: Int = -1

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

    class VarIncrement(val name: Get, val toAdd: Byte) : AstNode() {

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

    class FunctionCall(var func: Get, val arguments: MutableList<AstNode>) : AstNode() {

        lateinit var definition: Function

        fun getFullName(): String {
            return AstPrinter().visit(func)
        }

        override fun swap(orig: AstNode, to: AstNode) {
            if (func === orig && to is Get) {
                func = to
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

    class WalrusAssign(val name: Get, var toAssign: AstNode) : AstNode() {

        var index: Int = 0

        override fun swap(orig: AstNode, to: AstNode) {
            if (toAssign !== orig) throw CantSwapException()
            toAssign = to
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Get(val name: Token, var from: AstNode?) : AstNode() {

        var fieldDef: FieldDeclaration? = null

        override fun swap(orig: AstNode, to: AstNode) {
            if (from !== orig) throw CantSwapException()
            from = to
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

    class FieldDeclaration(
        val name: Token,
        val explType: DatatypeNode,
        var initializer: AstNode,
        val isConst: Boolean,
        val modifiers: List<Token>,
        val isTopLevel: Boolean
    ) : AstNode() {

        val isStatic: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "static") return true
                }
                return false
            }

        val isPrivate: Boolean
            get() {
                for (modifier in modifiers) {
                    if (modifier.tokenType == TokenType.IDENTIFIER && modifier.lexeme == "public") return false
                }
                return true
            }

        override fun swap(orig: AstNode, to: AstNode) {
            if (initializer !== orig) throw CantSwapException()
            initializer = to
        }

        var fieldType: Datatype = Datatype.Void()
        var clazz: ArtClass? = null

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    abstract class DatatypeNode(val kind: Datakind)
    class PrimitiveTypeNode(kind: Datakind) : DatatypeNode(kind)
    class ObjectTypeNode(val identifier: Token) : DatatypeNode(Datakind.OBJECT)

}

data class FunctionDescriptor(val args: MutableList<Pair<String, Datatype>>, val returnType: Datatype) {

    fun getDescriptorString(): String {
        val builder = StringBuilder()
        builder.append("(")
        for (arg in args) if (arg.first != "this") builder.append(arg.second.descriptorType)
        builder.append(")")
        builder.append(returnType.descriptorType)
        return builder.toString()
    }

    fun matches(desc: FunctionDescriptor): Boolean {
        if (desc.args.size != args.size) return false
        for (i in desc.args.indices) if (desc.args[i].second != args[i].second) return false
        return true
    }

}
