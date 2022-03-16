package parser

import ast.AstNode
import ast.AstPrinter
import tokenizer.Token
import tokenizer.TokenType
import passes.TypeChecker.Datakind
import kotlin.RuntimeException

object Parser {

    private var cur: Int = 0
    private var tokens: List<Token> = listOf()

    fun parse(tokens: List<Token>): AstNode.Program {
        cur = 0
        this.tokens = tokens

        val functions = mutableListOf<AstNode.Function>()
        val classes = mutableListOf<AstNode.ArtClass>()
        val fields = mutableListOf<AstNode.FieldDeclaration>()
        while (!match(TokenType.EOF)) {
            if (match(TokenType.K_FN)) {
                functions.add(parseFunc(listOf(), true))
                continue
            }
            if (match(TokenType.K_CLASS)) {
                classes.add(parseClass(listOf()))
                continue
            }
            if (match(TokenType.K_LET, TokenType.K_CONST)) {
                fields.add(parseFieldDeclaration(last().tokenType == TokenType.K_CONST))
                continue
            }
            throw RuntimeException("Expected function or class in global scope")
        }
        return AstNode.Program(functions.toTypedArray(), classes.toTypedArray(), fields.toTypedArray())
    }

    private fun parseFieldDeclaration(isConst: Boolean): AstNode.FieldDeclaration {
        consumeOrError(TokenType.IDENTIFIER, "expected name")
        val name = last()
        consumeOrError(TokenType.COLON, "Field-definitions always require a explicit type")
        val type = parseType()
        consumeOrError(TokenType.EQ, "")
        val initializer = parseStatement()
        return AstNode.FieldDeclaration(name, type, initializer, isConst, mutableListOf())
    }

    private fun parseFunc(modifiers: List<Token>, isTopLevel: Boolean): AstNode.Function {
        consumeOrError(TokenType.IDENTIFIER, "Expected function name")
        val funcName = last()
        consumeOrError(TokenType.L_PAREN, "Expected () after function name")

        if (funcName.lexeme == "main" && modifiers.isNotEmpty())
            throw RuntimeException("function main must not have access modifiers")

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

        val function = AstNode.Function(parseBlock(), funcName, modifiers, isTopLevel)
        function.args = args
        function.returnType = returnType

        return function
    }

    private fun parseClass(modifier: List<Token>): AstNode.ArtClass {
        consumeOrError(TokenType.IDENTIFIER, "Expected class name")
        val name = last()
        consumeOrError(TokenType.L_BRACE, "Expected opening brace after class definition")
        val funcs = mutableListOf<AstNode.Function>()
        val staticFuncs = mutableListOf<AstNode.Function>()
        while (!match(TokenType.R_BRACE)) {
            val modifiers = parseModifiers()
            consumeOrError(TokenType.K_FN, "Expected function")
            val func = parseFunc(modifiers, false)
            if (func.isStatic) staticFuncs.add(func) else funcs.add(func)
        }
        return AstNode.ArtClass(name, staticFuncs.toTypedArray(), funcs.toTypedArray())
    }

    private fun parseModifiers(): List<Token> {
        val modifiers = mutableListOf<Token>()
        while (match(TokenType.K_PUBLIC, TokenType.K_PRIVATE, TokenType.K_STATIC, TokenType.K_ABSTRACT)) {
            modifiers.add(last())
        }
        return modifiers
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
        if (match(TokenType.K_BREAK)) return AstNode.Break()
        if (match(TokenType.K_CONTINUE)) return AstNode.Continue()

        if (peekNext()?.tokenType in arrayOf(TokenType.D_PLUS, TokenType.D_MINUS)) return parseVarIncrement()

        if (peekNext()?.tokenType in
            arrayOf(TokenType.PLUS_EQ, TokenType.MINUS_EQ, TokenType.STAR_EQ, TokenType.SLASH_EQ)) {
            return parseVarAssignShorthand()
        }

        return parseAssignment()
    }

