package parser

import ast.Expression
import ast.Statement
import tokenizer.Token
import tokenizer.TokenType
import java.lang.RuntimeException

object Parser {

    private var cur: Int = 0
    private var tokens: List<Token> = listOf()

    fun parse(tokens: List<Token>): Statement.Program {
        cur = 0
        this.tokens = tokens

        val functions = mutableListOf<Statement.Function>()
        val classes = mutableListOf<Statement.ArtClass>()
        while (!match(TokenType.EOF)) {
            if (match(TokenType.K_FN)) {
                functions.add(parseFunc(listOf()))
                continue
            }
            if (match(TokenType.K_CLASS)) {
                classes.add(parseClass(listOf()))
                continue
            }
            throw RuntimeException("Expected function or class in global scope")
        }
        return Statement.Program(functions.toTypedArray(), classes.toTypedArray())
    }

    private fun parseFunc(modifiers: List<Token>): Statement.Function {
        consumeOrError(TokenType.IDENTIFIER, "Expected function name")
        val funcName = last()
        consumeOrError(TokenType.L_PAREN, "Expected () after function name")

        if (funcName.lexeme == "main" && modifiers.isNotEmpty())
            throw RuntimeException("function main must not have access modifiers")

        val args = mutableListOf<Pair<Token, Token>>()

        while (match(TokenType.IDENTIFIER)) {
            val name = last()
            consumeOrError(TokenType.COLON, "Expected type-declaration after argument")
            val type = parseType()
            args.add(Pair(name, type))
            if (!match(TokenType.COMMA)) break
        }

        consumeOrError(TokenType.R_PAREN, "Expected () after function name")

        var returnType: Token? = null
        if (match(TokenType.COLON)) returnType = parseType()

        consumeOrError(TokenType.L_BRACE, "Expected code block after function declaration")

        val function = Statement.Function(parseBlock(), funcName, modifiers)
        function.argTokens = args
        function.returnTypeToken = returnType

        return function
    }

    private fun parseClass(modifier: List<Token>): Statement.ArtClass {
        consumeOrError(TokenType.IDENTIFIER, "Expected class name")
        val name = last()
        consumeOrError(TokenType.L_BRACE, "Expected opening brace after class definition")
        val funcs = mutableListOf<Statement.Function>()
        while (!match(TokenType.R_BRACE)) {
            val modifiers = parseModifiers()
            consumeOrError(TokenType.K_FN, "Expected ")
            funcs.add(parseFunc(modifiers))
        }
        return Statement.ArtClass(name, funcs.toTypedArray())
    }

    private fun parseModifiers(): List<Token> {
        val modifiers = mutableListOf<Token>()
        while (match(TokenType.K_PUBLIC, TokenType.K_PRIVATE, TokenType.K_STATIC, TokenType.K_ABSTRACT)) {
            modifiers.add(last())
        }
        return modifiers
    }

    private fun parseStatement(): Statement {
        if (match(TokenType.L_BRACE)) return parseBlock()
        if (match(TokenType.K_PRINT)) return parsePrint()
        if (match(TokenType.K_LET)) return parseVariableDeclaration()
        if (match(TokenType.K_LOOP)) return parseLoop()
        if (match(TokenType.K_IF)) return parseIf()
        if (match(TokenType.K_WHILE)) return parseWhileLoop()
        if (match(TokenType.K_RETURN)) return parseReturn()

        if (peekNext()?.tokenType in arrayOf(TokenType.D_PLUS, TokenType.D_MINUS)) return parseVarIncrement()

        if (peekNext()?.tokenType in
            arrayOf(TokenType.PLUS_EQ, TokenType.MINUS_EQ, TokenType.STAR_EQ, TokenType.SLASH_EQ)) {
            return parseVarAssignShorthand()
        }

        val start = cur
        try {
            return parseVariableAssignment() //TODO: make better, maybe peek()
        } catch (e: RuntimeException) {
            cur = start
        }

        return parseExpressionStatement()
    }

