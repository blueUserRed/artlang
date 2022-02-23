package compiler

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import classFile.ClassFileBuilder
import classFile.MethodBuilder
import tokenizer.TokenType

class Compiler : StatementVisitor<Void?>, ExpressionVisitor<Void?> {

    private var outdir: String = ""
    private var name: String = ""
    private var curFile: String = ""

    private var curStack: Int = 0
    private var maxStack: Int = 0

    private var method: MethodBuilder? = null
    private var file: ClassFileBuilder? = null

    fun compile(program: Statement.Program, outdir: String, name: String) {
        this.outdir = outdir
        this.name = name
        program.accept(this)
    }

    override fun visit(exp: Expression.Binary): Void? {
        compExp(exp.left)
        compExp(exp.right)
        if (exp.operator.tokenType == TokenType.PLUS) emit(iadd)
        else TODO("not yet implemented")
        decStack()
        return null
    }

    override fun visit(exp: Expression.Literal): Void? {
        if (exp.literal.tokenType != TokenType.INT) TODO("not yet implemented")
        emitIntLoad(exp.literal.literal as Int)
        incStack()
        return null
    }

    override fun visit(stmt: Statement.ExpressionStatement): Void? {
        compExp(stmt.exp)
        emit(pop)
        decStack()
        return null
    }

    override fun visit(stmt: Statement.Function): Void? {
        curStack = 0
        maxStack = 0

        method = MethodBuilder()
        method!!.isPublic = true
        method!!.isStatic = true
        method!!.descriptor = "()V"
        method!!.name = stmt.name.lexeme

        compStmt(stmt.statements)

        method!!.maxStack = maxStack

        if (method!!.name == "main") {
            method!!.descriptor = "([Ljava/lang/String;)V"
            method!!.maxLocals = 1
        }

        file!!.addMethod(method!!)
        emit(_return)

        return null
    }

    override fun visit(stmt: Statement.Program): Void? {
        file = ClassFileBuilder()
        file!!.thisClass = "$name\$\$ArtTopLevel"
        file!!.superClass = "java/lang/Object"
        file!!.isSuper = true
        file!!.isPublic = true
        curFile = file!!.thisClass

        for (func in stmt.funcs) compStmt(func)

        file!!.build("$outdir/$curFile.class")

        return null
    }

    override fun visit(stmt: Statement.Print): Void? {
        emit(getStatic)
        emit(*Utils.getLastTwoBytes(file!!.fieldRefInfo(
            file!!.classInfo(file!!.utf8Info("java/lang/System")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("out"),
                file!!.utf8Info("Ljava/io/PrintStream;")
            )
        )))
        incStack()
        compExp(stmt.toPrint)
        emit(invokevirtual)
        val dataTypeToPrint = "I" //TODO: in typechecker, figure out which type to print
        emit(*Utils.getLastTwoBytes(file!!.methodRefInfo(
            file!!.classInfo(file!!.utf8Info("java/io/PrintStream")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("println"),
                file!!.utf8Info("($dataTypeToPrint)V")
            )
        )))
        decStack()
        decStack()
        return null
    }

    override fun visit(stmt: Statement.Block): Void? {
        for (s in stmt.statements) compStmt(s)
        return null
    }

    private fun emitIntLoad(i: Int) = when (i) {
        -1 -> emit(iconst_m1)
        0 -> emit(iconst_0)
        1 -> emit(iconst_1)
        2 -> emit(iconst_2)
        3 -> emit(iconst_3)
        4 -> emit(iconst_4)
        5 -> emit(iconst_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> emit(bipush, (i and 0xFF).toByte())
        in Short.MIN_VALUE..Short.MAX_VALUE -> emit(sipush, ((i and 0xFF00) shr 8).toByte(), (i and 0xFF).toByte())
        else -> emitLdc(file!!.integerInfo(i))
    }

    private fun emitLdc(index: Int) {
        if (index <= 255) emit(ldc, (index and 0xFF).toByte())
        else emit(ldc_w, ((index and 0xFF00) shr 8).toByte(), (index and 0xFF).toByte())
    }

    private fun compExp(exp: Expression){
        exp.accept(this)
        assert(curStack == 1)
    }

    private fun compStmt(stmt: Statement) {
        stmt.accept(this)
        assert(curStack == 0)
    }

    private fun emit(vararg bytes: Byte) = method!!.emitByteCode(*bytes)

    private fun incStack() {
        curStack++
        if (curStack > maxStack) maxStack = curStack
    }

    private fun decStack() {
        curStack--
    }




    companion object {

        const val iadd: Byte = 0x60.toByte()

        const val iconst_m1: Byte = 0x02.toByte()
        const val iconst_0: Byte = 0x03.toByte()
        const val iconst_1: Byte = 0x04.toByte()
        const val iconst_2: Byte = 0x05.toByte()
        const val iconst_3: Byte = 0x06.toByte()
        const val iconst_4: Byte = 0x07.toByte()
        const val iconst_5: Byte = 0x08.toByte()

        const val bipush: Byte = 0x10.toByte()
        const val sipush: Byte = 0x11.toByte()

        const val ldc: Byte = 0x12.toByte()
        const val ldc_w: Byte = 0x13.toByte()
        const val ldc2_w: Byte = 0x14.toByte()

        const val pop: Byte = 0x57.toByte()
        const val pop2: Byte = 0x58.toByte()

        const val getStatic: Byte = 0xB2.toByte()

        const val invokevirtual: Byte = 0xB6.toByte()

        const val _return: Byte = 0xB1.toByte()
    }

}