package parser

import ast.AstNode
import ast.AstPrinter
import errors.artError
import tokenizer.Token
import tokenizer.TokenType
import Datakind
import kotlin.RuntimeException
import errors.Errors

object Parser {

    private var cur: Int = 0
    private var tokens: List<Token> = listOf()
    private var srcCode: String = ""

    fun parse(tokens: List<Token>, code: String): AstNode.Program {
        cur = 0
        this.tokens = tokens
        srcCode = code

        val functions = mutableListOf<AstNode.Function>()
        val classes = mutableListOf<AstNode.ArtClass>()
        val fields = mutableListOf<AstNode.Field>()
        while (!match(TokenType.EOF)) try {

            val modifiers = parseModifiers()
            if (modifiers.isNotEmpty()) artError(Errors.ModifiersInTopLevelError(modifiers, srcCode))

            if (match(TokenType.K_FN)) {
                functions.add(parseFunc(listOf(), true))
                continue
            }
            if (match(TokenType.K_CLASS)) {
                classes.add(parseClass(listOf()))
                continue
            }
            if (matchIdent("field")) {

                fields.add(parseFieldDeclaration(listOf(), false, true))
                continue
            }
            if (match(TokenType.K_CONST)) {
                consumeIdentOrError("field", "Expected field declaration after const")
                fields.add(parseFieldDeclaration(listOf(),true, true))
                continue
            }

            artError(Errors.SyntaxError(consume(), "Expected either a field, a class or a function in the " +
                    "top level", srcCode))
            resyncTopLevel()

        } catch(e: ParserResyncException) {
            resyncTopLevel()
            continue
        }
        return AstNode.Program(functions, classes, fields)
    }

    fun resyncTopLevel() {
        while (peek().tokenType !in arrayOf(TokenType.K_FN, TokenType.K_CLASS, TokenType.K_CONST, TokenType.EOF) &&
            !(peek().tokenType == TokenType.IDENTIFIER && peek().lexeme == "field")) cur++
    }

    private fun parseFieldDeclaration(modifiers: List<Token>, isConst: Boolean, isTopLevel: Boolean)
    : AstNode.Field {
        consumeOrError(TokenType.IDENTIFIER, "expected name")
        val name = last()
        consumeOrError(TokenType.COLON, "Field-definitions always require a explicit type")
        val type = parseType()
        consumeOrError(TokenType.EQ, "")
        val initializer = parseStatement()
        return AstNode.FieldDeclaration(name, type, initializer, isConst, modifiers, isTopLevel)
    }

    private fun parseFunc(modifiers: List<Token>, isTopLevel: Boolean): AstNode.Function {
        consumeOrError(TokenType.IDENTIFIER, "Expected function name")
        val funcName = last()
        consumeOrError(TokenType.L_PAREN, "Expected () after function name")

        if (funcName.lexeme == "main" && modifiers.isNotEmpty()) {
            artError(Errors.InvalidMainFunctionDeclarationError("main function must not have modifiers",
                srcCode, modifiers))
        }

        val args = mutableListOf<Pair<Token, AstNode.DatatypeNode>>()

        while (match(TokenType.IDENTIFIER)) {
            val name = last()
            consumeOrError(TokenType.COLON, "Expected type-declaration after argument")
            val type = parseType()
            args.add(Pair(name, type))
            if (!match(TokenType.COMMA)) break
        }

        consumeOrError(TokenType.R_PAREN, "Expected () after function name")

        var returnType: AstNode.DatatypeNode? = null
        if (match(TokenType.COLON)) returnType = parseType()

        consumeOrError(TokenType.L_BRACE, "Expected code block after function declaration")

        val function = AstNode.FunctionDeclaration(parseBlock(), funcName,
            if (funcName.lexeme == "main") listOf() else modifiers, isTopLevel)
        function.args = args
        function.returnType = returnType

        return function
    }