    private fun parseVarAssignShorthand(): Statement {
        consumeOrError(TokenType.IDENTIFIER, "Expected variable before shorthand operator")
        val variable = last()
        match(TokenType.PLUS_EQ, TokenType.MINUS_EQ, TokenType.STAR_EQ, TokenType.SLASH_EQ)
        val op = last()
        val num = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Expected semicolon after shorthand operator")
        when (op.tokenType) {
            TokenType.STAR_EQ -> return Statement.VariableAssignment(
                variable,
                Expression.Binary(
                    Expression.Variable(variable),
                    Token(TokenType.STAR, "*=", null, op.file, op.pos),
                    num
                )
            )
            TokenType.SLASH_EQ -> return Statement.VariableAssignment(
                variable,
                Expression.Binary(
                    Expression.Variable(variable),
                    Token(TokenType.SLASH, "/=", null, op.file, op.pos),
                    num
                )
            )
            TokenType.PLUS_EQ -> {
                if (num is Expression.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return Statement.VarIncrement(variable, num.literal.literal.toByte())
                }
                return Statement.VariableAssignment(
                    variable,
                    Expression.Binary(
                        Expression.Variable(variable),
                        Token(TokenType.PLUS, "+=", null, op.file, op.pos),
                        num
                    )
                )
            }
            TokenType.MINUS_EQ -> {
                if (num is Expression.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return Statement.VarIncrement(variable, (-num.literal.literal).toByte())
                }
                return Statement.VariableAssignment(
                    variable,
                    Expression.Binary(
                        Expression.Variable(variable),
                        Token(TokenType.MINUS, "-=", null, op.file, op.pos),
                        num
                    )
                )
            }
            else -> throw RuntimeException("unreachable")
        }
    }

    private fun parseVarIncrement(): Statement {
        consumeOrError(TokenType.IDENTIFIER, "expected variable before inc/dec operator")
        val toInc = last()
        match(TokenType.D_MINUS, TokenType.D_PLUS)
        val op = last()
        consumeOrError(TokenType.SEMICOLON, "Expected semicolon after inc/dec")
        return Statement.VarIncrement(toInc, if (op.tokenType == TokenType.D_PLUS) 1 else -1)
    }

    private fun parseReturn(): Statement.Return {
        if (match(TokenType.SEMICOLON)) return Statement.Return(null)
        val returnExpr = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Semicolon expected after return")
        return Statement.Return(returnExpr)
    }

    private fun parseWhileLoop(): Statement {
        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after while")
        val condition = parseExpression()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val body = parseStatement()
        if (body is Statement.VariableDeclaration) throw RuntimeException("Cant declare variable in while unless it " +
                "is wrapped in a block")

        return Statement.While(body, condition)
    }

    private fun parseIf(): Statement {

        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after if")
        val condition = parseExpression()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val ifStmt = parseStatement()
        if (ifStmt is Statement.VariableDeclaration) throw RuntimeException("Cant declare variable in if unless it " +
                "is wrapped in a block")

        if (!match(TokenType.K_ELSE)) return Statement.If(ifStmt, null, condition)

        val elseStmt = parseStatement()
        if (elseStmt is Statement.VariableDeclaration) throw RuntimeException("Cant declare variable in else unless it " +
                "is wrapped in a block")

        return Statement.If(ifStmt, elseStmt, condition)
    }

    private fun parseLoop(): Statement {
        val stmt = parseStatement()
        if (stmt is Statement.VariableDeclaration) throw RuntimeException("Cant declare variable in loop unless it " +
                "is wrapped in a block")
        return Statement.Loop(stmt)
    }

