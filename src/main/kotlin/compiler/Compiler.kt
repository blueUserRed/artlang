package compiler

import Utils
import ast.AstNode
import ast.AstNodeVisitor
import classFile.ClassFileBuilder
import classFile.Field
import classFile.MethodBuilder
import classFile.StackMapTableAttribute
import classFile.StackMapTableAttribute.VerificationTypeInfo
import Datatype
import Datakind
import ast.SyntheticNode
import tokenizer.TokenType
import java.io.File
import java.util.*
import java.util.stream.Stream

/**
 * compiles the AST into a binary class file
 */
class Compiler : AstNodeVisitor<Unit> {

    // How the compiler works
    // Each node compiles their child-nodes using the compile method.
    // All compiled code is emitted to the emitterTarget. If code for e.g. a different method is compiled, the
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
     * the class that is currently being compiled
     */
    private var curClass: AstNode.ArtClass? = null

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

    private var curFunction: AstNode.Function? = null

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

        compile(binary.left, false)
        compile(binary.right, false)

        if (binary.type == Datatype.Bool()) { //gt, lt, gt_eq, etc.

            when (binary.left.type.kind) {

                Datakind.BYTE, Datakind.SHORT, Datakind.INT -> {
                    when (binary.operator.tokenType) {
                        TokenType.GT -> doIntCompare(if_icmpgt)
                        TokenType.GT_EQ -> doIntCompare(if_icmpge)
                        TokenType.LT -> doIntCompare(if_icmplt)
                        TokenType.LT_EQ -> doIntCompare(if_icmple)
                        TokenType.D_EQ -> doIntCompare(if_icmpeq)
                        TokenType.NOT_EQ -> doIntCompare(if_icmpne)
                        else -> throw RuntimeException("unreachable")
                    }
                    doConvertPrimitive(binary.left.type, binary.type)
                }

                Datakind.FLOAT -> doNonIntCompare(binary.operator.tokenType, fcmpg)
                Datakind.LONG -> doNonIntCompare(binary.operator.tokenType, lcmp)

                else -> throw RuntimeException("unreachable")

            }

            return
        }

        val instructions = arrayOf(
            arrayOf(iadd, fadd, ladd),
            arrayOf(isub, fsub, lsub),
            arrayOf(imul, fmul, lmul),
            arrayOf(idiv, fdiv, ldiv),
            arrayOf(irem, frem, lrem)
        )

        val typeIndex = when (binary.type.kind) {
            Datakind.LONG -> 2
            Datakind.FLOAT -> 1
            Datakind.BYTE, Datakind.SHORT, Datakind.INT -> 0
            Datakind.BOOLEAN -> -1
            else -> throw RuntimeException("unreachable")
        }

