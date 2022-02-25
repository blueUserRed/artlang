package compiler

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import classFile.ClassFileBuilder
import classFile.MethodBuilder
import classFile.StackMapTableAttribute
import tokenizer.TokenType
import passes.TypeChecker.Datatype
import classFile.StackMapTableAttribute.VerificationTypeInfo
import java.util.*

class Compiler : StatementVisitor<Unit>, ExpressionVisitor<Unit> {

    private var outdir: String = ""
    private var name: String = ""
    private var curFile: String = ""

//    private var curStack: Int = 0
    private var stack: Stack<VerificationTypeInfo> = Stack()
    private var maxStack: Int = 0
    private var locals: MutableList<VerificationTypeInfo?> = mutableListOf()

    private var lastStackMapFrameOffset: Int = 0

    private var method: MethodBuilder? = null
    private var file: ClassFileBuilder? = null

    fun compile(program: Statement.Program, outdir: String, name: String) {
        this.outdir = outdir
        this.name = name
        program.accept(this)
    }

    override fun visit(exp: Expression.Binary) {
        comp(exp.left)
        comp(exp.right)
        when (exp.operator.tokenType) {
            TokenType.PLUS -> emit(iadd)
            TokenType.MINUS -> emit(isub)
            TokenType.STAR -> emit(imul)
            TokenType.SLASH -> emit(idiv)
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(exp: Expression.Literal) {
        if (exp.type == Datatype.INT) {
            emitIntLoad(exp.literal.literal as Int)
            incStack(VerificationTypeInfo.INTEGER)
        }
        else if (exp.type == Datatype.STRING) {
            emitStringLoad(exp.literal.literal as String)
            incStack(getObjVerificationType("java/lang/String"))
        }
    }

    override fun visit(stmt: Statement.ExpressionStatement) {
        comp(stmt.exp)
        emit(pop)
        decStack()
    }

    override fun visit(stmt: Statement.Function) {
//        curStack = 0
        stack = Stack()
        locals = MutableList(stmt.amountLocals) { null }
        maxStack = 0

        method = MethodBuilder()
        method!!.isPublic = true
        method!!.isStatic = true
        method!!.descriptor = "()V"
        method!!.name = stmt.name.lexeme

        comp(stmt.statements)

        method!!.maxStack = maxStack
        method!!.maxLocals = stmt.amountLocals

        if (method!!.name == "main") {
            method!!.descriptor = "([Ljava/lang/String;)V"
            if (method!!.maxLocals < 1) method!!.maxLocals = 1 //TODO: fix when adding parameters
        }

        file!!.addMethod(method!!)
        emit(_return)
    }

    override fun visit(stmt: Statement.Program) {
        file = ClassFileBuilder()
        file!!.thisClass = "$name\$\$ArtTopLevel"
        file!!.superClass = "java/lang/Object"
        file!!.isSuper = true
        file!!.isPublic = true
        curFile = file!!.thisClass

        for (func in stmt.funcs) comp(func)

        file!!.build("$outdir/$curFile.class")
    }

    override fun visit(stmt: Statement.Print) {
        emit(getStatic)
        emit(*Utils.getLastTwoBytes(file!!.fieldRefInfo(
            file!!.classInfo(file!!.utf8Info("java/lang/System")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("out"),
                file!!.utf8Info("Ljava/io/PrintStream;")
            )
        )))
        incStack(getObjVerificationType("java/io/PrintStream"))
        comp(stmt.toPrint)
        emit(invokevirtual)

        val dataTypeToPrint = when (stmt.toPrint.type) {
            Datatype.INT -> "I"
            Datatype.FLOAT -> "F"
            Datatype.STRING -> "Ljava/lang/String;"
            else -> TODO("not yet implemented")
        }

        emit(*Utils.getLastTwoBytes(file!!.methodRefInfo(
            file!!.classInfo(file!!.utf8Info("java/io/PrintStream")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("println"),
                file!!.utf8Info("($dataTypeToPrint)V")
            )
        )))
        decStack()
        decStack()
    }

    override fun visit(exp: Expression.Variable) {
        when (exp.type) {
            Datatype.INT -> {
                emitIntVarLoad(exp.index)
                incStack(VerificationTypeInfo.INTEGER)
            }
            Datatype.STRING -> {
                emitObjectVarLoad(exp.index)
                incStack(getObjVerificationType("java/lang/String"))
            }
            else -> TODO("not yet implemented")
        }
    }

    override fun visit(stmt: Statement.VariableDeclaration) {
        comp(stmt.initializer)
        when (stmt.type) {
            Datatype.INT -> {
                locals[stmt.index] = VerificationTypeInfo.INTEGER
                emitIntVarStore(stmt.index)
            }
            Datatype.STRING -> {
                locals[stmt.index] = getObjVerificationType("java/lang/String")
                emitObjectVarStore(stmt.index)
            }
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(stmt: Statement.VariableAssignment) {
        comp(stmt.expr)
        when (stmt.type) {
            Datatype.INT -> emitIntVarStore(stmt.index)
            Datatype.STRING -> emitObjectVarStore(stmt.index)
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(stmt: Statement.Loop) {
        emitStackMapFrame()
        val before = method!!.curCodeOffset
        comp(stmt.stmt)
        val absOffset = (before - method!!.curCodeOffset)
        emitGoto(absOffset)
        emitStackMapFrame()
    }

    override fun visit(stmt: Statement.Block) {
        val before = locals.toMutableList()
        for (s in stmt.statements) comp(s)
        locals = before
    }

    override fun visit(stmt: Statement.If) {
        TODO("not yet implemented")
    }

    override fun visit(exp: Expression.Group) {
        TODO("Not yet implemented")
    }

    override fun visit(exp: Expression.Unary) {
        TODO("Not yet implemented")
    }

    private fun emitGoto(offset: Int) {
        if (offset !in Short.MIN_VALUE..Short.MAX_VALUE) emit(goto_w, *Utils.getIntAsBytes(offset))
        else emit(_goto, *Utils.getShortAsBytes(offset.toShort()))
    }

    private fun emitStackMapFrame() {

        //TODO: instead of always emitting FullStackMapFrames also use other types of StackMapFrames

        val offsetDelta = if (lastStackMapFrameOffset == 0) method!!.curCodeOffset
                            else (method!!.curCodeOffset - lastStackMapFrameOffset) - 1

        val frame = StackMapTableAttribute.FullStackMapFrame(offsetDelta)

        val newLocals = mutableListOf<VerificationTypeInfo>()
        for (local in locals) if (local != null) newLocals.add(local)

        frame.locals = newLocals
        frame.stack = stack.toMutableList()
        lastStackMapFrameOffset = method!!.curCodeOffset
        method!!.addStackMapFrame(frame)
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
        in Short.MIN_VALUE..Short.MAX_VALUE -> emit(sipush, *Utils.getLastTwoBytes(i))
        else -> emitLdc(file!!.integerInfo(i))
    }

    private fun emitIntVarLoad(index: Int) = when (index) {
        0 -> emit(iload_0)
        1 -> emit(iload_1)
        2 -> emit(iload_2)
        3 -> emit(iload_3)
        else -> emit(iload, (index and 0xFF).toByte()) //TODO: wide
    }

    private fun emitIntVarStore(index: Int) = when (index) {
        0 -> emit(istore_0)
        1 -> emit(istore_1)
        2 -> emit(istore_2)
        3 -> emit(istore_3)
        else -> emit(istore, (index and 0xFF).toByte()) //TODO: wide
    }

    private fun emitObjectVarLoad(index: Int) = when (index) {
        0 -> emit(aload_0)
        1 -> emit(aload_1)
        2 -> emit(aload_2)
        3 -> emit(aload_3)
        else -> emit(aload, (index and 0xFF).toByte()) //TODO: wide
    }

    private fun emitObjectVarStore(index: Int) = when (index) {
        0 -> emit(astore_0)
        1 -> emit(astore_1)
        2 -> emit(astore_2)
        3 -> emit(astore_3)
        else -> emit(astore, (index and 0xFF).toByte()) //TODO: wide
    }

    private fun emitLdc(index: Int) {
        if (index <= 255) emit(ldc, (index and 0xFF).toByte())
        else emit(ldc_w, *Utils.getLastTwoBytes(index))
    }

    private fun emitStringLoad(s: String) = emitLdc(file!!.stringInfo(file!!.utf8Info(s)))


    private fun comp(exp: Expression){
        exp.accept(this)
        assert(stack.size == 1)
//        assert(curStack == 1)
    }

    private fun comp(stmt: Statement) {
        stmt.accept(this)
        assert(stack.size == 0)
//        assert(curStack == 0)
    }

    private fun getObjVerificationType(clazz: String): VerificationTypeInfo {
        val type = VerificationTypeInfo.OBJECT_VARIABLE
        type.classIndex = file!!.classInfo(file!!.utf8Info(clazz))
        return type
    }

    private fun emit(vararg bytes: Byte) = method!!.emitByteCode(*bytes)

    private fun incStack(type: VerificationTypeInfo) {
        stack.push(type)
        if (stack.size > maxStack) maxStack = stack.size
//        curStack++
//        if (curStack > maxStack) maxStack = curStack
    }

    private fun decStack() {
//        curStack--
        stack.pop()
    }




    companion object {

        const val iadd: Byte = 0x60.toByte()
        const val isub: Byte = 0x64.toByte()
        const val imul: Byte = 0x68.toByte()
        const val idiv: Byte = 0x6C.toByte()

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

        const val iload: Byte = 0x15.toByte()
        const val iload_0: Byte = 0x1a.toByte()
        const val iload_1: Byte = 0x1b.toByte()
        const val iload_2: Byte = 0x1c.toByte()
        const val iload_3: Byte = 0x1d.toByte()

        const val istore: Byte = 0x36.toByte()
        const val istore_0: Byte = 0x3b.toByte()
        const val istore_1: Byte = 0x3c.toByte()
        const val istore_2: Byte = 0x3d.toByte()
        const val istore_3: Byte = 0x3e.toByte()

        const val aload: Byte = 0x19.toByte()
        const val aload_0: Byte = 0x2a.toByte()
        const val aload_1: Byte = 0x2b.toByte()
        const val aload_2: Byte = 0x2c.toByte()
        const val aload_3: Byte = 0x2d.toByte()

        const val astore: Byte = 0x3a.toByte()
        const val astore_0: Byte = 0x4b.toByte()
        const val astore_1: Byte = 0x4c.toByte()
        const val astore_2: Byte = 0x4d.toByte()
        const val astore_3: Byte = 0x4e.toByte()

        const val pop: Byte = 0x57.toByte()
        const val pop2: Byte = 0x58.toByte()

        const val _goto: Byte = 0xA7.toByte()
        const val goto_w: Byte = 0xC8.toByte()

        const val getStatic: Byte = 0xB2.toByte()

        const val invokevirtual: Byte = 0xB6.toByte()

        const val wide: Byte = 0xC4.toByte()

        const val _return: Byte = 0xB1.toByte()
    }
}