    private fun parseClass(classModifiers: List<Token>): AstNode.ArtClass {
        consumeOrError(TokenType.IDENTIFIER, "Expected class name")
        val name = last()

        var extends: Token? = null
        if (match(TokenType.COLON)) {
            consumeOrError(TokenType.IDENTIFIER, "Expected name of class to extend")
            extends = last()
        }

        val interfaces = mutableListOf<Token>()
        if (match(TokenType.TILDE)) {
            while (match(TokenType.IDENTIFIER)) {
                interfaces.add(last())
                if (!match(TokenType.COMMA)) break
            }
        }

        consumeOrError(TokenType.L_BRACE, "Expected opening brace after class definition")

        val funcs = mutableListOf<AstNode.Function>()
        val staticFuncs = mutableListOf<AstNode.Function>()
        val fields = mutableListOf<AstNode.Field>()
        val staticFields = mutableListOf<AstNode.Field>()

        while (!match(TokenType.R_BRACE)) try {
            val modifiers = parseModifiers()

            if (match(TokenType.K_FN)) {
                validateModifiersForFunc(modifiers)
                val func = parseFunc(modifiers, false)
                if (func.isStatic) staticFuncs.add(func) else funcs.add(func)
                continue
            }
            if (matchIdent("field")) {
                validateModifiersForField(modifiers)
                val field = parseFieldDeclaration(modifiers, false, false)
                if (field.isStatic) staticFields.add(field) else fields.add(field)
                continue
            }
            if (match(TokenType.K_CONST)) {
                consumeIdentOrError("field", "expected field declaration after const")
                val field = parseFieldDeclaration(modifiers, true, false)
                if (field.isStatic) staticFields.add(field) else fields.add(field)
                continue
            }
            artError(Errors.SyntaxError(consume(), "Expected a function or field declaration in class", srcCode))
            resyncClass()
        } catch (e: ParserResyncException) {
            resyncClass()
            continue
        }

        return AstNode.ClassDefinition(name, staticFuncs, funcs, staticFields, fields, extends)
    }

    private fun resyncClass() {
        while (peek().tokenType !in arrayOf(TokenType.K_FN, TokenType.K_CONST, TokenType.EOF) &&
            !(peek().tokenType == TokenType.IDENTIFIER && peek().lexeme == "field")) cur++
    }

    private fun parseModifiers(): List<Token> {
        val modifiers = mutableListOf<Token>()
        while (matchIdent("public", "abstract", "static")) modifiers.add(last())
        return modifiers
    }

    private fun validateModifiersForFunc(modifiers: List<Token>) {
        val had = mutableListOf<String>()
        for (modifier in modifiers) {
            if (modifier.lexeme in had) {
                artError(Errors.InvalidModifierError("duplicate modifier ${modifier.lexeme}", modifier, srcCode))
                continue
            }
            when (modifier.lexeme) {
                "abstract" -> TODO("abstract functions are not yet implemented")
                "public" -> had.add("public")
                "static" -> had.add("static")
                else -> throw RuntimeException("unknown modifier ${modifier.lexeme}")
            }
        }
    }

    private fun validateModifiersForField(modifiers: List<Token>) {
        val had = mutableListOf<String>()
        for (modifier in modifiers) {
            if (modifier.lexeme in had) {
                artError(Errors.InvalidModifierError("duplicate modifier ${modifier.lexeme}", modifier, srcCode))
                continue
            }
            when (modifier.lexeme) {
                "abstract" -> {
                    artError(Errors.InvalidModifierError("Field cannot be abstract", modifier, srcCode))
                    continue
                }
                "public" -> had.add("public")
                "static" -> had.add("static")
                else -> throw RuntimeException("unknown modifier ${modifier.lexeme}")
            }
        }
    }