        when (binary.operator.tokenType) {
            TokenType.PLUS -> {
                emit(instructions[0][typeIndex])
                doConvertPrimitive(binary.left.type, binary.type)
                decStack()
            }
            TokenType.MINUS -> {
                emit(instructions[1][typeIndex])
                doConvertPrimitive(binary.left.type, binary.type)
                decStack()
            }
            TokenType.STAR -> {
                emit(instructions[2][typeIndex])
                doConvertPrimitive(binary.left.type, binary.type)
                decStack()
            }
            TokenType.SLASH -> {
                emit(instructions[3][typeIndex])
                doConvertPrimitive(binary.left.type, binary.type)
                decStack()
            }
            TokenType.MOD -> {
                emit(instructions[4][typeIndex])
                doConvertPrimitive(binary.left.type, binary.type)
                decStack()
            }
            else -> TODO("not yet implemented")
        }
    }

    /**
     * emits the instruction to compare two non-integers
     */
    private fun doNonIntCompare(comparison: TokenType, compareInstruction: Byte) = when (comparison) {
        TokenType.GT -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(ifgt, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }
        TokenType.LT -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(iflt, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }
        TokenType.GT_EQ -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(iflt, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }
        TokenType.LT_EQ -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(ifgt, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }
        TokenType.D_EQ -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(ifne, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }
        TokenType.NOT_EQ -> {
            emit(compareInstruction)
            decStack()
            decStack()
            incStack(Datatype.Integer())
            emit(ifne, *Utils.getLastTwoBytes(7))
            decStack()
            emit(iconst_0)
            incStack(Datatype.Integer())
            emitStackMapFrame()
            emit(_goto, *Utils.getLastTwoBytes(4))
            decStack() // Because the first iconst instruction can be skipped
            emitStackMapFrame()
            emit(iconst_1)
            incStack(Datatype.Integer())
            emitStackMapFrame()
        }

        else -> throw RuntimeException("not a comparison")
    }

    /**
     * emits the instruction to convert the value on the top of the stack from '[from]' to '[to]'
     */
    private fun doConvertPrimitive(from: Datatype, to: Datatype) {
        if (from == to) return
        when (from.kind) {
            Datakind.BYTE -> when (to.kind) {
                Datakind.SHORT -> { }
                Datakind.INT -> { }
                Datakind.LONG -> emit(i2l)
                Datakind.FLOAT -> emit(i2f)
                Datakind.DOUBLE -> emit(i2d)
                else -> throw RuntimeException("unsupported type")
            }
            Datakind.SHORT -> when (to.kind) {
                Datakind.BYTE -> emit(i2b)
                Datakind.INT -> { }
                Datakind.LONG -> emit(i2l)
                Datakind.FLOAT -> emit(i2f)
                Datakind.DOUBLE -> emit(i2d)
                else -> throw RuntimeException("unsupported type")
            }
            Datakind.INT -> when (to.kind) {
                Datakind.BYTE -> emit(i2b)
                Datakind.SHORT -> emit(i2s)
                Datakind.LONG -> emit(i2l)
                Datakind.FLOAT -> emit(i2f)
                Datakind.DOUBLE -> emit(i2d)
                else -> throw RuntimeException("unsupported type")
            }
            Datakind.FLOAT -> when (to.kind) {
                Datakind.BYTE -> emit(f2i, i2b)
                Datakind.SHORT -> emit(f2i, i2s)
                Datakind.INT -> emit(f2i)
                Datakind.LONG -> emit(f2l)
                Datakind.DOUBLE -> emit(f2d)
                else -> throw RuntimeException("unsupported type")
            }
            else -> throw RuntimeException("unsupported type")
        }
        decStack()
        incStack(to)
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

        doDup()
        emit(invokespecial, *Utils.getLastTwoBytes(initMethodInfo))
        decStack()

        compile(exp.left, false)
        emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
        decStack()
        compile(exp.right, false)
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
        compile(exp.left, false)
        doDup()
        if (isAnd) emit(ifeq) else emit(ifne)
        decStack()
        emit(0x00.toByte(), 0x00.toByte())
        val jmpAddrPos = emitterTarget.curCodeOffset - 2
        compile(exp.right, false)
        if (isAnd) emit(iand) else emit(ior)
        decStack()
        emitStackMapFrame()
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrPos - 1)
        overwriteByteCode(jmpAddrPos, *Utils.getLastTwoBytes(jmpAddr))
    }

    override fun visit(literal: AstNode.Literal) {
        when (literal.type) {
            //literal.literal.literal.literal.literal.literal.literal.literal.literal.literal.liter...
            Datatype.Byte() -> emit(bipush, literal.literal.literal as Byte)
            Datatype.Short() -> emit(sipush, *Utils.getShortAsBytes(literal.literal.literal as Short))
            Datatype.Integer() -> emitIntLoad(literal.literal.literal as Int)
            Datatype.Float() -> emitFloatLoad(literal.literal.literal as Float)
            Datatype.Str() -> emitStringLoad(literal.literal.literal as String)
            Datatype.Long() -> emitLongLoad(literal.literal.literal as Long)
            Datatype.Bool() -> {
                if (literal.literal.literal as Boolean) emit(iconst_1)
                else emit(iconst_0)
            }
            else -> TODO("not yet implemented")
        }
        incStack(literal.type)
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) { //TODO: remove?
        compile(exprStmt.exp, true)
    }

    override fun visit(function: AstNode.Function) {
        function as AstNode.FunctionDeclaration

        curFunction = function

        val methodBuilder = MethodBuilder()
        method = MethodEmitter(methodBuilder)
        emitterTarget = method!!

        emitterTarget.stack = Stack()
        emitterTarget.locals = MutableList(function.amountLocals) { null }

        for (i in function.functionDescriptor.args.indices) {
            putTypeInLocals(i, function.functionDescriptor.args[i].second, false)
        }

        emitterTarget.maxStack = 0
        emitterTarget.lastStackMapFrameOffset = -1
        emitterTarget.maxLocals = function.amountLocals

        methodBuilder.descriptor = function.functionDescriptor.getDescriptorString()
        methodBuilder.descriptor = function.functionDescriptor.getDescriptorString()
        methodBuilder.name = function.name

        compile(function.statements, true)

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
            "override" -> { }
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
        curFunction = null

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
        curClass = null

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
        for (func in program.funcs) compile(func, true)
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

        for (clazz in program.classes) if (clazz !is SyntheticNode) doClass(clazz)
    }

    /**
     * compiles a class. Sets [file], [clinit], [init] and [emitterTarget].
     * Creates the output file and compiles all functions and fields associated with this class
     */
    private fun doClass(clazz: AstNode.ArtClass) {
        curFunction = null
        file = ClassFileBuilder()
        file.thisClass = clazz.jvmName
        file.superClass = clazz.extends!!.jvmName
        file.isSuper = true
        file.isPublic = true
        file.isPublic = true
        curFile = file.thisClass
        curClass = clazz

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

        for (func in clazz.staticFuncs) compile(func, true)
        for (func in clazz.funcs) compile(func, true)

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
    private fun doNonStaticFields(fields: List<AstNode.Field>) {
        if (fields.isEmpty()) return
        emitterTarget = init
        for (field in fields) compile(field, true)
    }

    /**
     * compiles the non-static fields of the current class. Sets [clinit]
     */
    private fun doStaticFields(fields: List<AstNode.Field>) {
        if (fields.isEmpty()) return
        emitterTarget = clinit
        for (field in fields) compile(field, true)
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

        var dataTypeToPrint = if (print.toPrint.type.kind in arrayOf(Datakind.OBJECT, Datakind.NULL, Datakind.ARRAY)) "Ljava/lang/Object;"
                                else print.toPrint.type.descriptorType

        //convert short and byte to their wrapper types before printing
        if (dataTypeToPrint == "S" || dataTypeToPrint == "B") {
            val className = if (dataTypeToPrint == "S") "java/lang/Short" else "java/lang/Byte"
            val valueOfInfo = file.methodRefInfo(
                file.classInfo(file.utf8Info(className)),
                file.nameAndTypeInfo(file.utf8Info("valueOf"), file.utf8Info("($dataTypeToPrint)L$className;"))
            )
            compile(print.toPrint, false)
            emit(invokestatic, *Utils.getLastTwoBytes(valueOfInfo))
            decStack()
            incStack(getObjVerificationType(className))
            dataTypeToPrint = "Ljava/lang/Object;"
        } else {
            compile(print.toPrint, false)
        }


        emit(invokevirtual)

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
        when (variable.type.kind) {
            Datakind.INT, Datakind.BOOLEAN, Datakind.SHORT, Datakind.BYTE -> emitIntVarLoad(variable.jvmIndex)
            Datakind.OBJECT, Datakind.ARRAY -> emitObjectVarLoad(variable.jvmIndex)
            Datakind.FLOAT -> emitFloatVarLoad(variable.jvmIndex)
            Datakind.LONG -> emitLongVarLoad(variable.jvmIndex)
            else -> TODO("variable load type not implemented")
        }
        incStack(variable.type)
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        compile(varDec.initializer, false)
//        if (varDec.varType == Datatype.Float() && varDec.initializer.type != Datatype.Float()) {
//            doConvertPrimitive(varDec.initializer.type, Datatype.Float())
//        }
        putTypeInLocals(varDec.jvmIndex, varDec.varType, true)
        decStack()
    }

    /**
     * puts the verification type associated with a datatype into the locals array of the current [emitterTarget]
     * @param emitStore if true, the function will also emit the correct store instruction
     */
    private fun putTypeInLocals(index: Int, type: Datatype, emitStore: Boolean) = when (type.kind) {
        Datakind.INT, Datakind.BOOLEAN, Datakind.SHORT, Datakind.BYTE -> {
            if (emitStore) emitIntVarStore(index)
            emitterTarget.locals[index] = VerificationTypeInfo.Integer()
        }
        Datakind.FLOAT -> {
            if (emitStore) emitFloatVarStore(index)
            emitterTarget.locals[index] = VerificationTypeInfo.Float()
        }
        Datakind.OBJECT -> {
            if (emitStore) emitObjectVarStore(index)
            emitterTarget.locals[index] = getObjVerificationType((type as Datatype.Object).clazz.jvmName)
        }
        Datakind.ARRAY -> {
            if (emitStore) emitObjectVarStore(index)
            emitterTarget.locals[index] = getObjVerificationType(type.descriptorType)
        }
        Datakind.LONG -> {
            if (emitStore) emitLongVarStore(index)
            emitterTarget.locals[index] = VerificationTypeInfo.Long()
            emitterTarget.locals[index + 1] = VerificationTypeInfo.Top()
        }
        else -> TODO("local store type not implemented")
    }

    override fun visit(varAssign: AstNode.Assignment) {
        if (varAssign.jvmIndex != -1) {
            doVarAssignForLocal(varAssign)
            return
        }

        if (varAssign.fieldDef!!.isStatic) {
            doVarAssignForStaticField(varAssign)
            return
        }

        //non static field

        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varAssign.fieldDef!!.clazz?.jvmName ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.name.lexeme),
                file.utf8Info(varAssign.fieldDef!!.fieldType.descriptorType)
            )
        )

        compile(varAssign.from!!, false)
        compile(varAssign.toAssign, false)

        if (varAssign.isWalrus) doDupBelow()

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
            file.classInfo(file.utf8Info(varAssign.fieldDef!!.clazz?.jvmName ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.name.lexeme),
                file.utf8Info(varAssign.fieldDef!!.fieldType.descriptorType)
            )
        )

        compile(varAssign.toAssign, false)
        if (varAssign.isWalrus) doDup()
        emit(putstatic)
        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
        return
    }

    /**
     * compiles an assignment to a local variable
     */
    private fun doVarAssignForLocal(varAssign: AstNode.Assignment) {
        compile(varAssign.toAssign, false)
        if (varAssign.isWalrus) doDup()
        when (varAssign.toAssign.type.kind) {
            Datakind.INT -> emitIntVarStore(varAssign.jvmIndex)
            Datakind.FLOAT -> emitFloatVarStore(varAssign.jvmIndex)
            Datakind.OBJECT, Datakind.NULL -> emitObjectVarStore(varAssign.jvmIndex)
            Datakind.BOOLEAN -> emitIntVarStore(varAssign.jvmIndex)
            else -> TODO("type for local assignment not implemented")
        }
        decStack()
        return
    }

    override fun visit(loop: AstNode.Loop) {
        emitStackMapFrame()
        val before = emitterTarget.curCodeOffset

        val loopContinueAddressBefore = loopContinueAddress
        val loopBreakAddressesToOverwriteBefore = loopBreakAddressesToOverwrite

        loopContinueAddress = before
        loopBreakAddressesToOverwrite = mutableListOf()

        compile(loop.body, true)
        val absOffset = (before - emitterTarget.curCodeOffset)
        emitGoto(absOffset)
        emitStackMapFrame()
        val offset = emitterTarget.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }

        loopContinueAddress = loopContinueAddressBefore
        loopBreakAddressesToOverwrite = loopBreakAddressesToOverwriteBefore
    }

    override fun visit(block: AstNode.Block) {
        val before = emitterTarget.locals.toMutableList()
        for (s in block.statements) {
            if (s is AstNode.YieldArrow) {
                compile(s, false)
                break
            } else compile(s, true)
        }
        emitterTarget.locals = before
    }

    override fun visit(ifStmt: AstNode.If) {
        val hasElse = ifStmt.elseStmt != null

        compile(ifStmt.condition, false)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2

        wasReturn = false
        compile(ifStmt.ifStmt, ifStmt.type.kind == Datakind.VOID)
        if (ifStmt.type.kind != Datakind.VOID) decStack()

        val skipGoto = wasReturn
        wasReturn = false

        if (hasElse && !skipGoto) emit(_goto, 0x00.toByte(), 0x00.toByte())
        val elseJmpAddrOffset = emitterTarget.curCodeOffset - 2
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrOffset - 1)
        overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        if (hasElse) emitStackMapFrame()
        if (hasElse) compile(ifStmt.elseStmt!!, ifStmt.type.kind == Datakind.VOID)

        val elseJmpAddr = emitterTarget.curCodeOffset - (elseJmpAddrOffset - 1)
        if (hasElse && !skipGoto) overwriteByteCode(elseJmpAddrOffset, *Utils.getLastTwoBytes(elseJmpAddr))
        emitStackMapFrame()
    }

    override fun visit(group: AstNode.Group) {
        compile(group.grouped, false)
    }

    override fun visit(unary: AstNode.Unary) {
        compile(unary.on, false)
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

        val loopContinueAddressBefore = loopContinueAddress
        val loopBreakAddressesToOverwriteBefore = loopBreakAddressesToOverwrite

        loopContinueAddress = startOffset
        loopBreakAddressesToOverwrite = mutableListOf()

        emitStackMapFrame()
        compile(whileStmt.condition, false)
        emit(ifeq, 0x00.toByte(), 0x00.toByte())
        decStack()
        val jmpAddrOffset = emitterTarget.curCodeOffset - 2
        compile(whileStmt.body, true)
        emit(_goto, *Utils.getShortAsBytes((startOffset - emitterTarget.curCodeOffset).toShort()))
        val jmpAddr = emitterTarget.curCodeOffset - (jmpAddrOffset - 1)
        overwriteByteCode(jmpAddrOffset, *Utils.getLastTwoBytes(jmpAddr))
        emitStackMapFrame()
        val offset = emitterTarget.curCodeOffset
        for (addr in loopBreakAddressesToOverwrite) {
            overwriteByteCode(addr, *Utils.getShortAsBytes((offset - (addr - 1)).toShort()))
        }

        loopContinueAddress = loopContinueAddressBefore
        loopBreakAddressesToOverwrite = loopBreakAddressesToOverwriteBefore

    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        if (funcCall.definition.isStatic || funcCall.definition.isTopLevel) {
            //static function
            for (arg in funcCall.arguments) compile(arg, false)
            emit(invokestatic)
            val funcRef = file.methodRefInfo(
                file.classInfo(file.utf8Info(
                    if (funcCall.definition.isTopLevel) topLevelName else funcCall.definition.clazz!!.jvmName
                )),
                file.nameAndTypeInfo(
                    file.utf8Info(funcCall.name.lexeme),
                    file.utf8Info(funcCall.definition.functionDescriptor.getDescriptorString())
                )
            )
            emit(*Utils.getLastTwoBytes(funcRef))
            repeat(funcCall.arguments.size) { decStack() }
            if (funcCall.type != Datatype.Void()) incStack(funcCall.type)
            return
        }

        //non-static function
        compile(funcCall.from!!, false)
        for (arg in funcCall.arguments) compile(arg, false)
        emit(invokevirtual)
        val funcRef = file.methodRefInfo(
            file.classInfo(file.utf8Info(funcCall.definition.clazz!!.jvmName)),
            file.nameAndTypeInfo(
                file.utf8Info(funcCall.name.lexeme),
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

        compile(returnStmt.toReturn!!, false)

        when (returnStmt.toReturn!!.type.kind) {
            Datakind.OBJECT, Datakind.ARRAY, Datakind.NULL -> emit(areturn)
            Datakind.INT, Datakind.SHORT, Datakind.BYTE -> emit(ireturn)
            Datakind.FLOAT -> emit(freturn)
            else -> TODO("return-type is not yet implemented")
        }


        decStack()
        wasReturn = true
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        emit(iinc, (varInc.jvmIndex and 0xFF).toByte(), varInc.toAdd)
    }

    override fun visit(varInc: AstNode.VarAssignShorthand) {

        if (varInc.toAdd.type == Datatype.Str()) {
            doVarAssignShortHandForStringConcat(varInc)
            return
        }

        if (varInc.jvmIndex != -1) {
            when (varInc.toAdd.type.kind) {
                Datakind.BYTE, Datakind.SHORT, Datakind.INT -> emitIntVarLoad(varInc.jvmIndex)
                Datakind.FLOAT -> emitFloatVarLoad(varInc.jvmIndex)
                else -> throw RuntimeException("unsupported type")
            }
            incStack(emitterTarget.locals[varInc.jvmIndex]!!)
            doVarAssignShortHandCalc(varInc)
            when (varInc.toAdd.type.kind) {
                Datakind.BYTE, Datakind.SHORT, Datakind.INT -> emitIntVarStore(varInc.jvmIndex)
                Datakind.FLOAT -> emitFloatVarStore(varInc.jvmIndex)
                else -> throw RuntimeException("unsupported type")
            }
            decStack()
            return
        }

        val fieldIndex = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varInc.fieldDef!!.clazz?.jvmName ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varInc.fieldDef!!.name),
                file.utf8Info(varInc.fieldDef!!.fieldType.descriptorType)
            )
        )

        if (varInc.fieldDef!!.isStatic) emit(getstatic, *Utils.getLastTwoBytes(fieldIndex))
        else {
            compile(varInc.from!!, false)
            doDup()
            emit(getfield, *Utils.getLastTwoBytes(fieldIndex))
            decStack()
        }

        incStack(varInc.fieldDef!!.fieldType)
        doVarAssignShortHandCalc(varInc)

        if (varInc.fieldDef!!.isStatic) emit(putstatic, *Utils.getLastTwoBytes(fieldIndex))
        else {
            emit(putfield, *Utils.getLastTwoBytes(fieldIndex))
            decStack()
        }
        decStack()
    }

    private fun doVarAssignShortHandForStringConcat(varAssign: AstNode.VarAssignShorthand) {
        val stringBuilderIndex = file.classInfo(file.utf8Info("java/lang/StringBuilder"))

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

        fun getStringBuilder() {
            emit(new, *Utils.getLastTwoBytes(stringBuilderIndex))
            incStack(getObjVerificationType("java/lang/StringBuilder"))
            doDup()
            emit(invokespecial, *Utils.getLastTwoBytes(initMethodInfo))
            decStack()
        }

        if (varAssign.jvmIndex != -1) {

            getStringBuilder()

            emitObjectVarLoad(varAssign.jvmIndex)
            incStack(Datatype.Str())

            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            compile(varAssign.toAdd, false)
            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            emit(invokevirtual, *Utils.getLastTwoBytes(toStringMethodInfo))
            decStack()
            incStack(Datatype.Str())
            emitObjectVarStore(varAssign.jvmIndex)
            decStack()
            return
        }

        val fieldIndex = file.fieldRefInfo(
            file.classInfo(file.utf8Info(varAssign.fieldDef!!.clazz?.jvmName ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(varAssign.fieldDef!!.name),
                file.utf8Info(varAssign.fieldDef!!.fieldType.descriptorType)
            )
        )

        if (varAssign.fieldDef!!.isStatic) {

            getStringBuilder()

            emit(getstatic, *Utils.getLastTwoBytes(fieldIndex))
            incStack(varAssign.fieldDef!!.fieldType)

            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            compile(varAssign.toAdd, false)
            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            emit(invokevirtual, *Utils.getLastTwoBytes(toStringMethodInfo))
            decStack()
            incStack(Datatype.Str())

            emit(putstatic, *Utils.getLastTwoBytes(fieldIndex))
            decStack()
        } else {
            compile(varAssign.from!!, false)
            doDup()
            emit(getfield, *Utils.getLastTwoBytes(fieldIndex))

            getStringBuilder()

            emit(swap) //TODO: are swaps dangerous with two byte data types?
            decStack()
            decStack()
            incStack(getObjVerificationType("java/lang/StringBuilder"))
            incStack(Datatype.Str())

            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            compile(varAssign.toAdd, false)
            emit(invokevirtual, *Utils.getLastTwoBytes(appendMethodInfo))
            decStack()

            emit(invokevirtual, *Utils.getLastTwoBytes(toStringMethodInfo))
            decStack()
            incStack(Datatype.Str())

            emit(putfield, *Utils.getLastTwoBytes(fieldIndex))
            decStack()
            decStack()
        }
    }

    private fun doVarAssignShortHandCalc(varAssign: AstNode.VarAssignShorthand) {
        compile(varAssign.toAdd, false)

        when (varAssign.toAdd.type.kind) {
            Datakind.BYTE, Datakind.SHORT, Datakind.INT -> emit(iadd)
            Datakind.FLOAT -> emit(fadd)
            else -> throw RuntimeException("unsupported type")
        }

        decStack()
    }

    override fun visit(get: AstNode.Get) {

        if (get.from != null && get.from!!.type.matches(Datakind.ARRAY) && get.name.lexeme == "size") {
            //special case for array size
            compile(get.from!!, false)
            emit(arraylength)
            decStack()
            incStack(Datatype.Integer())
            return
        }

        if (get.fieldDef == null) return //reference to static class, doesn't need to be compiled

        if (get.from == null) {
            //direct reference to either static field or top level field
            emit(getstatic)
            val fieldRef = file.fieldRefInfo(
                file.classInfo(file.utf8Info(
                    if (get.fieldDef!!.isTopLevel) topLevelName else get.fieldDef!!.clazz!!.jvmName
                )),
                file.nameAndTypeInfo(
                    file.utf8Info(get.fieldDef!!.name),
                    file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
                )
            )
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(get.fieldDef!!.fieldType)
            return
        }

        if (get.fieldDef!!.isStatic || get.fieldDef!!.isTopLevel) {
            //reference to static or top level field with a from specified
            emit(getstatic)
            val fieldRef = file.fieldRefInfo(
                file.classInfo(file.utf8Info((get.from!!.type as Datatype.StatClass).clazz.jvmName)),
                file.nameAndTypeInfo(
                    file.utf8Info(get.name.lexeme),
                    file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
                )
            )
            emit(*Utils.getLastTwoBytes(fieldRef))
            incStack(get.fieldDef!!.fieldType)
            return
        }

        //a non-static field

        compile(get.from!!, false)
        emit(getfield)

        val fieldRef = file.fieldRefInfo(
            file.classInfo(file.utf8Info((get.from!!.type as Datatype.Object).clazz.jvmName)),
            file.nameAndTypeInfo(
                file.utf8Info(get.name.lexeme),
                file.utf8Info(get.fieldDef!!.fieldType.descriptorType)
            )
        )
        emit(*Utils.getLastTwoBytes(fieldRef))
        decStack()
        incStack(get.fieldDef!!.fieldType)
    }

    override fun visit(arr: AstNode.ArrGet) {
        compile(arr.from, false)
        compile(arr.arrIndex, false)
        emitALoad(arr.type)
        decStack()
        decStack()
        incStack(arr.type)
    }

    override fun visit(arr: AstNode.ArrSet) {
        compile(arr.from, false)
        compile(arr.arrIndex, false)
        compile(arr.to, false)

        if (arr.isWalrus) doDupX2()

        emitAStore(arr.to.type)
        decStack()
        decStack()
        decStack()
    }

    /**
     * emits the array store instruction for the type
     */
    fun emitAStore(type: Datatype) = when (type.kind) {
        Datakind.BYTE, Datakind.SHORT, Datakind.INT -> emit(iastore)
        Datakind.FLOAT -> emit(fastore)
        Datakind.LONG -> emit(lastore)
        Datakind.OBJECT, Datakind.ARRAY, Datakind.NULL -> emit(aastore)
        else -> TODO("only int, string and object arrays are implemented")
    }

    /**
     * emits the array load instruction for the type
     */
    fun emitALoad(type: Datatype) = when (type.kind) {
        Datakind.BYTE, Datakind.SHORT, Datakind.INT -> emit(iaload)
        Datakind.FLOAT -> emit(faload)
        Datakind.OBJECT, Datakind.ARRAY -> emit(aaload)
        Datakind.LONG -> emit(laload)
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
        emit(new, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(constructorCall.clazz.jvmName))))
        incStack(constructorCall.type)
        doDup()

        val methodIndex = file.methodRefInfo(
            file.classInfo(file.utf8Info(constructorCall.clazz.name)),
            file.nameAndTypeInfo(
                file.utf8Info("<init>"),
                file.utf8Info("()V")
            )
        )
        emit(invokespecial, *Utils.getLastTwoBytes(methodIndex))
        decStack()
    }

    override fun visit(yieldArrow: AstNode.YieldArrow) {
        compile(yieldArrow.expr, false)
    }

    /**
     * assumes [emitterTarget] is set correctly
     */
    override fun visit(field: AstNode.Field) {
        field as AstNode.FieldDeclaration

        val fieldToAdd = Field(
            file.utf8Info(field.name),
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
            incStack(getObjVerificationType(curClass!!.jvmName))
        }

        compile(field.initializer, false)

        val fieldRefIndex = file.fieldRefInfo(
            file.classInfo(file.utf8Info(curClass?.jvmName ?: topLevelName)),
            file.nameAndTypeInfo(
                file.utf8Info(field.name),
                file.utf8Info(field.fieldType.descriptorType)
            )
        )

        if (field.isStatic || field.isTopLevel) emit(putstatic, *Utils.getLastTwoBytes(fieldRefIndex))
        else emit(putfield, *Utils.getLastTwoBytes(fieldRefIndex))
        decStack()
        if (!field.isStatic && !field.isTopLevel) decStack()
    }

    override fun visit(arr: AstNode.ArrayCreate) {
        if (arr.amounts.size == 1) doOneDimensionalArray(arr)
        else doMultiDimensionalArray(arr)
    }

    /**
     * compiles a multi-dimensional array-create
     */
    private fun doMultiDimensionalArray(arr: AstNode.ArrayCreate) {
        for (amount in arr.amounts) compile(amount, false)
        emit(
            multianewarray,
            *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(arr.type.descriptorType))),
            Utils.getLastTwoBytes(arr.amounts.size)[1]
        )
        repeat(arr.amounts.size) { decStack() }
        incStack(arr.type)
    }

    /**
     * compiles a single-dimensional array-create
     */
    private fun doOneDimensionalArray(arr: AstNode.ArrayCreate) {
        when (val kind = (arr.type as Datatype.ArrayType).type.kind) {
            Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.FLOAT, Datakind.LONG -> {
                compile(arr.amounts[0], false)
                emit(newarray, getAType(kind))
                decStack()
                incStack(arr.type)
            }
            Datakind.OBJECT -> {
                compile(arr.amounts[0], false)
                emit(
                    anewarray, *Utils.getLastTwoBytes(
                        file.classInfo(
                            file.utf8Info(
                                ((arr.type as Datatype.ArrayType).type as Datatype.Object).clazz.jvmName
                            )
                        )
                    )
                )
                decStack()
                incStack(arr.type)
            }
            else -> TODO("array creation type not implemented")
        }
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        when (val kind = (arr.type as Datatype.ArrayType).type.kind) {

            Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.FLOAT, Datakind.LONG -> {

                val storeInstruction = when (kind) {
                    Datakind.INT -> iastore
                    Datakind.FLOAT -> fastore
                    Datakind.LONG -> lastore
                    else -> throw RuntimeException("unreachable")
                }

                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(newarray, getAType(kind))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    doDup()
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i], false)
                    emit(storeInstruction)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            Datakind.OBJECT -> {
                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(
                    ((arr.type as Datatype.ArrayType).type as Datatype.Object).clazz.jvmName
                ))))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    doDup()
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i], false)
                    emit(aastore)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            Datakind.ARRAY -> {
                val arrType = arr.type as Datatype.ArrayType
                emitIntLoad(arr.elements.size)
                incStack(Datatype.Integer())
                emit(anewarray, *Utils.getLastTwoBytes(file.classInfo(file.utf8Info(arrType.type.descriptorType))))
                decStack()
                incStack(arr.type)
                for (i in arr.elements.indices) {
                    doDup()
                    emitIntLoad(i)
                    incStack(Datatype.Integer())
                    compile(arr.elements[i], false)
                    emit(aastore)
                    decStack()
                    decStack()
                    decStack()
                }
            }
            else -> TODO("array literal type not implemented")
        }
    }

    override fun visit(nul: AstNode.Null) {
        emit(aconst_null)
        incStack(Datatype.NullType())
    }

    override fun visit(convert: AstNode.TypeConvert) {
        compile(convert.toConvert, false)
        doConvertPrimitive(convert.toConvert.type, convert.type)
    }

    /**
     * the [newarray] instruction uses an operand to tell it the type of array. This functions returns
     * the byte for a specific type
     */
    private fun getAType(type: Datakind): Byte = when (type) {
        Datakind.INT -> 10
        Datakind.BOOLEAN -> 4
        Datakind.FLOAT -> 6
        Datakind.LONG -> 11
        else -> TODO("Not yet implemented")
    }

    /**
     * emits the default constructor. assumes [emitterTarget] is set correctly, super is set "java/lang/Object" and does
     * not return
     */
    private fun doDefaultConstructor() {
        val objConstructorIndex = file.methodRefInfo(
            file.classInfo(file.utf8Info(curClass?.extends?.jvmName ?: "java/lang/Object")),
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
    private fun doIntCompare(compareInstruction: Byte) {
        emitStackMapFrame()
        emit(compareInstruction, *Utils.getShortAsBytes(7.toShort()))
        decStack()
        decStack()
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
     * emits either a dup or dup2 instruction depending on the type and updates the stack
     */
    private fun doDup() {
        if (emitterTarget.stack.peek() is VerificationTypeInfo.Top) {
            emit(dup2)
            incStack(emitterTarget.stack[emitterTarget.stack.size - 2])
            incStack(emitterTarget.stack[emitterTarget.stack.size - 2])
        } else {
            emit(dup)
            incStack(emitterTarget.stack.peek())
        }
    }

    /**
     * emits either a dup_x1 or dup2_x1 instruction depending on the type and updates the stack
     */
    private fun doDupBelow() {
        if (emitterTarget.stack.peek() is VerificationTypeInfo.Top) {
            if (emitterTarget.stack[emitterTarget.stack.size - 3] is VerificationTypeInfo.Top) doDupX2()
            else doDupX1()
        } else {
            if (emitterTarget.stack[emitterTarget.stack.size - 2] is VerificationTypeInfo.Top) doDupX2()
            else doDupX1()
        }
    }

    private fun doDupX1() {
        if (emitterTarget.stack.peek() is VerificationTypeInfo.Top) {
            emit(dup2_x1)
            val top = emitterTarget.stack.pop()
            val middle = emitterTarget.stack.pop()
            val bottom = emitterTarget.stack.pop()
            incStack(middle)
            incStack(top)
            incStack(bottom)
            incStack(middle)
            incStack(top)
        } else {
            emit(dup_x1)
            val top = emitterTarget.stack.pop()
            val bottom = emitterTarget.stack.pop()
            incStack(top)
            incStack(bottom)
            incStack(top)
        }
    }

    private fun doDupX2() {
        if (emitterTarget.stack.peek() is VerificationTypeInfo.Top) {
            emit(dup2_x2)
            val top1 = emitterTarget.stack.pop()
            val top2 = emitterTarget.stack.pop()
            val m1 = emitterTarget.stack.pop()
            val m2 = emitterTarget.stack.pop()
            incStack(top2)
            incStack(top1)
            incStack(m2)
            incStack(m1)
            incStack(top2)
            incStack(top1)
        } else {
            emit(dup_x2)
            val top = emitterTarget.stack.pop()
            val m1 = emitterTarget.stack.pop()
            val m2 = emitterTarget.stack.pop()
            incStack(top)
            incStack(m2)
            incStack(m1)
            incStack(top)
        }
    }

    /**
     * emits a StackMapFrame at the current offset in the byte code using the information from the
     * stack and locals array
     */
    private fun emitStackMapFrame() {

        //TODO: instead of always emitting FullStackMapFrames also use other types of StackMapFrames

        val offsetDelta = if (emitterTarget.lastStackMapFrameOffset == -1) emitterTarget.curCodeOffset
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
     * loads a long constant
     */
    private fun emitLongLoad(l: Long) = when (l) {
        0L -> emit(lconst_0)
        1L -> emit(lconst_1)
        else -> {
            val longIndex = file.longInfo(l)
            emit(ldc2_w, *Utils.getLastTwoBytes(longIndex))
        }
    }

    /**
     * loads an integer constant
     */
    private fun emitFloatLoad(f: Float) = when (f) {
        0F -> emit(fconst_0)
        1F -> emit(fconst_1)
        2F -> emit(fconst_2)
        else -> emitLdc(file.floatInfo(f))
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
     * loads a float variable
     */
    private fun emitFloatVarLoad(index: Int) = when (index) {
        0 -> emit(fload_0)
        1 -> emit(fload_1)
        2 -> emit(fload_2)
        3 -> emit(fload_3)
        else -> emit(fload, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * loads a long variable
     */
    private fun emitLongVarLoad(index: Int) = when (index) {
        0 -> emit(lload_0)
        1 -> emit(lload_1)
        2 -> emit(lload_2)
        3 -> emit(lload_3)
        else -> emit(lload, (index and 0xFF).toByte())
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
     * stores a float in a variable
     */
    private fun emitFloatVarStore(index: Int) = when (index) {
        0 -> emit(fstore_0)
        1 -> emit(fstore_1)
        2 -> emit(fstore_2)
        3 -> emit(fstore_3)
        else -> emit(fstore, (index and 0xFF).toByte()) //TODO: wide
    }

    /**
     * stores a long in a variable
     */
    private fun emitLongVarStore(index: Int) = when (index) {
        0 -> emit(lstore_0)
        1 -> emit(lstore_1)
        2 -> emit(lstore_2)
        3 -> emit(lstore_3)
        else -> emit(lstore, (index and 0xFF).toByte())
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
     * emits the correct pop-instruction for the datatype on top of the stack
     */
    private fun doPop() {
        if (emitterTarget.stack.peek() is VerificationTypeInfo.Top) emit(pop2)
        else emit(pop)
        decStack()
    }

    /**
     * compiles a node
     * @param node the node to compile
     * @param forceNoValueOnStack if true, the compile function will check if the compiled node left a value on the
     * stack, and if so pop it
     */
    private fun compile(node: AstNode, forceNoValueOnStack: Boolean) {
        node.accept(this)
        if (forceNoValueOnStack && emitterTarget.stack.size != 0) {
            println(emitterTarget.stack.size)
            doPop()
        }
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
        when (type.kind) {
            Datakind.INT, Datakind.BOOLEAN, Datakind.BYTE, Datakind.SHORT -> {
                emitterTarget.stack.push(VerificationTypeInfo.Integer())
            }
            Datakind.FLOAT -> emitterTarget.stack.push(VerificationTypeInfo.Float())
            Datakind.OBJECT -> emitterTarget.stack.push(getObjVerificationType((type as Datatype.Object).clazz.jvmName))
            Datakind.ARRAY -> emitterTarget.stack.push(getObjVerificationType(type.descriptorType))
            Datakind.NULL -> emitterTarget.stack.push(VerificationTypeInfo.Null())
            Datakind.LONG -> {
                emitterTarget.stack.push(VerificationTypeInfo.Long())
                emitterTarget.stack.push(VerificationTypeInfo.Top())
            }
            else -> TODO("not yet implemented")
        }
        if (emitterTarget.stack.size > emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    /**
     * adds an element at [at] to the stack of the current [emitterTarget]. Sets the maxStack property if necessary.
     * If [type] is Long or Double, VerificationType.Long/Double is inserted at `at - 1` and VerificationType.Top is
     * inserted at `at`.
     */
    private fun incStackAt(at: Int, type: Datatype) {
        when (type.kind) {
            Datakind.INT, Datakind.BOOLEAN, Datakind.BYTE, Datakind.SHORT -> {
                emitterTarget.stack.add(at, VerificationTypeInfo.Integer())
            }
            Datakind.FLOAT -> emitterTarget.stack.add(at, VerificationTypeInfo.Float())
            Datakind.OBJECT -> emitterTarget.stack.add(at, getObjVerificationType((type as Datatype.Object).clazz.jvmName))
            Datakind.ARRAY -> emitterTarget.stack.add(at, getObjVerificationType(type.descriptorType))
            Datakind.NULL -> emitterTarget.stack.add(at, VerificationTypeInfo.Null())
            Datakind.LONG -> {
                emitterTarget.stack.add(at - 1, VerificationTypeInfo.Long())
                emitterTarget.stack.add(at, VerificationTypeInfo.Top())
            }
            else -> TODO("not yet implemented")
        }
        if (emitterTarget.stack.size > emitterTarget.maxStack) emitterTarget.maxStack = emitterTarget.stack.size
    }

    /**
     * decrements the stack of the current [emitterTarget]; twice if VerificationType.Top is at the top of the stack
     */
    private fun decStack() {
        if (emitterTarget.stack.pop() is VerificationTypeInfo.Top) emitterTarget.stack.pop()
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

        override var lastStackMapFrameOffset: Int = -1

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

        // I'm not writing javadoc for each instruction, look them up here:
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

        const val fadd: Byte = 0x62.toByte()
        const val fdiv: Byte = 0x6E.toByte()
        const val fmul: Byte = 0x6A.toByte()
        const val fneg: Byte = 0x76.toByte()
        const val frem: Byte = 0x72.toByte()
        const val fsub: Byte = 0x66.toByte()

        const val ladd: Byte = 0x61.toByte()
        const val lsub: Byte = 0x65.toByte()
        const val lmul: Byte = 0x69.toByte()
        const val ldiv: Byte = 0x6D.toByte()
        const val lneg: Byte = 0x6D.toByte()
        const val lrem: Byte = 0x71.toByte()

        const val iconst_m1: Byte = 0x02.toByte()
        const val iconst_0: Byte = 0x03.toByte()
        const val iconst_1: Byte = 0x04.toByte()
        const val iconst_2: Byte = 0x05.toByte()
        const val iconst_3: Byte = 0x06.toByte()
        const val iconst_4: Byte = 0x07.toByte()
        const val iconst_5: Byte = 0x08.toByte()

        const val fconst_0: Byte = 0x0b.toByte()
        const val fconst_1: Byte = 0x0c.toByte()
        const val fconst_2: Byte = 0x0d.toByte()

        const val lconst_0: Byte = 0x09.toByte()
        const val lconst_1: Byte = 0x0A.toByte()

        const val aconst_null: Byte = 0x01.toByte()

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

        const val fstore: Byte = 0x38.toByte()
        const val fstore_0: Byte = 0x43.toByte()
        const val fstore_1: Byte = 0x44.toByte()
        const val fstore_2: Byte = 0x45.toByte()
        const val fstore_3: Byte = 0x46.toByte()

        const val fload: Byte = 0x17.toByte()
        const val fload_0: Byte = 0x22.toByte()
        const val fload_1: Byte = 0x23.toByte()
        const val fload_2: Byte = 0x24.toByte()
        const val fload_3: Byte = 0x25.toByte()

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

        const val lstore: Byte = 0x37.toByte()
        const val lstore_0: Byte = 0x3f.toByte()
        const val lstore_1: Byte = 0x40.toByte()
        const val lstore_2: Byte = 0x41.toByte()
        const val lstore_3: Byte = 0x42.toByte()

        const val lload: Byte = 0x16.toByte()
        const val lload_0: Byte = 0x1E.toByte()
        const val lload_1: Byte = 0x1F.toByte()
        const val lload_2: Byte = 0x20.toByte()
        const val lload_3: Byte = 0x21.toByte()

        const val pop: Byte = 0x57.toByte()
        const val pop2: Byte = 0x58.toByte()
        const val swap: Byte = 0x5F.toByte()

        const val dup: Byte = 0x59.toByte()
        const val dup_x1: Byte = 0x5A.toByte()
        const val dup_x2: Byte = 0x5B.toByte()
        const val dup2: Byte = 0x5C.toByte()
        const val dup2_x1: Byte = 0x5D.toByte()
        const val dup2_x2: Byte = 0x5E.toByte()

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
        const val freturn: Byte = 0xAE.toByte()

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

        const val fcmpg: Byte = 0x96.toByte()
        const val fcmpl: Byte = 0x95.toByte()

        const val lcmp: Byte = 0x94.toByte()

        const val aaload: Byte = 0x32.toByte()
        const val aastore: Byte = 0x53.toByte()
        const val anewarray: Byte = 0xBD.toByte()
        const val multianewarray: Byte = 0xC5.toByte()
        const val arraylength: Byte = 0xBE.toByte()

        const val iastore: Byte = 0x4F.toByte()
        const val fastore: Byte = 0x51.toByte()
        const val lastore: Byte = 0x50.toByte()

        const val iaload: Byte = 0x2E.toByte()
        const val faload: Byte = 0x30.toByte()
        const val laload: Byte = 0x2F.toByte()

        const val newarray: Byte = 0xBC.toByte()

        const val i2b: Byte = 0x91.toByte()
        const val i2c: Byte = 0x92.toByte()
        const val i2d: Byte = 0x87.toByte()
        const val i2f: Byte = 0x86.toByte()
        const val i2l: Byte = 0x85.toByte()
        const val i2s: Byte = 0x93.toByte()

        const val f2d: Byte = 0x8d.toByte()
        const val f2i: Byte = 0x8b.toByte()
        const val f2l: Byte = 0x8c.toByte()

        const val d2f: Byte = 0x90.toByte()
        const val d2i: Byte = 0x8e.toByte()
        const val d2l: Byte = 0x8f.toByte()

        const val l2d: Byte = 0x8a.toByte()
        const val l2f: Byte = 0x89.toByte()
        const val l2i: Byte = 0x88.toByte()

    }
}
