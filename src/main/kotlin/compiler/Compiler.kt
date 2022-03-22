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

/**
 * compiles the AST into a binary class file
 */
class Compiler : AstNodeVisitor<Unit> {

    // How the compiler works
    // Specific nodes using the visit functions. Each node compiles their child-nodes using the compile method
    // all compiled code is emitted to the emitterTarget. If code for e.g. a different method is compiled, the
    // emitterTarget property must be set to the correct target. Most methods assume the emitterTarget is correct.
    // Whenever an instruction is emitted that causes the stack to change, the incStack or decStack method must
    // be called. This changes the simulated stack in the emitterTarget and sets the maxStack property. It is also
    // necessary for StackMapFrames, which need to know the layout of the stack. Also, any change in the local array
    // must be reflected in the simulated local array of the emitterTarget.
    // Any helper method starting with emit does not call these functions. If the helper method emits instructions that
    // change the stack or the locals, the caller is responsible for reflecting these changes in the
    // simulated stack/locals. More complex helper methods start with do and responsible for doing this themselves.

    /**
     * the directory in which the output files are located
     */
    private var outdir: String = ""

    /**
     * the name of the class in which the top level code is located
     */
    private var topLevelName: String = ""

    /**
     * the name of the file in which the source code is located
     */
    private var originFileName: String = ""

    /**
     * the name of the file that is currently being compiled
     */
    private var curFile: String = ""

    /**
     * the name of the class that is currently being compiled
     */
    private var curClassName: String = ""

    /**
     * true if the top level is currently being compiled
     */
    private var isTopLevel: Boolean = false

    /**
     * the methodEmitter for the current method or null if not in a method
     */
    private var method: MethodEmitter? = null

    /**
     * the builder for the current classFile
     */
    private lateinit var file: ClassFileBuilder

    /**
     * the builder for the
     * [`<clinit>` special method](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.9)
     * of the current classfile
     */
    private lateinit var clinit: MethodEmitter

    /**
     * the builder for the
     * [`<init>` special method](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.9)
     * of the current classfile
     */
    private lateinit var init: MethodEmitter

    /**
     * true if the last statement that was compiled was a return
     */
    private var wasReturn: Boolean = false

    /**
     * the address to jump to if a `continue` is encountered
     */
    private var loopContinueAddress: Int = -1

    /**
     * list of the addresses where the gotos associated with breaks are located. These are overwritten with the actual
     * jump addresses when the compilation of the loop body is finished
     */
    private var loopBreakAddressesToOverwrite = mutableListOf<Int>()

    /**
     * the current target to which bytecode is emitted
     */
    private lateinit var emitterTarget: EmitterTarget

    /**
     * the program that is being compiled
     */
    private lateinit var curProgram: AstNode.Program

    /**
     * compiles a program in form of an Ast to class-files
     * @param program the program
     * @param outdir the dir in which the output files are created
     * @param name the name of the program
     */
    fun compileProgram(program: AstNode.Program, outdir: String, name: String) {
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

        compile(binary.left)
        compile(binary.right)

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

    /**
     * compiles a binary addition of strings using StringBuilders
     */
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

        compile(exp.left)
        emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
        decStack()
        compile(exp.right)
        emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
        decStack()
        emit(invokevirtual, *Utils.getLastTwoBytes(toStringMethodInfo))
        decStack()
        incStack(Datatype.Str())
    }

