package compiler

import ast.AstNode
import ast.AstNodeVisitor
import classFile.ClassFileBuilder
import classFile.MethodBuilder
import classFile.StackMapTableAttribute
import tokenizer.TokenType
import classFile.StackMapTableAttribute.VerificationTypeInfo
import java.io.File
import passes.TypeChecker.Datatype
import java.util.*

class Compiler : AstNodeVisitor<Unit> {

    private var outdir: String = ""
    private var topLevelName: String = ""
    private var originFileName: String = ""
    private var curFile: String = ""

    private var stack: Stack<VerificationTypeInfo> = Stack()
    private var maxStack: Int = 0
    private var locals: MutableList<VerificationTypeInfo?> = mutableListOf()

    private var lastStackMapFrameOffset: Int = 0

    private var method: MethodBuilder? = null
    private var file: ClassFileBuilder? = null

    private var wasReturn: Boolean = false

    private var loopContinueAddress: Int = -1
    private var loopBreakAddressesToOverwrite = mutableListOf<Int>()

    private lateinit var curProgram: AstNode.Program

    fun compile(program: AstNode.Program, outdir: String, name: String) {
        this.outdir = outdir
        this.topLevelName = "$name\$\$ArtTopLevel"
        originFileName = name
        File(outdir).mkdirs() //create folders if they dont exist
        program.accept(this)
    }

    override fun visit(binary: AstNode.Binary) {
        if (binary.type == Datatype.Str()) {
            doStringConcat(binary)
            return
        }

        if (binary.operator.tokenType == TokenType.D_AND || binary.operator.tokenType == TokenType.D_OR) {
            doBooleanComparison(binary)
            return
        }

        comp(binary.left)
        comp(binary.right)

        when (binary.operator.tokenType) {
            TokenType.PLUS -> {
                emit(iadd)
                decStack()
            }
            TokenType.MINUS -> {
                emit(isub)
                decStack()
            }
            TokenType.STAR -> {
                emit(imul)
                decStack()
            }
            TokenType.SLASH -> {
                emit(idiv)
                decStack()
            }
            TokenType.MOD -> {
                emit(irem)
                decStack()
            }
            TokenType.GT -> doCompare(if_icmpgt)
            TokenType.GT_EQ -> doCompare(if_icmpge)
            TokenType.LT -> doCompare(if_icmplt)
            TokenType.LT_EQ -> doCompare(if_icmple)
            TokenType.D_EQ -> doCompare(if_icmpeq)
            TokenType.NOT_EQ -> doCompare(if_icmpne)
            else -> TODO("not yet implemented")
        }
    }