    private fun validateModifiersForClass(modifiers: List<Token>) {
        val had = mutableListOf<String>()
        for (modifier in modifiers) {
            if (modifier.lexeme in had) {
                artError(Errors.InvalidModifierError("duplicate modifier ${modifier.lexeme}", modifier, srcCode))
                continue
            }
            when (modifier.lexeme) {
                "abstract" -> TODO("abstract classes are not implemented")
                "public" -> throw RuntimeException("class cant be public")
                "static" -> throw RuntimeException("class cant be static")
                else -> throw RuntimeException("unknown modifier ${modifier.lexeme}")
            }
        }
    }

    private fun parseStatement(): AstNode {
        if (match(TokenType.L_BRACE)) return parseBlock()
        if (match(TokenType.K_PRINT)) return parsePrint()
        if (match(TokenType.K_LET)) return parseVariableDeclaration(false)
        if (match(TokenType.K_CONST)) return parseVariableDeclaration(true)
        if (match(TokenType.K_LOOP)) return parseLoop()
        if (match(TokenType.K_IF)) return parseIf()
        if (match(TokenType.K_WHILE)) return parseWhileLoop()
        if (match(TokenType.K_RETURN)) return parseReturn()
        if (match(TokenType.K_BREAK)) return AstNode.Break(last())
        if (match(TokenType.K_CONTINUE)) return AstNode.Continue(last())

        return parseAssignment()
    }