    private fun parseVarAssignShorthand(): AstNode {
        consumeOrError(TokenType.IDENTIFIER, "Expected variable before shorthand operator")
        val variable = last()
        match(TokenType.PLUS_EQ, TokenType.MINUS_EQ, TokenType.STAR_EQ, TokenType.SLASH_EQ)
        val op = last()
        val num = parseStatement()
        when (op.tokenType) {
            TokenType.STAR_EQ -> return AstNode.Assignment(
                AstNode.Get(variable, null),
                AstNode.Binary(
                    AstNode.Get(variable, null),
                    Token(TokenType.STAR, "*=", null, op.file, op.pos),
                    num
                )
            )
            TokenType.SLASH_EQ -> return AstNode.Assignment(
                AstNode.Get(variable, null),
                AstNode.Binary(
                    AstNode.Get(variable, null),
                    Token(TokenType.SLASH, "/=", null, op.file, op.pos),
                    num
                )
            )
            TokenType.PLUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(AstNode.Get(variable, null), num.literal.literal.toByte())
                }
                return AstNode.Assignment(
                    AstNode.Get(variable, null),
                    AstNode.Binary(
                        AstNode.Get(variable, null),
                        Token(TokenType.PLUS, "+=", null, op.file, op.pos),
                        num
                    )
                )
            }
            TokenType.MINUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(AstNode.Get(variable, null), (-num.literal.literal).toByte())
                }
                return AstNode.Assignment(
                    AstNode.Get(variable, null),
                    AstNode.Binary(
                        AstNode.Variable(variable),
                        Token(TokenType.MINUS, "-=", null, op.file, op.pos),
                        num
                    )
                )
            }
            else -> throw RuntimeException("unreachable")
        }
    }

    private fun parseVarIncrement(): AstNode {
        consumeOrError(TokenType.IDENTIFIER, "expected variable before inc/dec operator")
        val toInc = last()
        match(TokenType.D_MINUS, TokenType.D_PLUS)
        val op = last()
        return AstNode.VarIncrement(AstNode.Get(toInc, null), if (op.tokenType == TokenType.D_PLUS) 1 else -1)
    }

    private fun parseReturn(): AstNode.Return {
        if (matchNSFB(TokenType.SOFT_BREAK)) return AstNode.Return(null)
        val returnExpr = parseStatement()
        return AstNode.Return(returnExpr)
    }

    private fun parseWhileLoop(): AstNode {
        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after while")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val body = parseStatement()
        if (body is AstNode.VariableDeclaration) throw RuntimeException("Cant declare variable in while unless it " +
                "is wrapped in a block")

        return AstNode.While(body, condition)
    }

    private fun parseIf(): AstNode {

        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after if")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val ifStmt = parseStatement()
        if (ifStmt is AstNode.VariableDeclaration) throw RuntimeException("Cant declare variable in if unless it " +
                "is wrapped in a block")

        if (!match(TokenType.K_ELSE)) return AstNode.If(ifStmt, null, condition)

        val elseStmt = parseStatement()
        if (elseStmt is AstNode.VariableDeclaration) throw RuntimeException("Cant declare variable in else unless it " +
                "is wrapped in a block")

        return AstNode.If(ifStmt, elseStmt, condition)
    }

    private fun parseLoop(): AstNode {
        val stmt = parseStatement()
        if (stmt is AstNode.VariableDeclaration) throw RuntimeException("Cant declare variable in loop unless it " +
                "is wrapped in a block")
        return AstNode.Loop(stmt)
    }

    private fun parseVariableDeclaration(isConst: Boolean): AstNode.VariableDeclaration {
        consumeOrError(TokenType.IDENTIFIER, "expected identifier after let")
        val name = last()
        var type: AstNode.DatatypeNode? = null
        if (match(TokenType.COLON)) type = parseType()
        consumeOrError(TokenType.EQ, "initializer expected")
        val initializer = parseStatement()
//        consumeOrError(TokenType.SEMICOLON, "Expected a Semicolon after variable Declaration")
//        consumeSoftBreaks()
        val stmt = AstNode.VariableDeclaration(name, initializer, isConst)
        stmt.explType = type
        return stmt
    }

    private fun parseAssignment(): AstNode {
        val left = parseBooleanComparison()
        if (match(TokenType.EQ)) {
            if (left is AstNode.Get) return AstNode.Assignment(AstNode.Get(left.name, null), parseStatement())
            else throw RuntimeException("expected variable before Assignment")
        }
        if (match(TokenType.WALRUS)) {
            if (left is AstNode.Get) return AstNode.WalrusAssign(AstNode.Get(left.name, null), parseStatement())
            else throw RuntimeException("expected variable before Assignment")
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
            } else if (matchNSFB(TokenType.L_PAREN)) left = parseFunctionCall(left)
            else break
        }
        return left
    }

    private fun parseLiteralExpression(): AstNode {
        if (match(TokenType.IDENTIFIER)) return AstNode.Get(last(), null)
        if (match(TokenType.L_PAREN)) return groupExpression()
        if (!match(TokenType.INT, TokenType.FLOAT, TokenType.STRING, TokenType.BOOLEAN)) {
            throw RuntimeException("Not a Statement")
        }

        return AstNode.Literal(last())
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
        return AstNode.FunctionCall(func, args)
    }

    private fun groupExpression(): AstNode {
        val exp = parseStatement()
        if (!match(TokenType.R_PAREN)) throw RuntimeException("Expected closing Parenthesis")
        return AstNode.Group(exp)
    }

    private fun parseBlock(): AstNode.Block {
        val statements = mutableListOf<AstNode>()
        while (!match(TokenType.R_BRACE)) {
            statements.add(parseStatement())
            consumeExpectingSoftBreakOrError("Expected line break or semicolon")
        }
        return AstNode.Block(statements.toTypedArray())
    }

    private fun parsePrint(): AstNode {
        val exp = parseStatement()
//        consumeOrError(TokenType.SEMICOLON, "Expected Semicolon after print")
//        consumeExpectingSoftBreakOrError("expected end of line")
        return AstNode.Print(exp)
    }

    private fun parseType(): AstNode.DatatypeNode {
        if (match(TokenType.T_INT, TokenType.T_STRING, TokenType.T_BOOLEAN)) {
            return AstNode.PrimitiveTypeNode(when (last().tokenType) {
                TokenType.T_INT -> Datakind.INT
                TokenType.T_STRING -> Datakind.STRING
                TokenType.T_BOOLEAN -> Datakind.BOOLEAN
                else -> throw RuntimeException("unreachable")
            })
        }
        consumeOrError(TokenType.IDENTIFIER, "Expected Type")
        return AstNode.ObjectTypeNode(last())
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

    private fun consumeExpectingSoftBreakOrError(message: String) {
        if (tokens[cur].tokenType !in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) throw RuntimeException(message)
        while (tokens[cur].tokenType in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) cur++
    }

    private fun consumeOrError(type: TokenType, message: String) {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        if (tokens[cur].tokenType == type) cur++
        else throw RuntimeException(message)
    }

    private fun last(): Token {
        return tokens[cur - 1]
    }

    private fun peek(): Token? {
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

}