    private fun doStringConcat(exp: AstNode.Binary) {
        //TODO: this can be a lot more efficient
        val stringBuilderIndex = file!!.classInfo(file!!.utf8Info("java/lang/StringBuilder"))

        emit(new, *Utils.getLastTwoBytes(stringBuilderIndex))
        incStack(getObjVerificationType("java/lang/StringBuilder"))

        val initMethodInfo = file!!.methodRefInfo(
            stringBuilderIndex,
            file!!.nameAndTypeInfo(
                file!!.utf8Info("<init>"),
                file!!.utf8Info("()V")
            )
        )

        val appendMethodInfo = file!!.methodRefInfo(
            stringBuilderIndex,
            file!!.nameAndTypeInfo(
                file!!.utf8Info("append"),
                file!!.utf8Info("(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            )
        )

        val toStringMethodInfo = file!!.methodRefInfo(
            stringBuilderIndex,
            file!!.nameAndTypeInfo(
                file!!.utf8Info("toString"),
                file!!.utf8Info("()Ljava/lang/String;")
            )
        )

        emit(dup)
        incStack(getObjVerificationType("java/lang/StringBuilder"))
        emit(invokespecial, *Utils.getLastTwoBytes(initMethodInfo))
        decStack()

        comp(exp.left)
        emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
        decStack()
        comp(exp.right)
        emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
        decStack()
        emit(invokevirtual, *Utils.getLastTwoBytes(toStringMethodInfo))
        decStack()
        incStack(getObjVerificationType("java/lang/String"))
    }

    private fun doBooleanComparison(exp: AstNode.Binary) {
        val isAnd = exp.operator.tokenType == TokenType.D_AND
        comp(exp.left)
        emit(dup)
        incStack(VerificationTypeInfo.Integer())
        if (isAnd) emit(ifeq) else emit(ifne)
        decStack()
        emit(0x00.toByte(), 0x00.toByte())
        val jmpAddrPos = method!!.curCodeOffset - 2
        comp(exp.right)
        if (isAnd) emit(iand) else emit(ior)
        decStack()
        emitStackMapFrame()
        val jmpAddr = method!!.curCodeOffset - (jmpAddrPos - 1)
        method!!.overwriteByteCode(jmpAddrPos, *Utils.getLastTwoBytes(jmpAddr))
    }

    override fun visit(literal: AstNode.Literal) = when (literal.type) {
        Datatype.Integer() -> {
            emitIntLoad(literal.literal.literal as Int)
            incStack(VerificationTypeInfo.Integer())
        }
        Datatype.Str() -> {
            emitStringLoad(literal.literal.literal as String)
            incStack(getObjVerificationType("java/lang/String"))
        }
        Datatype.Bool() -> {
            if (literal.literal.literal as Boolean) emit(iconst_1)
            else emit(iconst_0)
            incStack(VerificationTypeInfo.Integer())
        }
        else -> TODO("not yet implemented")
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        comp(exprStmt.exp)
        if (exprStmt.exp is AstNode.FunctionCall && exprStmt.exp.type == Datatype.Void()) return
        emit(pop)
        decStack()
    }

    override fun visit(function: AstNode.Function) {
        stack = Stack()
        locals = MutableList(function.amountLocals) { null }
        for (i in function.functionDescriptor.args.indices) {
            locals[i] = when (function.functionDescriptor.args[i].second) {
                Datatype.Integer(), Datatype.Bool() -> VerificationTypeInfo.Integer()
                Datatype.Float() -> VerificationTypeInfo.Float()
                Datatype.Str() -> getObjVerificationType("java/lang/String")
                else -> TODO("not yet implemented")
            }
        }
        maxStack = 0
        lastStackMapFrameOffset = 0

        method = MethodBuilder()
//        method!!.isPublic = true
//        method!!.isStatic = true
        method!!.descriptor = function.functionDescriptor.getDescriptorString()
        method!!.name = function.name.lexeme

        comp(function.statements)

        method!!.maxStack = maxStack
        method!!.maxLocals = function.amountLocals

        if (method!!.name == "main") {
            method!!.descriptor = "([Ljava/lang/String;)V"
            if (method!!.maxLocals < 1) method!!.maxLocals = 1 //TODO: fix when adding parameters
            method!!.isPrivate = false
            method!!.isStatic = true
        }

        for (modifier in function.modifiers) when (modifier.tokenType) {
            TokenType.K_PUBLIC -> method!!.isPublic = true
            TokenType.K_STATIC -> method!!.isStatic = true
            TokenType.K_ABSTRACT -> method!!.isAbstract = true
            else -> TODO("not yet implemented")
        }
        method!!.isPrivate = !method!!.isPublic

        file!!.addMethod(method!!)
        if (function.functionDescriptor.returnType == Datatype.Void()) emit(_return)
        if (lastStackMapFrameOffset >= method!!.curCodeOffset) method!!.popStackMapFrame() //TODO: can probably be done better
    }

    override fun visit(program: AstNode.Program) {
        curProgram = program

        file = ClassFileBuilder()
        file!!.thisClass = topLevelName
        file!!.superClass = "java/lang/Object"
        file!!.isSuper = true
        file!!.isPublic = true
        curFile = file!!.thisClass

        for (func in program.funcs) comp(func)

        file!!.build("$outdir/$curFile.class")

        for (clazz in program.classes) {
            file = ClassFileBuilder()
            file!!.thisClass = clazz.name.lexeme
            file!!.superClass = "java/lang/Object"
            file!!.isSuper = true
            file!!.isPublic = true
            curFile = file!!.thisClass
            for (func in clazz.staticFuncs) comp(func)
            for (func in clazz.funcs) comp(func)
            doDefaultConstructor()
            file!!.build("$outdir/$curFile.class")
        }

    }

    override fun visit(print: AstNode.Print) {
        emit(getStatic)
        emit(*Utils.getLastTwoBytes(file!!.fieldRefInfo(
            file!!.classInfo(file!!.utf8Info("java/lang/System")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("out"),
                file!!.utf8Info("Ljava/io/PrintStream;")
            )
        )))
        incStack(getObjVerificationType("java/io/PrintStream"))
        comp(print.toPrint)
        emit(invokevirtual)

        val dataTypeToPrint = print.toPrint.type.descriptorType

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

    override fun visit(clazz: AstNode.ArtClass) {
        TODO("Not yet implemented")
    }

    override fun visit(variable: AstNode.Variable) {
        when (variable.type) {
            Datatype.Integer(), Datatype.Bool() -> {
                emitIntVarLoad(variable.index)
                incStack(VerificationTypeInfo.Integer())
            }
            Datatype.Str() -> {
                emitObjectVarLoad(variable.index)
                incStack(getObjVerificationType("java/lang/String"))
            }
            else -> TODO("not yet implemented")
        }
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        comp(varDec.initializer)
        when (varDec.initializer.type) {
            Datatype.Integer() , Datatype.Bool() -> {
                locals[varDec.index] = VerificationTypeInfo.Integer()
                emitIntVarStore(varDec.index)
            }
            Datatype.Str() -> {
                locals[varDec.index] = getObjVerificationType("java/lang/String")
                emitObjectVarStore(varDec.index)
            }
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(varAssign: AstNode.VariableAssignment) {
        comp(varAssign.toAssign)
        when (varAssign.toAssign.type) {
            Datatype.Integer() -> emitIntVarStore(varAssign.index)
            Datatype.Str() -> emitObjectVarStore(varAssign.index)
            Datatype.Bool() -> emitIntVarStore(varAssign.index)
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(loop: AstNode.Loop) {
        emitStackMapFrame()
        val before = method!!.curCodeOffset
        loopContinueAddress = before
        loopBreakAddressesToOverwrite = mutableListOf()
        comp(loop.body)
        val absOffset = (before - method!!.curCodeOffset)
        emitGoto(absOffset)
        emitStackMapFrame()
        val offset = method!!.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            method!!.overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }
    }

    override fun visit(block: AstNode.Block) {
        val before = locals.toMutableList()
        for (s in block.statements) {
            comp(s)
            if (stack.size != 0) {
                println(stack.size)
                emit(pop)
                decStack()
            }
        }
        locals = before
    }

    override fun visit(ifStmt: AstNode.If) {
        val hasElse = ifStmt.elseStmt != null

        comp(ifStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = method!!.curCodeOffset - 2

        wasReturn = false
        comp(ifStmt.ifStmt)
        val skipGoto = wasReturn
        wasReturn = false

        if (hasElse && !skipGoto) emit(_goto, 0x00.toByte(), 0x00.toByte())
        val elseJmpAddrOffset = method!!.curCodeOffset - 2
        val jmpAddr = method!!.curCodeOffset - (jmpAddrOffset - 1)
        method!!.overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        if (hasElse) emitStackMapFrame()
        if (hasElse) comp(ifStmt.elseStmt!!)
        val elseJmpAddr = method!!.curCodeOffset - (elseJmpAddrOffset - 1)
        if (hasElse && !skipGoto) method!!.overwriteByteCode(elseJmpAddrOffset, *Utils.getLastTwoBytes(elseJmpAddr))
        emitStackMapFrame()
    }

    override fun visit(group: AstNode.Group) {
        comp(group.grouped)
    }

    override fun visit(unary: AstNode.Unary) {
        comp(unary.on)
        when (unary.operator.tokenType) {
            TokenType.MINUS -> emit(ineg)
            TokenType.NOT -> {
                emitStackMapFrame()
                emit(ifeq, *Utils.getShortAsBytes(7.toShort()))
                decStack()
                emit(iconst_0, _goto, *Utils.getShortAsBytes(4.toShort()))
                emitStackMapFrame()
                emit(iconst_1)
                incStack(VerificationTypeInfo.Integer())
                emitStackMapFrame()
            }
            else -> TODO("not implemented")
        }
    }

    override fun visit(whileStmt: AstNode.While) {
        val startOffset = method!!.curCodeOffset
        loopContinueAddress = startOffset
        emitStackMapFrame()
        comp(whileStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = method!!.curCodeOffset - 2
        comp(whileStmt.body)
        emit(_goto, *Utils.getShortAsBytes((startOffset - method!!.curCodeOffset).toShort()))
        val jmpAddr = method!!.curCodeOffset - (jmpAddrOffset - 1)
        method!!.overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        emitStackMapFrame()
        val offset = method!!.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            method!!.overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }
    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        for (arg in funcCall.arguments) comp(arg)

        val methodRefIndex: Int

        if (funcCall.definition.clazz == null) {
            methodRefIndex = file!!.methodRefInfo(
                file!!.classInfo(file!!.utf8Info(topLevelName)),
                file!!.nameAndTypeInfo(
                    file!!.utf8Info((funcCall.func as Either.Right).value.lexeme),
                    file!!.utf8Info(funcCall.definition.functionDescriptor.getDescriptorString())
                )
            )
        } else {
            methodRefIndex = file!!.methodRefInfo(
                file!!.classInfo(file!!.utf8Info(funcCall.definition.clazz!!.name.lexeme)),
                file!!.nameAndTypeInfo(
                    file!!.utf8Info(funcCall.definition.name.lexeme),
                    file!!.utf8Info(funcCall.definition.functionDescriptor.getDescriptorString())
                )
            )
        }

        emit(invokestatic, *Utils.getLastTwoBytes(methodRefIndex))
        repeat(funcCall.arguments.size) { decStack() }
        when (funcCall.type) {
            Datatype.Integer(), Datatype.Bool() -> incStack(VerificationTypeInfo.Integer())
            Datatype.Float() -> incStack(VerificationTypeInfo.Float())
            Datatype.Str() -> incStack(getObjVerificationType("java/lang/String"))
            else -> { }
        }
    }

    override fun visit(returnStmt: AstNode.Return) {
        if (returnStmt.toReturn == null) {
            emit(_return)
            return
        }
        comp(returnStmt.toReturn!!)
        emit(when (returnStmt.toReturn!!.type) {
            Datatype.Str() -> areturn
            Datatype.Integer(), Datatype.Bool() -> ireturn
            else -> TODO("not yet implemented")
        })
        decStack()
        wasReturn = true
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        emit(iinc, (varInc.index and 0xFF).toByte(), varInc.toAdd)
    }

    override fun visit(walrus: AstNode.WalrusAssign) {
        comp(walrus.toAssign)
        emit(dup)
        incStack(stack.peek())
        when (walrus.type) {
            Datatype.Integer() -> emitIntVarStore(walrus.index)
            Datatype.Str() -> emitObjectVarStore(walrus.index)
            Datatype.Bool() -> emitIntVarStore(walrus.index)
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(get: AstNode.Get) {
        TODO("Not yet implemented")
    }

    override fun visit(set: AstNode.Set) {
        TODO("Not yet implemented")
    }

    override fun visit(walrus: AstNode.WalrusSet) {
        TODO("Not yet implemented")
    }

    override fun visit(cont: AstNode.Continue) {
        emitGoto(loopContinueAddress - method!!.curCodeOffset)
    }

    override fun visit(breac: AstNode.Break) {
        emit(_goto, 0x00, 0x00)
        loopBreakAddressesToOverwrite.add(method!!.curCodeOffset - 2)
    }

    override fun visit(constructorCall: AstNode.ConstructorCall) {
        TODO("Not yet implemented")
    }

    private fun doDefaultConstructor() {
        method = MethodBuilder()
        method!!.isPublic = true
        method!!.isSynthetic = true
        method!!.descriptor = "()V"
        method!!.name = "<init>"
        method!!.maxStack = 1
        method!!.maxLocals = 1

        val objConstructorIndex = file!!.methodRefInfo(
            file!!.classInfo(file!!.utf8Info("java/lang/Object")),
            file!!.nameAndTypeInfo(
                file!!.utf8Info("<init>"),
                file!!.utf8Info("()V")
            )
        )
        emit(
            aload_0,
            invokespecial,
            *Utils.getLastTwoBytes(objConstructorIndex),
            _return
        )
        file!!.addMethod(method!!)
    }

    private fun doCompare(compareInstruction: Byte) {
        emitStackMapFrame()
        emit(compareInstruction, *Utils.getShortAsBytes(7.toShort()))
        decStack(); decStack()
        emit(iconst_0, _goto, *Utils.getShortAsBytes(4.toShort()))
        emitStackMapFrame()
        emit(iconst_1)
        incStack(VerificationTypeInfo.Integer())
        emitStackMapFrame()
    }

    private fun emitGoto(offset: Int) {
        if (offset !in Short.MIN_VALUE..Short.MAX_VALUE) emit(goto_w, *Utils.getIntAsBytes(offset))
        else emit(_goto, *Utils.getShortAsBytes(offset.toShort()))
    }

    private fun emitStackMapFrame() {

        //TODO: instead of always emitting FullStackMapFrames also use other types of StackMapFrames

        val offsetDelta = if (lastStackMapFrameOffset == 0) method!!.curCodeOffset
                            else (method!!.curCodeOffset - lastStackMapFrameOffset) - 1

        if (offsetDelta < 0) return //frame already exists at this offset

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
        in Short.MIN_VALUE..Short.MAX_VALUE -> emit(sipush, *Utils.getShortAsBytes(i.toShort()))
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


    private fun comp(node: AstNode) {
        node.accept(this)
    }

    private fun getObjVerificationType(clazz: String): VerificationTypeInfo {
        return VerificationTypeInfo.ObjectVariable(file!!.classInfo(file!!.utf8Info(clazz)))
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
        const val ineg: Byte = 0x74.toByte()
        const val irem: Byte = 0x70.toByte()
        const val iand: Byte = 0x7E.toByte()
        const val ior: Byte = 0x80.toByte()
        const val iinc: Byte = 0x84.toByte()

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
        const val dup: Byte = 0x59.toByte()

        const val _goto: Byte = 0xA7.toByte()
        const val goto_w: Byte = 0xC8.toByte()

        const val getStatic: Byte = 0xB2.toByte()

        const val new: Byte = 0xBB.toByte()

        const val nop: Byte = 0x00.toByte()

        const val invokevirtual: Byte = 0xB6.toByte()
        const val invokespecial: Byte = 0xB7.toByte()
        const val invokestatic: Byte = 0xB8.toByte()

        const val wide: Byte = 0xC4.toByte()

        const val _return: Byte = 0xB1.toByte()
        const val areturn: Byte = 0xB0.toByte()
        const val ireturn: Byte = 0xAC.toByte()

        const val if_icmpeq: Byte = 0x9F.toByte()
        const val if_icmpge: Byte = 0xA2.toByte()
        const val if_icmpgt: Byte = 0xA3.toByte()
        const val if_icmple: Byte = 0xA4.toByte()
        const val if_icmplt: Byte = 0xA1.toByte()
        const val if_icmpne: Byte = 0xA0.toByte()

        const val ifeq: Byte = 0x99.toByte()
        const val ifge: Byte = 0x9C.toByte()
        const val ifgt: Byte = 0x9D.toByte()
        const val ifle: Byte = 0x9E.toByte()
        const val iflt: Byte = 0x9B.toByte()
        const val ifne: Byte = 0x9A.toByte()
    }
}
