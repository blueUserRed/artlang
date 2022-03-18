package compiler

import Utils
import ast.AstNode
import ast.AstNodeVisitor
import classFile.ClassFileBuilder
import classFile.Field
import classFile.MethodBuilder
import classFile.StackMapTableAttribute
import classFile.StackMapTableAttribute.VerificationTypeInfo
import passes.TypeChecker.Datakind
import passes.TypeChecker.Datatype
import tokenizer.TokenType
import java.io.File
import java.util.*

//TODO: there are way to many todos in here
class Compiler : AstNodeVisitor<Unit> {

    private var outdir: String = ""
    private var topLevelName: String = ""
    private var originFileName: String = ""
    private var curFile: String = ""
    private var curClassName: String = ""
    private var isTopLevel: Boolean = false

//    private var stack: Stack<VerificationTypeInfo> = Stack()
//    private var maxStack: Int = 0
//    private var locals: MutableList<VerificationTypeInfo?> = mutableListOf()
//
//    private var lastStackMapFrameOffset: Int = 0

    private var method: MethodEmitter? = null
    private lateinit var file: ClassFileBuilder
    private lateinit var clinit: MethodEmitter
    private lateinit var init: MethodEmitter

    private var wasReturn: Boolean = false

    private var loopContinueAddress: Int = -1
    private var loopBreakAddressesToOverwrite = mutableListOf<Int>()

    private lateinit var emitterTarget: EmitterTarget

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

        if (!binary.type.matches(Datakind.INT)) TODO("not yet implemented")

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
        val stringBuilderIndex = file.classInfo(file.utf8Info("java/lang/StringBuilder"))

        emit(new, *Utils.getLastTwoBytes(stringBuilderIndex))
        incStack(getObjVerificationType("java/lang/StringBuilder"))

        val initMethodInfo = file.methodRefInfo(
            stringBuilderIndex,
            file.nameAndTypeInfo(
                file.utf8Info("<init>"),
                file.utf8Info("()V")
            )
        )