    /**
     * compiles `&&` and `||` and short-circuiting
     */
    private fun doBooleanComparison(exp: AstNode.Binary) {
        val isAnd = exp.operator.tokenType == TokenType.D_AND
        compile(exp.left)
        emit(dup)
        incStack(Datatype.Bool())
        if (isAnd) emit(ifeq) else emit(ifne)
        decStack()
        emit(0x00.toByte(), 0x00.toByte())
        val jmpAddrPos = emitterTarget.curCodeOffset - 2
        compile(exp.right)
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

    override fun visit(exprStmt: AstNode.ExpressionStatement) { //TODO: remove?
        compile(exprStmt.exp)
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
            putTypeInLocals(i, function.functionDescriptor.args[i].second, false)
        }

        emitterTarget.maxStack = 0
        emitterTarget.lastStackMapFrameOffset = 0
        emitterTarget.maxLocals = function.amountLocals

        methodBuilder.descriptor = function.functionDescriptor.getDescriptorString()
        methodBuilder.name = function.name.lexeme

        compile(function.statements)

        //special cases for main method
        if (methodBuilder.name == "main") {
            methodBuilder.descriptor = "([Ljava/lang/String;)V"
            if (method!!.maxLocals < 1) methodBuilder.maxLocals = 1
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
            //top level functions are always public and static
            methodBuilder.isPublic = true
            methodBuilder.isStatic = true
        }

        methodBuilder.isPrivate = !methodBuilder.isPublic

        if (function.functionDescriptor.returnType == Datatype.Void()) emit(_return) //ensure void method always returns

        //TODO: can probably be done better
        if (emitterTarget.lastStackMapFrameOffset >= emitterTarget.curCodeOffset) emitterTarget.popStackMapFrame()

        if (methodBuilder.curCodeOffset != 0) file.addMethod(methodBuilder) //only add method if it contains code
    }