    private fun parseVariableAssignment(): Statement {
        consumeOrError(TokenType.IDENTIFIER, "")
        val name = last()
        consumeOrError(TokenType.EQ, "")
        val exp = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "")
        return Statement.VariableAssignment(name, exp)
    }

    private fun parseVariableDeclaration(): Statement {
        consumeOrError(TokenType.IDENTIFIER, "expected identifier after let")
        val name = last()
        var type: Token? = null
        if (match(TokenType.COLON)) type = parseType()
        consumeOrError(TokenType.EQ, "initializer expected")
        val initializer = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Expected a Semicolon after variable Declaration")
        val stmt = Statement.VariableDeclaration(name, initializer)
        stmt.typeToken = type
        return stmt
    }

    private fun parseExpressionStatement(): Statement {
        val exp = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Expected Semicolon")
        return Statement.ExpressionStatement(exp)
    }

    private fun parseExpression(): Expression {
        return parseWalrusAssignment()
    }

    private fun parseWalrusAssignment(): Expression {
        val left = parseBooleanComparison()
        if (!match(TokenType.WALRUS)) return left
        if (left !is Expression.Variable) throw RuntimeException("Expected Variable before :=")
        val right = parseExpression()
        return Expression.WalrusAssign(left.name, right)
    }

    private fun parseBooleanComparison(): Expression {
        var left = parseComparison()
        while (match(TokenType.D_AND, TokenType.D_OR)) { //TODO: fix priority
            val operator = last()
            val right = parseComparison()
            left = Expression.Binary(left, operator, right)
        }
        return left
    }

    private fun parseComparison(): Expression {
        var left = parseTermExpression()
        while (match(TokenType.D_EQ, TokenType.LT, TokenType.LT_EQ, TokenType.GT, TokenType.GT_EQ, TokenType.NOT_EQ)) {
            val operator = last()
            val right = parseTermExpression()
            left = Expression.Binary(left, operator, right)
        }
        return left
    }

    private fun parseTermExpression(): Expression {
        var left = parseFactorExpression()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = last()
            val right = parseFactorExpression()
            left = Expression.Binary(left, operator, right)
        }
        return left
    }

    private fun parseFactorExpression(): Expression {
        var left = parseUnaryExpression()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            val operator = last()
            val right = parseUnaryExpression()
            left = Expression.Binary(left, operator, right)
        }
        return left
    }

    private fun parseUnaryExpression(): Expression {
        var cur: Expression? = null
        if (match(TokenType.MINUS, TokenType.NOT)) {
            val operator = last()
            val exp = parseUnaryExpression()
            cur = Expression.Unary(exp, operator)
        }
        return cur ?: parseLiteralExpression()
    }

    private fun parseLiteralExpression(): Expression {
        if (match(TokenType.IDENTIFIER)){
            if (peek()?.tokenType == TokenType.L_PAREN) return parseFunctionCall()
            return Expression.Variable(last())
        }
        if (match(TokenType.L_PAREN)) return groupExpression()

        if (!match(TokenType.INT, TokenType.FLOAT, TokenType.STRING, TokenType.BOOLEAN)) {
            throw RuntimeException("Not a expression")
        }

        return Expression.Literal(last())
    }

    private fun parseFunctionCall(): Expression {
        val name = last()
        match(TokenType.L_PAREN)
        val args = mutableListOf<Expression>()
        while (!match(TokenType.R_PAREN)) {
            args.add(parseExpression())
            if (!match(TokenType.COMMA)) {
                consumeOrError(TokenType.R_PAREN, "expected closing paren")
                break
            }
        }
        return Expression.FunctionCall(name, args)
    }

    private fun groupExpression(): Expression {
        val exp = parseExpression()
        if (!match(TokenType.R_PAREN)) throw RuntimeException("Expected closing Parenthesis")
        return Expression.Group(exp)
    }

    private fun parseBlock(): Statement.Block {
        val statements = mutableListOf<Statement>()
        while (!match(TokenType.R_BRACE)) {
            statements.add(parseStatement())
        }
        return Statement.Block(statements.toTypedArray())
    }

    private fun parsePrint(): Statement {
        val exp = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Expected Semicolon after print")
        return Statement.Print(exp)
    }

    private fun parseType(): Token {
        if (!match(TokenType.T_INT, TokenType.T_STRING, TokenType.T_BOOLEAN)) {
            throw RuntimeException("Expected Datatype")
        }
        return last()
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) if (tokens[cur].tokenType == type) {
            cur++
            return true
        }
        return false
    }

    private fun consumeOrError(type: TokenType, message: String) {
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
        return if (cur + 1 < tokens.size) tokens[cur + 1] else null
    }

}