    private fun parseVarAssignShorthand(variable: AstNode.Get, op: Token, num: AstNode): AstNode {
        when (op.tokenType) {
            TokenType.STAR_EQ -> return AstNode.Assignment(
                variable.from,
                variable.name,
                AstNode.Binary(
                    variable,
                    Token(TokenType.STAR, "*=", null, op.file, op.pos, op.line),
                    num
                ),
                false
            )
            TokenType.SLASH_EQ -> return AstNode.Assignment(
                variable.from,
                variable.name,
                AstNode.Binary(
                    variable,
                    Token(TokenType.SLASH, "/=", null, op.file, op.pos, op.line),
                    num
                ),
                false
            )
            TokenType.PLUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(variable, num.literal.literal.toByte())
                }
                return AstNode.Assignment(
                    variable.from,
                    variable.name,
                    AstNode.Binary(
                        variable,
                        Token(TokenType.PLUS, "+=", null, op.file, op.pos, op.line),
                        num
                    ),
                    false
                )
            }
            TokenType.MINUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(variable, (-num.literal.literal).toByte())
                }
                return AstNode.Assignment(
                    variable.from,
                    variable.name,
                    AstNode.Binary(
                        variable,
                        Token(TokenType.MINUS, "-=", null, op.file, op.pos, op.line),
                        num
                    ),
                    false
                )
            }
            else -> throw RuntimeException("unreachable")
        }
    }

    private fun parseReturn(): AstNode.Return {
        val returnToken = last()
        if (matchNSFB(TokenType.SOFT_BREAK)) return AstNode.Return(null, returnToken)
        val returnExpr = parseStatement()
        return AstNode.Return(returnExpr, returnToken)
    }

    private fun parseWhileLoop(): AstNode {
        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after while")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val body = parseStatement()
        if (body is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(body, srcCode))
        }

        return AstNode.While(body, condition)
    }

    private fun parseIf(): AstNode {

        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after if")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val ifStmt = parseStatement()
        if (ifStmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(ifStmt, srcCode))
        }

        if (!match(TokenType.K_ELSE)) return AstNode.If(ifStmt, null, condition)

        val elseStmt = parseStatement()
        if (elseStmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(elseStmt, srcCode))
        }

        return AstNode.If(ifStmt, elseStmt, condition)
    }

    private fun parseLoop(): AstNode {
        val stmt = parseStatement()
        if (stmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(stmt, srcCode))
        }
        return AstNode.Loop(stmt)
    }

    private fun parseVariableDeclaration(isConst: Boolean): AstNode.VariableDeclaration {
        val decToken = last()
        consumeOrError(TokenType.IDENTIFIER, "expected identifier after let/const")
        val name = last()
        var type: AstNode.DatatypeNode? = null
        if (match(TokenType.COLON)) type = parseType()
        consumeOrError(TokenType.EQ, "initializer expected")
        val initializer = parseStatement()
        val stmt = AstNode.VariableDeclaration(name, initializer, isConst, decToken)
        stmt.explType = type
        return stmt
    }

    private fun parseAssignment(): AstNode {
        val left = parseBooleanComparison()
        if (match(TokenType.EQ)) {
            if (left is AstNode.Get) return AstNode.Assignment(left.from, left.name, parseStatement(), false)
            else {
                artError(Errors.InvalidAssignmentTargetError(left, srcCode))
                throw ParserResyncException()
            }
        }
        if (match(TokenType.WALRUS)) {
            if (left is AstNode.Get) return AstNode.Assignment(left.from, left.name, parseStatement(), true)
            else {
                artError(Errors.InvalidAssignmentTargetError(left, srcCode))
                throw ParserResyncException()
            }
        }
        if (match(TokenType.PLUS_EQ, TokenType.MINUS_EQ, TokenType.STAR_EQ, TokenType.SLASH_EQ)) {
            if (left !is AstNode.Get) {
                artError(Errors.InvalidAssignmentTargetError(left, srcCode))
                throw ParserResyncException()
            }
            return parseVarAssignShorthand(left, last(), parseStatement())
        }
        return left
    }

    private fun parseBooleanComparison(): AstNode {
        var left = parseComparison()
        while (match(TokenType.D_AND, TokenType.D_OR)) { //TODO: fix priority
            val operator = last()
            val right = parseComparison()
            left = AstNode.Binary(left, operator, right)
        }
        return left
    }

    private fun parseComparison(): AstNode {
        var left = parseTermExpression()
        while (match(TokenType.D_EQ, TokenType.LT, TokenType.LT_EQ, TokenType.GT, TokenType.GT_EQ, TokenType.NOT_EQ)) {
            val operator = last()
            val right = parseTermExpression()
            left = AstNode.Binary(left, operator, right)
        }
        return left
    }

    private fun parseTermExpression(): AstNode {
        var left = parseFactorExpression()
        while (matchNSFB(TokenType.PLUS, TokenType.MINUS)) {
            val operator = last()
            val right = parseFactorExpression()
            left = AstNode.Binary(left, operator, right)
        }
        return left
    }

    private fun parseFactorExpression(): AstNode {
        var left = parseUnaryExpression()
        while (matchNSFB(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            val operator = last()
            val right = parseUnaryExpression()
            left = AstNode.Binary(left, operator, right)
        }
        return left
    }

    private fun parseUnaryExpression(): AstNode {
        var cur: AstNode? = null
        if (match(TokenType.MINUS, TokenType.NOT)) {
            val operator = last()
            val exp = parseUnaryExpression()
            cur = AstNode.Unary(exp, operator)
        }
        return cur ?: parseGetExpression()
    }

    private fun parseGetExpression(): AstNode {
        var left = parseLiteralExpression()
        while (true) {
            if (match(TokenType.DOT)) {
                consumeOrError(TokenType.IDENTIFIER, "Expected indentifier after dot")
                left = AstNode.Get(last(), left)
            } else if (matchNSFB(TokenType.L_PAREN)) {
                left = parseFunctionCall(left)
            } else if (match(TokenType.D_PLUS)) {
                if (left !is AstNode.Get) {
                    artError(Errors.InvalidIncrementTargetError(left, srcCode))
                    throw ParserResyncException()
                }
                left = AstNode.VarIncrement(left, 1)
            } else if (match(TokenType.D_MINUS)) {
                if (left !is AstNode.Get) {
                    artError(Errors.InvalidIncrementTargetError(left, srcCode))
                    throw ParserResyncException()
                }
                left = AstNode.VarIncrement(left, -1)
            } else if (match(TokenType.L_BRACKET)) {
                val element = parseStatement()
                consumeOrError(TokenType.R_BRACKET, "Expected right bracket")
                if (match(TokenType.EQ)) {
                    return AstNode.ArrSet(left, element, parseStatement(), false)
                } else if (match(TokenType.WALRUS)) {
                    return AstNode.ArrSet(left, element, parseStatement(), true)
                } else {
                    left = AstNode.ArrGet(left, element)
                }
            }
            else break
        }
        return left
    }

    private fun parseLiteralExpression(): AstNode {
        if (match(TokenType.IDENTIFIER)) return AstNode.Get(last(), null)
        if (match(TokenType.L_PAREN)) return groupExpression()
        if (match(TokenType.L_BRACKET)) return parseArrayLiteral()

        if (match(
                TokenType.T_BYTE,
                TokenType.T_SHORT,
                TokenType.T_INT,
                TokenType.T_LONG,
                TokenType.T_FLOAT,
                TokenType.T_DOUBLE,
                TokenType.T_BOOLEAN,
                TokenType.T_STRING
        )) {
            val primitive = last()
            consumeOrError(TokenType.L_BRACKET, "Expected array initializer")
            val amount = parseStatement()
            consumeOrError(TokenType.R_BRACKET, "Expected closing bracket for array initializer")
            return AstNode.ArrayCreate(
                    when (primitive.tokenType) {
                        TokenType.T_BYTE -> Datatype.Byte()
                        TokenType.T_SHORT -> Datatype.Short()
                        TokenType.T_INT -> Datatype.Integer()
                        TokenType.T_LONG -> Datatype.Long()
                        TokenType.T_FLOAT -> Datatype.Float()
                        TokenType.T_DOUBLE -> Datatype.Double()
                        TokenType.T_BOOLEAN -> Datatype.Bool()
                        TokenType.T_STRING -> Datatype.Str()
                        else -> throw RuntimeException("unrechable")
                    }, amount)
        }

        if (!match(
                TokenType.BYTE,
                TokenType.SHORT,
                TokenType.INT,
                TokenType.LONG,
                TokenType.FLOAT,
                TokenType.DOUBLE,
                TokenType.STRING,
                TokenType.BOOLEAN
        )) {
            artError(Errors.SyntaxError(consume(), "Expected a statement, got ${last().lexeme}", srcCode))
            throw ParserResyncException()
        }

        return AstNode.Literal(last())
    }

    private fun parseArrayLiteral(): AstNode.ArrayLiteral {
        val startToken = last()
        val elements = mutableListOf<AstNode>()
        while (!match(TokenType.R_BRACKET)) {
            elements.add(parseStatement())
            if (!match(TokenType.COMMA)) {
                consumeOrError(TokenType.R_BRACKET, "expected closing bracket after array literal")
                break
            }
        }
        val endToken = last()
        return AstNode.ArrayLiteral(elements, startToken, endToken)
    }

    private fun parseFunctionCall(func: AstNode): AstNode {
        val args = mutableListOf<AstNode>()
        while (!match(TokenType.R_PAREN)) {
            args.add(parseStatement())
            if (!match(TokenType.COMMA)) {
                consumeOrError(TokenType.R_PAREN, "expected closing paren")
                break
            }
        }
        if (func !is AstNode.Get) throw RuntimeException("cant call ${func.accept(AstPrinter())} like a function")
        return AstNode.FunctionCall(func.from, func.name, args)
    }

    private fun groupExpression(): AstNode {
        val exp = parseStatement()
        if (!match(TokenType.R_PAREN)) throw RuntimeException("Expected closing Parenthesis")
        return AstNode.Group(exp)
    }

    private fun parseBlock(): AstNode.Block {
        val statements = mutableListOf<AstNode>()
        while (!match(TokenType.R_BRACE)) {
            try {
                statements.add(parseStatement())
                consumeExpectingSoftBreakOrError("Expected line break or semicolon")
            } catch (e: ParserResyncException) {
                resync()
                continue
            }
        }
        return AstNode.Block(statements.toTypedArray())
    }

    private fun resync() {
        while (!matchNSFB(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) consume()
    }

    private fun parsePrint(): AstNode {
        val printToken = last()
        val exp = parseStatement()
        return AstNode.Print(exp, printToken)
    }

    private fun parseType(): AstNode.DatatypeNode {
        val primitives = arrayOf(
            TokenType.T_BYTE,
            TokenType.T_SHORT,
            TokenType.T_INT,
            TokenType.T_LONG,
            TokenType.T_FLOAT,
            TokenType.T_LONG,
            TokenType.T_BOOLEAN,
            TokenType.T_STRING
        )
        if (match(*primitives)) {
            return AstNode.PrimitiveTypeNode(tokenTypeToDataKind(last().tokenType)).apply {
                if (matchNSFB(TokenType.L_BRACKET)) {
                    consumeOrError(TokenType.R_BRACKET, "expected closing bracket")
                    isArray = true
                }
            }
        }
        consumeOrError(TokenType.IDENTIFIER, "Expected Type")
        val token = last()
        if (matchNSFB(TokenType.L_BRACKET)) {
            consumeOrError(TokenType.R_BRACKET, "expected closing bracket")
            return AstNode.ObjectTypeNode(token).apply { isArray = true }
        }
        return AstNode.ObjectTypeNode(last())
    }

    private fun tokenTypeToDataKind(type: TokenType) = when (type) {
        TokenType.T_BYTE -> Datakind.BYTE
        TokenType.T_SHORT -> Datakind.SHORT
        TokenType.T_INT -> Datakind.INT
        TokenType.T_LONG -> Datakind.LONG
        TokenType.T_FLOAT -> Datakind.FLOAT
        TokenType.T_BOOLEAN -> Datakind.BOOLEAN
        TokenType.T_STRING -> Datakind.STRING
        else -> throw RuntimeException("invalid type")
    }

    private fun match(vararg types: TokenType): Boolean {
        val start = cur
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        for (type in types) if (tokens[cur].tokenType == type) {
            cur++
            return true
        }
        cur = start
        return false
    }

    private fun matchIdent(vararg identifiers: String): Boolean {
        val start = cur
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        for (ident in identifiers) {
            if (tokens[cur].tokenType == TokenType.IDENTIFIER && tokens[cur].lexeme == ident) {
                cur++
                return true
            }
        }
        cur = start
        return false
    }

    //match no soft break; TODO: come up with better name
    private fun matchNSFB(vararg types: TokenType): Boolean {
        for (type in types) if (tokens[cur].tokenType == type) {
            cur++
            return true
        }
        return false
    }

    private fun consumeSoftBreaks() {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
    }

    private fun consume(): Token {
        consumeSoftBreaks()
        cur++
        return last()
    }

    private fun consumeExpectingSoftBreakOrError(message: String) {
        if (tokens[cur].tokenType !in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
        while (tokens[cur].tokenType in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) cur++
    }

    private fun consumeOrError(type: TokenType, message: String) {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        if (tokens[cur].tokenType == type) cur++
        else {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
    }

    private fun consumeIdentOrError(identifier: String, message: String) {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        if (tokens[cur].tokenType == TokenType.IDENTIFIER && tokens[cur].lexeme == identifier) cur++
        else {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
    }

    private fun last(): Token {
        return tokens[cur - 1]
    }

    private fun peek(): Token {
        return tokens[cur]
//        return if (cur + 1 < tokens.size) tokens[cur + 1] else null
    }

    private fun peekNext(): Token? {
        val start = cur
        consumeSoftBreaks()
        cur++
        consumeSoftBreaks()
        val ret = tokens[cur]
        cur = start
        return ret
//        return if (cur + 1 < tokens.size) tokens[cur + 1] else null
    }

    class ParserResyncException : RuntimeException()

}