    override fun visit(program: AstNode.Program) {
        curProgram = program

        file = ClassFileBuilder()
        file.thisClass = topLevelName
        file.superClass = "java/lang/Object"
        file.isPublic = true

        file.isSuper = true //always set super flag
        //from https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.1:
        //compilers to the instruction set of the Java Virtual Machine should set the ACC_SUPER flag.
        //
        //The ACC_SUPER flag exists for backward compatibility with code compiled by older compilers for the Java
        // programming language. In Oracle’s JDK prior to release 1.0.2, the compiler generated ClassFile access_flags
        // in which the flag now representing ACC_SUPER had no assigned meaning, and Oracle's Java Virtual Machine
        // implementation ignored the flag if it was set.

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
        for (func in program.funcs) compile(func)
        isTopLevel = false

        //only add methods if they have code
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

    /**
     * compiles a class. Sets [file], [clinit], [init] and [emitterTarget].
     * Creates the output file and compiles all functions and fieds associated with this class
     */
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

        emitterTarget = init
        doDefaultConstructor()

        doNonStaticFields(clazz.fields)
        doStaticFields(clazz.staticFields)

        for (func in clazz.staticFuncs) compile(func)
        for (func in clazz.funcs) compile(func)

        //only emit methods if they contain code
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

    /**
     * compiles the non-static fields of the current class. Sets [init]
     */
    private fun doNonStaticFields(fields: List<AstNode.FieldDeclaration>) {
        if (fields.isEmpty()) return
        emitterTarget = init
        for (field in fields) compile(field)
    }

    /**
     * compiles the non-static fields of the current class. Sets [clinit]
     */
    private fun doStaticFields(fields: List<AstNode.FieldDeclaration>) {
        if (fields.isEmpty()) return
        emitterTarget = clinit
        for (field in fields) compile(field)
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
        compile(print.toPrint)
        emit(invokevirtual)

        val dataTypeToPrint = if (print.toPrint.type.matches(Datakind.OBJECT)) "Ljava/lang/Object;"
                                else print.toPrint.type.descriptorType

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
        throw RuntimeException("unreachable") //classes are compiled by the doClass function called
                                              // from the visit(Astnode.Program) function
    }

    override fun visit(variable: AstNode.Variable) {

        if (variable.arrIndex == null) {
            when (variable.type.kind) {
                Datakind.INT, Datakind.BOOLEAN -> emitIntVarLoad(variable.index)
                Datakind.STRING, Datakind.OBJECT, Datakind.ARRAY -> emitObjectVarLoad(variable.index)
                else -> TODO("variable load type not implemented")
            }
            incStack(variable.type)
            return
        }

        emitObjectVarLoad(variable.index)
        incStack(emitterTarget.locals[variable.index]!!)
        compile(variable.arrIndex!!)
        emitALoad(variable.type)
        decStack()
        decStack()
        incStack(variable.type)
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        compile(varDec.initializer)
        putTypeInLocals(varDec.index, varDec.varType, true)
        decStack()
    }

    /**
     * puts the verification type associated with a datatype into the locals array of the current [emitterTarget]
     * @param emitStore if true, the function will also emit the correct store instruction
     */
    private fun putTypeInLocals(index: Int, type: Datatype, emitStore: Boolean) = when (type.kind) {
        Datakind.INT, Datakind.BOOLEAN -> {
            if (emitStore) emitIntVarStore(index)
            emitterTarget.locals[index] = VerificationTypeInfo.Integer()
        }
        Datakind.STRING -> {
            if (emitStore) emitObjectVarStore(index)
            emitterTarget.locals[index] = getObjVerificationType("java/lang/String")
        }
        Datakind.OBJECT -> {
            if (emitStore) emitObjectVarStore(index)
            emitterTarget.locals[index] =
                getObjVerificationType((type as Datatype.Object).clazz.name.lexeme)
        }
        Datakind.ARRAY -> {
            if (emitStore) emitObjectVarStore(index)
            emitterTarget.locals[index] = getObjVerificationType(type.descriptorType)
        }
        else -> TODO("not yet implemented")
    }

    override fun visit(varAssign: AstNode.Assignment) {
        if (varAssign.index != -1) {
            doVarAssignForLocal(varAssign)
            return
        }

        if (varAssign.name.fieldDef!!.isStatic || varAssign.name.fieldDef!!.isTopLevel) {
            doVarAssignForStaticField(varAssign)
            return
        }

        //non static field

        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varAssign.name.fieldDef!!.clazz?.name?.lexeme ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.name.name.lexeme),
                file.utf8Info(varAssign.name.fieldDef!!.fieldType.descriptorType)
            )
        )

        if (varAssign.arrIndex != null) {
            //array access
            compile(varAssign.name.from!!)
            emit(getfield)
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(varAssign.name.fieldDef!!.type)
            compile(varAssign.arrIndex!!)
            compile(varAssign.toAssign)
            if (varAssign.isWalrus) {
                emit(dup_x2)
                incStack(varAssign.toAssign.type)
            }
            emitAStore(varAssign.toAssign.type)
            decStack()
            decStack()
            decStack()
            return
        }

        compile(varAssign.name.from!!)
        compile(varAssign.toAssign)

        if (varAssign.isWalrus) {
            emit(dup_x1)
            incStack(emitterTarget.stack[emitterTarget.stack.size - 1])
        }

        emit(putfield)

        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
        decStack()
    }

    /**
     * compiles an assignment to a static field
     */
    private fun doVarAssignForStaticField(varAssign: AstNode.Assignment) {

        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varAssign.name.fieldDef!!.clazz?.name?.lexeme ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.name.name.lexeme),
                file.utf8Info(varAssign.name.fieldDef!!.fieldType.descriptorType)
            )
        )

        if (varAssign.arrIndex != null) {
            //array access
            emit(getstatic)
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(varAssign.name.type)
            compile(varAssign.arrIndex!!)
            compile(varAssign.toAssign)
            if (varAssign.isWalrus) {
                emit(dup_x2)
                incStack(varAssign.toAssign.type)
            }
            emitAStore(varAssign.toAssign.type)
            decStack()
            decStack()
            decStack()
            return
        }

        compile(varAssign.toAssign)
        if (varAssign.isWalrus) {
            emit(dup)
            incStack(emitterTarget.stack.peek())
        }
        emit(putstatic)
        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
        return
    }

    /**
     * compiles an assignment to a local variable
     */
    private fun doVarAssignForLocal(varAssign: AstNode.Assignment) {
        if (varAssign.arrIndex != null) {
            //array access
            emitObjectVarLoad(varAssign.index)
            incStack(varAssign.name.type)
            compile(varAssign.arrIndex!!)
            compile(varAssign.toAssign)
            if (varAssign.isWalrus) {
                emit(dup_x2)
                incStack(varAssign.toAssign.type)
            }
            emitAStore(varAssign.toAssign.type)
            decStack()
            decStack()
            decStack()
            return
        }

        compile(varAssign.toAssign)
        if (varAssign.isWalrus) {
            emit(dup)
            incStack(emitterTarget.stack.peek())
        }
        when (varAssign.toAssign.type) {
            Datatype.Integer() -> emitIntVarStore(varAssign.index)
            Datatype.Str() -> emitObjectVarStore(varAssign.index)
            Datatype.Bool() -> emitIntVarStore(varAssign.index)
            else -> TODO("type for local assignment not implemented")
        }
        decStack()
        return
    }

    override fun visit(loop: AstNode.Loop) {
        emitStackMapFrame()
        val before = emitterTarget.curCodeOffset
        loopContinueAddress = before //TODO: fix nested loops
        loopBreakAddressesToOverwrite = mutableListOf()
        compile(loop.body)
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
            compile(s)
            if (emitterTarget.stack.size != 0) { //last statement was an expression, left value on stack
                println(emitterTarget.stack.size)
                emit(pop)
                decStack()
            }
        }
        emitterTarget.locals = before
    }

    override fun visit(ifStmt: AstNode.If) {
        val hasElse = ifStmt.elseStmt != null

        compile(ifStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2

        wasReturn = false
        compile(ifStmt.ifStmt)
        val skipGoto = wasReturn
        wasReturn = false

        if (hasElse && !skipGoto) emit(_goto, 0x00.toByte(), 0x00.toByte())
        val elseJmpAddrOffset = emitterTarget.curCodeOffset - 2
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrOffset - 1)
        overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        if (hasElse) emitStackMapFrame()
        if (hasElse) compile(ifStmt.elseStmt!!)
        val elseJmpAddr = emitterTarget.curCodeOffset - (elseJmpAddrOffset - 1)
        if (hasElse && !skipGoto) overwriteByteCode(elseJmpAddrOffset, *Utils.getLastTwoBytes(elseJmpAddr))
        emitStackMapFrame()
    }

    override fun visit(group: AstNode.Group) {
        compile(group.grouped)
    }

    override fun visit(unary: AstNode.Unary) {
        compile(unary.on)
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
        //TODO: fix nested loops
        val startOffset = emitterTarget.curCodeOffset
        loopContinueAddress = startOffset
        emitStackMapFrame()
        compile(whileStmt.condition)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2
        compile(whileStmt.body)
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
            //static function
            for (arg in funcCall.arguments) compile(arg)
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

        //non-static function
        compile(funcCall.func.from!!)
        for (arg in funcCall.arguments) compile(arg)
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

        compile(returnStmt.toReturn!!)

        when (returnStmt.toReturn!!.type.kind) {
            Datakind.STRING, Datakind.OBJECT, Datakind.ARRAY -> emit(areturn)
            Datakind.INT -> emit(ireturn)
            else -> TODO("returning ${returnStmt.type.kind} is not yet implemented")
        }

        decStack()
        wasReturn = true
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        emit(iinc, (varInc.index and 0xFF).toByte(), varInc.toAdd)
    }

    override fun visit(get: AstNode.Get) {
        if (get.from == null) {
            //direct reference to either static field or top level field
            emit(getstatic)
            val fieldRef = file.fieldRefInfo(
                file.classInfo(file.utf8Info(
                    if (get.fieldDef!!.isTopLevel) topLevelName else get.fieldDef!!.clazz!!.name.lexeme
                )),
                file.nameAndTypeInfo(
                    file.utf8Info(get.fieldDef!!.name.lexeme),
                    file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
                )
            )
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(get.fieldDef!!.fieldType)
            if (get.arrIndex == null) return
            compile(get.arrIndex!!)
            emitALoad(get.type)
            decStack()
            decStack()
            incStack(get.type)
            return
        }

        if (get.fieldDef!!.isStatic || get.fieldDef!!.isTopLevel) {
            //reference to static or top level field with a from specified
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
            if (get.arrIndex == null) return
            compile(get.arrIndex!!)
            emitALoad(get.type)
            decStack()
            decStack()
            incStack(get.type)
            return
        }

        //a non-static field

        compile(get.from!!)
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
        if (get.arrIndex == null) return
        compile(get.arrIndex!!)
        emitALoad(get.type)
        decStack()
        decStack()
        incStack(get.type)
    }

    /**
     * emits the array store instruction for the type
     */
    fun emitAStore(type: Datatype) = when (type.kind) {
        Datakind.INT -> emit(iastore)
        Datakind.STRING, Datakind.OBJECT -> emit(aastore)
        else -> TODO("only int, string and object arrays are implemented")
    }

    /**
     * emits the array load instruction for the type
     */
    fun emitALoad(type: Datatype) = when (type.kind) {
        Datakind.INT -> emit(iaload)
        Datakind.STRING, Datakind.OBJECT -> emit(aaload)
        else -> TODO("only int, string and object arrays are implemented")
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

    /**
     * assumes [emitterTarget] is set correctly
     */
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

        compile(field.initializer)

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

    override fun visit(arr: AstNode.ArrayCreate) {
        when (val kind = (arr.type as Datatype.ArrayType).type.kind) {
            Datakind.INT -> {
                compile(arr.amount)
                emit(newarray, getAType(kind))
                decStack()
                incStack(arr.type)
            }
            Datakind.STRING -> {
                compile(arr.amount)
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info("java/lang/String"))))
                decStack()
                incStack(arr.type)
            }
            Datakind.OBJECT -> {
                compile(arr.amount)
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(
                    ((arr.type as Datatype.ArrayType).type as Datatype.Object).clazz.name.lexeme
                ))))
                decStack()
                incStack(arr.type)
            }
            else -> TODO("only int, string and object arrs are implemented")
        }
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        when (val kind = (arr.type as Datatype.ArrayType).type.kind) {
            Datakind.INT -> {
                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(newarray, getAType(kind))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    emit(dup)
                    incStack(emitterTarget.stack.peek())
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i])
                    emit(iastore)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            Datakind.STRING -> {
                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info("java/lang/String"))))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    emit(dup)
                    incStack(emitterTarget.stack.peek())
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i])
                    emit(aastore)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            Datakind.OBJECT -> {
                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(
                    ((arr.type as Datatype.ArrayType).type as Datatype.Object).clazz.name.lexeme
                ))))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    emit(dup)
                    incStack(emitterTarget.stack.peek())
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i])
                    emit(aastore)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            else -> TODO("only int, string and object arrays are implemented")
        }
    }

    /**
     * the [newarray] instruction uses an operand to tell it the type of array. This functions returns
     * the byte for a specific type
     */
    private fun getAType(type: Datakind): Byte = when (type) {
        Datakind.INT -> 10
        Datakind.BOOLEAN -> 4
        Datakind.FLOAT -> 6
        else -> TODO("Not yet implemented")
    }

    /**
     * emits the default constructor. assumes [emitterTarget] is set correctly, super is set "java/lang/Object" and does
     * not return
     */
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
        incStack(VerificationTypeInfo.UninitializedThis())
        decStack()
    }

    /**
     * compiles a comparison for a given compare instruction (e.g. [if_icmpeq], [if_icmpgt], [if_icmple]).
     * leaves a boolean (0 or 1) on the stack
     */
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

    /**
     * emits either a [_goto] or [goto_w] instruction depending on the size of offset
     */
    private fun emitGoto(offset: Int) {
        if (offset !in Short.MIN_VALUE..Short.MAX_VALUE) emit(goto_w, *Utils.getIntAsBytes(offset))
        else emit(_goto, *Utils.getShortAsBytes(offset.toShort()))
    }

    /**
     * emits a StackMapFrame at the current offset in the byte code using the information from the
     * stack and locals array
     */
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

    /**
     * loads an integer constant
     */
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

    /**
     * loads an integer variable
     */
    private fun emitIntVarLoad(index: Int) = when (index) {
        0 -> emit(iload_0)
        1 -> emit(iload_1)
        2 -> emit(iload_2)
        3 -> emit(iload_3)
        else -> emit(iload, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * stores an integer in a variable
     */
    private fun emitIntVarStore(index: Int) = when (index) {
        0 -> emit(istore_0)
        1 -> emit(istore_1)
        2 -> emit(istore_2)
        3 -> emit(istore_3)
        else -> emit(istore, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * loads an object variable
     */
    private fun emitObjectVarLoad(index: Int) = when (index) {
        0 -> emit(aload_0)
        1 -> emit(aload_1)
        2 -> emit(aload_2)
        3 -> emit(aload_3)
        else -> emit(aload, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * stores an object in a variable
     */
    private fun emitObjectVarStore(index: Int) = when (index) {
        0 -> emit(astore_0)
        1 -> emit(astore_1)
        2 -> emit(astore_2)
        3 -> emit(astore_3)
        else -> emit(astore, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * loads a constant from th constant pool
     */
    private fun emitLdc(index: Int) {
        if (index <= 255) emit(ldc, (index and 0xFF).toByte())
        else emit(ldc_w, *Utils.getLastTwoBytes(index))
    }

    /**
     * adds a string to the constant pool and emits the correct load instruction for the string
     */
    private fun emitStringLoad(s: String) = emitLdc(file.stringInfo(file.utf8Info(s)))

    /**
     * compiles a node
     */
    private fun compile(node: AstNode) {
        node.accept(this)
    }

    /**
     * returns the verification type for an object
     */
    private fun getObjVerificationType(clazz: String): VerificationTypeInfo {
        return VerificationTypeInfo.ObjectVariable(file.classInfo(file.utf8Info(clazz)))
    }

    /**
     * emits byte code to the current [emitterTarget]
     */
    private fun emit(vararg bytes: Byte) = emitterTarget.emitByteCode(*bytes)

    /**
     * overwrites byte code of the current [emitterTarget]
     * @param pos the offset at which the first byte is inserted
     */
    private fun overwriteByteCode(pos: Int, vararg bytes: Byte) = emitterTarget.overwriteByteCode(pos, *bytes)

    /**
     * increments the stack of the current [emitterTarget] and sets the maxStack property if necessary
     */
    private fun incStack(type: VerificationTypeInfo) {
        emitterTarget.stack.push(type)
        if (emitterTarget.stack.size >emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    /**
     * increments the stack of the current [emitterTarget] and sets the maxStack property if necessary
     */
    private fun incStack(type: Datatype) {
        val verificationType = when (type.kind) {
            Datakind.INT, Datakind.BOOLEAN -> VerificationTypeInfo.Integer()
            Datakind.FLOAT -> VerificationTypeInfo.Float()
            Datakind.STRING -> getObjVerificationType("java/lang/String")
            Datakind.OBJECT -> getObjVerificationType((type as Datatype.Object).clazz.name.lexeme)
            Datakind.ARRAY -> getObjVerificationType(type.descriptorType)
            else -> TODO("not yet implemented")
        }

        emitterTarget.stack.push(verificationType)
        if (emitterTarget.stack.size > emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    /**
     * decrements the stack of the current [emitterTarget]
     */
    private fun decStack() {
        emitterTarget.stack.pop()
    }

    /**
     * represents an object to which byte code can be emitted
     */
    abstract class EmitterTarget {
        /**
         * returns the current offset into the byteCode
         */
        abstract val curCodeOffset: Int

        /**
         * this stack is used to simulate the stack of the jvm at compile time
         */
        abstract var stack: Stack<VerificationTypeInfo>

        /**
         * the maximum stack size
         */
        abstract var maxStack: Int

        /**
         * simulates the locals array of the jvm at compile time
         */
        abstract var locals: MutableList<VerificationTypeInfo?>

        /**
         * the maximum amount of locals
         */
        abstract var maxLocals: Int

        /**
         * the offset at which the last StackMapFrame was emitted
         */
        abstract var lastStackMapFrameOffset: Int

        /**
         * adds byte code to the emitter
         */
        abstract fun emitByteCode(vararg bytes: Byte)

        /**
         * overwrites byte code of the emitter at a specified offset
         * @param insertPos the offset at which the first byte is inserted
         */
        abstract fun overwriteByteCode(insertPos: Int, vararg bytes: Byte)

        /**
         * adds a StackMapFrame to the emitter
         */
        abstract fun addStackMapFrame(stackMapFrame: StackMapTableAttribute.StackMapFrame)

        /**
         * removes the last StackMapFrame
         */
        abstract fun popStackMapFrame(): StackMapTableAttribute.StackMapFrame
    }

    /**
     * translates the functions of [EmitterTarget] to [MethodBuilder]
     */
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

        //I'm not writing javadoc for each instruction, look them up here:
        // https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions

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
        const val dup_x1: Byte = 0x5A.toByte()
        const val dup_x2: Byte = 0x5B.toByte()

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

        const val aaload: Byte = 0x32.toByte()
        const val aastore: Byte = 0x53.toByte()
        const val anewarray: Byte = 0xBD.toByte()
        const val multianewarray: Byte = 0xC5.toByte()
        const val iastore: Byte = 0x4F.toByte()

        const val iaload: Byte = 0x2E.toByte()
        const val newarray: Byte = 0xBC.toByte()
    }
}
