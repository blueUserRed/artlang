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
        while (!match(TokenType.EOF)) functions.add(parseFunc())
        return Statement.Program(functions.toTypedArray())
    }

    private fun parseFunc(): Statement.Function {
        consumeOrError(TokenType.K_FN, "Expected function")
        consumeOrError(TokenType.IDENTIFIER, "Expected function name")
        val funcName = last()
        consumeOrError(TokenType.L_PAREN, "Expected () after function name")
        consumeOrError(TokenType.R_PAREN, "Expected () after function name")
        consumeOrError(TokenType.L_BRACE, "Expected code block after function declaration")
        return Statement.Function(parseBlock(), funcName)
    }

    private fun parseStatement(): Statement {
        if (match(TokenType.L_BRACE)) return parseBlock()
        if (match(TokenType.K_PRINT)) return parsePrint()
        return parseExpressionStatement()
    }

    private fun parseExpressionStatement(): Statement {
        val exp = parseExpression()
        consumeOrError(TokenType.SEMICOLON, "Expected Semicolon")
        return Statement.ExpressionStatement(exp)
    }

    private fun parseExpression(): Expression {
        return parseTermExpression()
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
        var left = parsePrimaryExpression()
        while (match(TokenType.STAR, TokenType.SLASH)) {
            val operator = last()
            val right = parsePrimaryExpression()
            left = Expression.Binary(left, operator, right)
        }
        return left
    }

    private fun parsePrimaryExpression(): Expression {
        if (!match(TokenType.INT, TokenType.FLOAT, TokenType.STRING)) throw RuntimeException("Not a expression")
        return Expression.Literal(last())
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

}