        val appendMethodInfo = file.methodRefInfo(
            stringBuilderIndex,
            file.nameAndTypeInfo(
                file.utf8Info("append"),
                file.utf8Info("(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            )
        )

        val toStringMethodInfo = file.methodRefInfo(
            stringBuilderIndex,
            file.nameAndTypeInfo(
                file.utf8Info("toString"),
                file.utf8Info("()Ljava/lang/String;")
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
        incStack(Datatype.Str())
    }

    private fun doBooleanComparison(exp: AstNode.Binary) {
        val isAnd = exp.operator.tokenType == TokenType.D_AND
        comp(exp.left)
        emit(dup)
        incStack(Datatype.Bool())
        if (isAnd) emit(ifeq) else emit(ifne)
        decStack()
        emit(0x00.toByte(), 0x00.toByte())
        val jmpAddrPos = emitterTarget.curCodeOffset - 2
        comp(exp.right)
        if (isAnd) emit(iand) else emit(ior)
        decStack()
        emitStackMapFrame()
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrPos - 1)
        overwriteByteCode(jmpAddrPos, *Utils.getLastTwoBytes(jmpAddr))
    }

    override fun visit(literal: AstNode.Literal) {
        when (literal.type) {
            Datatype.Integer() ->  emitIntLoad(literal.literal.literal as Int)
            Datatype.Str() -> emitStringLoad(literal.literal.literal as String)
            Datatype.Bool() -> {
                if (literal.literal.literal as Boolean) emit(iconst_1)
                else emit(iconst_0)
            }
            else -> TODO("not yet implemented")
        }
        incStack(literal.type)
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        comp(exprStmt.exp)
        if (exprStmt.exp is AstNode.FunctionCall && exprStmt.exp.type == Datatype.Void()) return
        emit(pop)
        decStack()
    }

    override fun visit(function: AstNode.Function) {
        val methodBuilder = MethodBuilder()
        method = MethodEmitter(methodBuilder)
        emitterTarget = method!!

        emitterTarget.stack = Stack()
        emitterTarget.locals = MutableList(function.amountLocals) { null }

        for (i in function.functionDescriptor.args.indices) {
            emitterTarget.locals[i] = when (val arg = function.functionDescriptor.args[i].second) {
                Datatype.Integer(), Datatype.Bool() -> VerificationTypeInfo.Integer()
                Datatype.Float() -> VerificationTypeInfo.Float()
                Datatype.Str() -> getObjVerificationType("java/lang/String")
                else -> {
                    if (arg.matches(Datakind.OBJECT)) getObjVerificationType((arg as Datatype.Object).name)
                    else TODO("not yet implemented")
                }
            }
        }

        emitterTarget.maxStack = 0
        emitterTarget.lastStackMapFrameOffset = 0

        methodBuilder.descriptor = function.functionDescriptor.getDescriptorString()
        methodBuilder.name = function.name.lexeme

        comp(function.statements)

        if (methodBuilder.name == "main") {
            methodBuilder.descriptor = "([Ljava/lang/String;)V"
            method!!.maxLocals = 1
            methodBuilder.isPrivate = false
            methodBuilder.isStatic = true
        }

        for (modifier in function.modifiers) when (modifier.lexeme) {
            "public" -> methodBuilder.isPublic = true
            "static" -> methodBuilder.isStatic = true
            "abstract" -> methodBuilder.isAbstract = true
            else -> TODO("not yet implemented")
        }

        if (isTopLevel) {
            methodBuilder.isPublic = true
            methodBuilder.isStatic = true
        }

        methodBuilder.isPrivate = !methodBuilder.isPublic

        if (function.functionDescriptor.returnType == Datatype.Void()) emit(_return)

        //TODO: can probably be done better
        if (emitterTarget.lastStackMapFrameOffset >= emitterTarget.curCodeOffset) emitterTarget.popStackMapFrame()

        if (methodBuilder.curCodeOffset != 0) file.addMethod(methodBuilder)
    }

    override fun visit(program: AstNode.Program) {
        curProgram = program

        file = ClassFileBuilder()
        file.thisClass = topLevelName
        file.superClass = "java/lang/Object"
        file.isSuper = true
        file.isPublic = true
        curFile = file.thisClass
        curClassName = file.thisClass

        val clinitBuilder = MethodBuilder()
        clinit = MethodEmitter(clinitBuilder)
        clinitBuilder.name = "<clinit>"
        clinitBuilder.descriptor = "()V"
        clinitBuilder.isStatic = true

        val initBuilder = MethodBuilder()
        init = MethodEmitter(initBuilder)
        initBuilder.name = "<init>"
        initBuilder.descriptor = "()V"

        doStaticFields(program.fields)

        isTopLevel = true
        for (func in program.funcs) comp(func)
        isTopLevel = false

        if (clinit.curCodeOffset != 0) {
            clinit.emitByteCode(_return)
            file.addMethod(clinitBuilder)
        }
        if (init.curCodeOffset != 0) {
            init.emitByteCode(_return)
            file.addMethod(initBuilder)
        }
        file.build("$outdir/$curFile.class")

        for (clazz in program.classes) doClass(clazz)
    }

    private fun doClass(clazz: AstNode.ArtClass) {
        file = ClassFileBuilder()
        file.thisClass = clazz.name.lexeme
        file.superClass = "java/lang/Object"
        file.isSuper = true
        file.isPublic = true
        curFile = file.thisClass
        curClassName = clazz.name.lexeme

        val clinitBuilder = MethodBuilder()
        clinit = MethodEmitter(clinitBuilder)
        clinitBuilder.name = "<clinit>"
        clinitBuilder.descriptor = "()V"
        clinitBuilder.isStatic = true

        val initBuilder = MethodBuilder()
        init = MethodEmitter(initBuilder)
        initBuilder.name = "<init>"
        initBuilder.descriptor = "()V"
        initBuilder.maxLocals = 1

        doDefaultConstructor()

        doNonStaticFields(clazz.fields)
        doStaticFields(clazz.staticFields)

        for (func in clazz.staticFuncs) comp(func)
        for (func in clazz.funcs) comp(func)

        if (clinit.curCodeOffset != 0) {
            clinit.emitByteCode(_return)
            file.addMethod(clinitBuilder)
        }
        if (init.curCodeOffset != 0) {
            init.emitByteCode(_return)
            file.addMethod(initBuilder)
        }

        file.build("$outdir/$curFile.class")
    }

    private fun doNonStaticFields(fields: List<AstNode.FieldDeclaration>) {
        if (fields.isEmpty()) return
        emitterTarget = init
        for (field in fields) comp(field)
    }

    private fun doStaticFields(fields: List<AstNode.FieldDeclaration>) {
        if (fields.isEmpty()) return
        emitterTarget = clinit
        for (field in fields) comp(field)
    }

    override fun visit(print: AstNode.Print) {
        emit(getstatic)
        emit(*Utils.getLastTwoBytes(file.fieldRefInfo(
            file.classInfo(file.utf8Info("java/lang/System")),
            file.nameAndTypeInfo(
                file.utf8Info("out"),
                file.utf8Info("Ljava/io/PrintStream;")
            )
        )))
        incStack(getObjVerificationType("java/io/PrintStream"))
        comp(print.toPrint)
        emit(invokevirtual)

        val dataTypeToPrint = print.toPrint.type.descriptorType

        emit(*Utils.getLastTwoBytes(file.methodRefInfo(
            file.classInfo(file.utf8Info("java/io/PrintStream")),
            file.nameAndTypeInfo(
                file.utf8Info("println"),
                file.utf8Info("($dataTypeToPrint)V")
            )
        )))
        decStack()
        decStack()
    }

    override fun visit(clazz: AstNode.ArtClass) {
        TODO("Not yet implemented")
    }

    override fun visit(variable: AstNode.Variable) {
        if (variable.type.matches(Datakind.INT, Datakind.BOOLEAN)) {
            emitIntVarLoad(variable.index)
        } else if (variable.type.matches(Datakind.STRING)) {
            emitObjectVarLoad(variable.index)
        } else if (variable.type.matches(Datakind.OBJECT)) {
            emitObjectVarLoad(variable.index)
        } else TODO("not yet implemented")
        incStack(variable.type)
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        comp(varDec.initializer)
        if (varDec.initializer.type.matches(Datakind.INT, Datakind.BOOLEAN)) {
            emitterTarget.locals[varDec.index] = VerificationTypeInfo.Integer()
            emitIntVarStore(varDec.index)
        }
        else if (varDec.initializer.type.matches(Datakind.STRING)) {
            emitterTarget.locals[varDec.index] = getObjVerificationType("java/lang/String")
            emitObjectVarStore(varDec.index)
        }
        else if (varDec.initializer.type.matches(Datakind.OBJECT)) {
            emitterTarget.locals[varDec.index] =
                getObjVerificationType((varDec.initializer.type as Datatype.Object).clazz.name.lexeme)
            emitObjectVarStore(varDec.index)
        }
        else TODO("not yet implemented")
        decStack()
    }

    override fun visit(varAssign: AstNode.Assignment) {
        comp(varAssign.toAssign)
        if (varAssign.index != -1) {
            when (varAssign.toAssign.type) {
                Datatype.Integer() -> emitIntVarStore(varAssign.index)
                Datatype.Str() -> emitObjectVarStore(varAssign.index)
                Datatype.Bool() -> emitIntVarStore(varAssign.index)
                else -> TODO("not yet implemented")
            }
            decStack()
            return
        }
        emit(putstatic)
        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varAssign.name.fieldDef!!.clazz?.name?.lexeme ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.name.name.lexeme),
                file.utf8Info(varAssign.name.fieldDef!!.fieldType.descriptorType)
            )
        )
        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
    }

    override fun visit(loop: AstNode.Loop) {
        emitStackMapFrame()
        val before = emitterTarget.curCodeOffset
        loopContinueAddress = before
        loopBreakAddressesToOverwrite = mutableListOf()
        comp(loop.body)
        val absOffset = (before - emitterTarget.curCodeOffset)
        emitGoto(absOffset)
        emitStackMapFrame()
        val offset = emitterTarget.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }
    }

    override fun visit(block: AstNode.Block) {
        val before = emitterTarget.locals.toMutableList()
        for (s in block.statements) {
            comp(s)
            if (emitterTarget.stack.size != 0) {
                println(emitterTarget.stack.size)
                emit(pop)
                decStack()
            }
        }
        emitterTarget.locals = before
    }

    override fun visit(ifStmt: AstNode.If) {
        val hasElse = ifStmt.elseStmt != null

        comp(ifStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2

        wasReturn = false
        comp(ifStmt.ifStmt)
        val skipGoto = wasReturn
        wasReturn = false

        if (hasElse && !skipGoto) emit(_goto, 0x00.toByte(), 0x00.toByte())
        val elseJmpAddrOffset = emitterTarget.curCodeOffset - 2
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrOffset - 1)
        overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        if (hasElse) emitStackMapFrame()
        if (hasElse) comp(ifStmt.elseStmt!!)
        val elseJmpAddr = emitterTarget.curCodeOffset - (elseJmpAddrOffset - 1)
        if (hasElse && !skipGoto) overwriteByteCode(elseJmpAddrOffset, *Utils.getLastTwoBytes(elseJmpAddr))
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
                incStack(Datatype.Integer())
                emitStackMapFrame()
            }
            else -> TODO("not implemented")
        }
    }

    override fun visit(whileStmt: AstNode.While) {
        val startOffset = emitterTarget.curCodeOffset
        loopContinueAddress = startOffset
        emitStackMapFrame()
        comp(whileStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2
        comp(whileStmt.body)
        emit(_goto, *Utils.getShortAsBytes((startOffset - emitterTarget.curCodeOffset).toShort()))
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrOffset - 1)
        overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        emitStackMapFrame()
        val offset = emitterTarget.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }
    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        if (funcCall.definition.isStatic || funcCall.definition.isTopLevel) {
            for (arg in funcCall.arguments) comp(arg)
            emit(invokestatic)
            val funcRef = file.methodRefInfo(
                file.classInfo(file.utf8Info(
                    if (funcCall.definition.isTopLevel) topLevelName else funcCall.definition.clazz!!.name.lexeme
                )),
                file.nameAndTypeInfo(
                    file.utf8Info(funcCall.func.name.lexeme),
                    file.utf8Info(funcCall.definition.functionDescriptor.getDescriptorString())
                )
            )
            emit(*Utils.getLastTwoBytes(funcRef))
            repeat(funcCall.arguments.size) { decStack() }
            if (funcCall.type != Datatype.Void()) incStack(funcCall.type)
            return
        }

        comp(funcCall.func.from!!)
        for (arg in funcCall.arguments) comp(arg)
        emit(invokevirtual)
        val funcRef = file.methodRefInfo(
            file.classInfo(file.utf8Info(funcCall.definition.clazz!!.name.lexeme)),
            file.nameAndTypeInfo(
                file.utf8Info(funcCall.func.name.lexeme),
                file.utf8Info(funcCall.definition.functionDescriptor.getDescriptorString())
            )
        )
        emit(*Utils.getLastTwoBytes(funcRef))
        repeat(funcCall.arguments.size) { decStack() }
        decStack()
        if (funcCall.type != Datatype.Void()) incStack(funcCall.type)
    }

    override fun visit(returnStmt: AstNode.Return) {
        if (returnStmt.toReturn == null) {
            emit(_return)
            return
        }

        comp(returnStmt.toReturn!!)

        if (returnStmt.toReturn!!.type.matches(Datakind.STRING)) emit(areturn)
        else if (returnStmt.toReturn!!.type.matches(Datakind.INT, Datakind.STRING)) emit(ireturn)
        else if (returnStmt.toReturn!!.type.matches(Datakind.OBJECT)) emit(areturn)
        else TODO("not yet implemented")

        decStack()
        wasReturn = true
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        emit(iinc, (varInc.index and 0xFF).toByte(), varInc.toAdd)
    }

    override fun visit(walrus: AstNode.WalrusAssign) {
        comp(walrus.toAssign)
        emit(dup)
        incStack(emitterTarget.stack.peek())
        when (walrus.type) {
            Datatype.Integer() -> emitIntVarStore(walrus.index)
            Datatype.Str() -> emitObjectVarStore(walrus.index)
            Datatype.Bool() -> emitIntVarStore(walrus.index)
            else -> TODO("not yet implemented")
        }
        decStack()
    }

    override fun visit(get: AstNode.Get) {
        if (get.from == null) {
            emit(getstatic)
            val fieldRef = file.fieldRefInfo(
                file.classInfo(file.utf8Info(topLevelName)),
                file.nameAndTypeInfo(
                    file.utf8Info(get.fieldDef!!.name.lexeme),
                    file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
                )
            )
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(get.fieldDef!!.fieldType)
            return
        }

        if (get.fieldDef!!.isStatic && get.fieldDef!!.isTopLevel) {
            emit(getstatic)
            val fieldRef = file.fieldRefInfo(
                file.classInfo(file.utf8Info((get.from!!.type as Datatype.StatClass).clazz.name.lexeme)),
                file.nameAndTypeInfo(
                    file.utf8Info(get.name.lexeme),
                    file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
                )
            )
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(get.fieldDef!!.fieldType)
            return
        }

        comp(get.from!!)
        emit(getfield)

        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info((get.from!!.type as Datatype.Object).clazz.name.lexeme)),
            file.nameAndTypeInfo(
                file.utf8Info(get.name.lexeme),
                file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
            )
        )
        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
        incStack(get.fieldDef!!.fieldType)
    }

    override fun visit(cont: AstNode.Continue) {
        emitGoto(loopContinueAddress - emitterTarget.curCodeOffset)
    }

    override fun visit(breac: AstNode.Break) {
        emit(_goto, 0x00, 0x00)
        loopBreakAddressesToOverwrite.add(emitterTarget.curCodeOffset - 2)
    }

    override fun visit(constructorCall: AstNode.ConstructorCall) {
        emit(new, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(constructorCall.clazz.name.lexeme))))
        incStack(constructorCall.type)
        emit(dup)
        incStack(constructorCall.type)

        val methodIndex = file.methodRefInfo(
            file.classInfo(file.utf8Info(constructorCall.clazz.name.lexeme)),
            file.nameAndTypeInfo(
                file.utf8Info("<init>"),
                file.utf8Info("()V")
            )
        )
        emit(invokespecial, *Utils.getLastTwoBytes(methodIndex))
        decStack()
    }

    override fun visit(field: AstNode.FieldDeclaration) {
        val fieldToAdd = Field(
            file.utf8Info(field.name.lexeme),
            file.utf8Info(field.fieldType.descriptorType)
        )

        for (modifier in field.modifiers) when (modifier.lexeme) {
            "public" -> fieldToAdd.isPublic = true
            "static" -> fieldToAdd.isStatic = true
        }
        if (field.isTopLevel) fieldToAdd.isStatic = true
        if (field.isConst) fieldToAdd.isFinal = true
        fieldToAdd.isPrivate = !fieldToAdd.isPublic

        file.addField(fieldToAdd)

        if (!field.isStatic && !field.isTopLevel) {
            emit(aload_0)
            incStack(getObjVerificationType(curClassName))
        }

        comp(field.initializer)

        val fieldRefIndex = file.fieldRefInfo(
            file.classInfo(file.utf8Info(curClassName)),
            file.nameAndTypeInfo(
                file.utf8Info(field.name.lexeme),
                file.utf8Info(field.fieldType.descriptorType)
            )
        )

        if (field.isStatic || field.isTopLevel) emit(putstatic, *Utils.getLastTwoBytes(fieldRefIndex))
        else emit(putfield, *Utils.getLastTwoBytes(fieldRefIndex))
        decStack()
        if (!field.isStatic && !field.isTopLevel) decStack()
    }

    private fun doDefaultConstructor() {
        val objConstructorIndex = file.methodRefInfo(
            file.classInfo(file.utf8Info("java/lang/Object")),
            file.nameAndTypeInfo(
                file.utf8Info("<init>"),
                file.utf8Info("()V")
            )
        )
        emit(
            aload_0,
            invokespecial,
            *Utils.getLastTwoBytes(objConstructorIndex),
        )
    }

    private fun doCompare(compareInstruction: Byte) {
        emitStackMapFrame()
        emit(compareInstruction, *Utils.getShortAsBytes(7.toShort()))
        decStack(); decStack()
        emit(iconst_0, _goto, *Utils.getShortAsBytes(4.toShort()))
        emitStackMapFrame()
        emit(iconst_1)
        incStack(Datatype.Integer())
        emitStackMapFrame()
    }

    private fun emitGoto(offset: Int) {
        if (offset !in Short.MIN_VALUE..Short.MAX_VALUE) emit(goto_w, *Utils.getIntAsBytes(offset))
        else emit(_goto, *Utils.getShortAsBytes(offset.toShort()))
    }

    private fun emitStackMapFrame() {

        //TODO: instead of always emitting FullStackMapFrames also use other types of StackMapFrames

        val offsetDelta = if (emitterTarget.lastStackMapFrameOffset == 0) emitterTarget.curCodeOffset
        else (emitterTarget.curCodeOffset - emitterTarget.lastStackMapFrameOffset) - 1

        if (offsetDelta < 0) return //frame already exists at this offset

        val frame = StackMapTableAttribute.FullStackMapFrame(offsetDelta)

        val newLocals = mutableListOf<VerificationTypeInfo>()
        for (local in emitterTarget.locals) if (local != null) newLocals.add(local)

        frame.locals = newLocals
        frame.stack = emitterTarget.stack.toMutableList()
        emitterTarget.lastStackMapFrameOffset = emitterTarget.curCodeOffset
        emitterTarget.addStackMapFrame(frame)
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
        else -> emitLdc(file.integerInfo(i))
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

    private fun emitStringLoad(s: String) = emitLdc(file.stringInfo(file.utf8Info(s)))


    private fun comp(node: AstNode) {
        node.accept(this)
    }

    private fun getObjVerificationType(clazz: String): VerificationTypeInfo {
        return VerificationTypeInfo.ObjectVariable(file.classInfo(file.utf8Info(clazz)))
    }

    private fun emit(vararg bytes: Byte) = emitterTarget.emitByteCode(*bytes)
    private fun overwriteByteCode(pos: Int, vararg bytes: Byte) = emitterTarget.overwriteByteCode(pos, *bytes)

    private fun incStack(type: VerificationTypeInfo) {
        emitterTarget.stack.push(type)
        if (emitterTarget.stack.size >emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    private fun incStack(type: Datatype) {
        val verificationType = when (type.kind) {
            Datakind.INT, Datakind.BOOLEAN -> VerificationTypeInfo.Integer()
            Datakind.FLOAT -> VerificationTypeInfo.Float()
            Datakind.STRING -> getObjVerificationType("java/lang/String")
            Datakind.OBJECT -> getObjVerificationType((type as Datatype.Object).clazz.name.lexeme)
            else -> TODO("not yet implemented")
        }

        emitterTarget.stack.push(verificationType)
        if (emitterTarget.stack.size > emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    private fun decStack() {
        emitterTarget.stack.pop()
    }

    abstract class EmitterTarget {
        abstract val curCodeOffset: Int
        abstract var stack: Stack<VerificationTypeInfo>
        abstract var maxStack: Int
        abstract var locals: MutableList<VerificationTypeInfo?>
        abstract var maxLocals: Int
        abstract var lastStackMapFrameOffset: Int

        abstract fun emitByteCode(vararg bytes: Byte)
        abstract fun overwriteByteCode(insertPos: Int, vararg bytes: Byte)
        abstract fun addStackMapFrame(stackMapFrame: StackMapTableAttribute.StackMapFrame)
        abstract fun popStackMapFrame(): StackMapTableAttribute.StackMapFrame
    }

    class MethodEmitter(val methodBuilder: MethodBuilder) : EmitterTarget() {

        override val curCodeOffset: Int
            get() = methodBuilder.curCodeOffset

        override var stack: Stack<VerificationTypeInfo> = Stack()

        override var maxStack: Int
            get() = methodBuilder.maxStack
            set(value) {
                methodBuilder.maxStack = value
            }

        override var maxLocals: Int
            get() = methodBuilder.maxLocals
            set(value) {
                methodBuilder.maxLocals = value
            }

        override var locals: MutableList<VerificationTypeInfo?> = mutableListOf()

        override var lastStackMapFrameOffset: Int = 0

        override fun emitByteCode(vararg bytes: Byte) = methodBuilder.emitByteCode(*bytes)

        override fun overwriteByteCode(insertPos: Int, vararg bytes: Byte) {
            methodBuilder.overwriteByteCode(insertPos, *bytes)
        }

        override fun addStackMapFrame(stackMapFrame: StackMapTableAttribute.StackMapFrame) {
            methodBuilder.addStackMapFrame(stackMapFrame)
        }

        override fun popStackMapFrame() = methodBuilder.popStackMapFrame()
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

        const val getstatic: Byte = 0xB2.toByte()
        const val putstatic: Byte = 0xB3.toByte()
        const val getfield: Byte = 0xB4.toByte()
        const val putfield: Byte = 0xB5.toByte()

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
