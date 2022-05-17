package parser

import ast.AstNode
import ast.AstPrinter
import errors.artError
import tokenizer.Token
import tokenizer.TokenType
import Datakind
import kotlin.RuntimeException
import errors.Errors

/**
 * parses a list of tokens and constructs the ast
 */
class Parser {

    /**
     * the index of the current token
     */
    private var cur: Int = 0

    /**
     * the tokens that are being parsed
     */
    private var tokens: List<Token> = listOf()

    /**
     * the srcCode the tokens originated from
     */
    private var srcCode: String = ""

    /**
     * parses the [tokens] and constructs the ast
     * @param tokens the tokens
     * @param code the srcCode the tokens originated from
     */
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
        return AstNode.Program(functions, classes, fields, srcCode, listOf())
    }

    /**
     * attempts to resync the parser in the context of the top-level
     */
    private fun resyncTopLevel() {
        while (peek().tokenType !in arrayOf(TokenType.K_FN, TokenType.K_CLASS, TokenType.K_CONST, TokenType.EOF) &&
            !(peek().tokenType == TokenType.IDENTIFIER && peek().lexeme == "field")) cur++
    }

    /**
     * parses a field declaration
     * @param modifiers the modifiers that preceeded the field
     * @param isConst true if the field is const
     * @param isTopLevel true if the field was defined in the top level
     */
    private fun parseFieldDeclaration(modifiers: List<Token>, isConst: Boolean, isTopLevel: Boolean) : AstNode.Field {
        val fieldToken = last()
        consumeOrError(TokenType.IDENTIFIER, "expected name")
        val name = last()
        consumeOrError(TokenType.COLON, "Field-definitions always require a explicit type")
        val type = parseType()
        consumeOrError(TokenType.EQ, "")
        val initializer = parseStatement()
        return AstNode.FieldDeclaration(
            name,
            type,
            initializer,
            isConst,
            modifiers,
            isTopLevel,
            modifiers + listOf(fieldToken)
        )
    }

    /**
     * parses a function
     * @param modifiers the modifies that preceeded the function
     * @param isTopLevel true if the function was defined in the top level
     */
    private fun parseFunc(modifiers: List<Token>, isTopLevel: Boolean): AstNode.Function {
        val fnToken = last()
        consumeOrError(TokenType.IDENTIFIER, "Expected function name")
        val funcName = last()
        consumeOrError(TokenType.L_PAREN, "Expected () after function name")

        if (funcName.lexeme == "main" && modifiers.isNotEmpty()) {
            artError(Errors.InvalidMainFunctionDeclarationError("Main function must not have modifiers",
                srcCode, modifiers))
        }

        val args = mutableListOf<Pair<Token, AstNode.DatatypeNode>>()

        while (match(TokenType.IDENTIFIER)) {
            val name = last()
            if (name.lexeme == "this") syntaxError("'this' cannot be used as a parameter-name", name)
            consumeOrError(TokenType.COLON, "Expected type-declaration after argument")
            val type = parseType()
            args.add(Pair(name, type))
            if (!match(TokenType.COMMA)) break
        }

        consumeOrError(TokenType.R_PAREN, "Expected () after function name")
        val rParenToken = last()

        var returnType: AstNode.DatatypeNode? = null
        if (match(TokenType.COLON)) returnType = parseType()

        consumeOrError(TokenType.L_BRACE, "Expected code block after function declaration")

        val function = AstNode.FunctionDeclaration(
            parseBlock(),
            funcName,
            if (funcName.lexeme == "main") listOf() else modifiers,
            isTopLevel,
            modifiers + listOf(fnToken, rParenToken)
        )

        function.args = args
        function.returnType = returnType

        return function
    }

    /**
     * parses a class
     * @param classModifiers the modifiers that preceeded the class
     */
    private fun parseClass(classModifiers: List<Token>): AstNode.ArtClass {
        val classToken = last()
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

            if (peek().tokenType == TokenType.EOF) {
                artError(Errors.SyntaxError(peek(), "Expected closing brace, reached end of file instead", srcCode))
                break
            }

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
                consumeIdentOrError("field", "Expected field declaration after const")
                val field = parseFieldDeclaration(modifiers, true, false)
                if (field.isStatic) staticFields.add(field) else fields.add(field)
                continue
            }
            artError(Errors.SyntaxError(peek(), "Expected a function or field declaration in class", srcCode))
//            resyncClass()
        } catch (e: ParserResyncException) {
            resyncClass()
            continue
        }

        val rBraceToken = last()

        return AstNode.ClassDefinition(
            name,
            staticFuncs,
            funcs,
            staticFields,
            fields,
            extends,
            classModifiers + listOf(classToken, rBraceToken)
        )
    }

    /**
     * attempts to resync the parser in the context of a class
     */
    private fun resyncClass() {
        while (
            peek().tokenType !in arrayOf(TokenType.K_FN, TokenType.K_CONST, TokenType.EOF) &&
            !(peek().tokenType == TokenType.IDENTIFIER && peek().lexeme == "field") &&
            peek().tokenType != TokenType.R_BRACE
        ) {
            cur++
        }
    }

    /**
     * parses a list of modifiers
     */
    private fun parseModifiers(): List<Token> {
        val modifiers = mutableListOf<Token>()
        while (matchIdent("public", "abstract", "static", "override")) modifiers.add(last())
        return modifiers
    }

    /**
     * validates that the [modifiers] can be used in front of a function and that each modifier is only present once
     */
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
                "override" -> had.add("override")
                else -> throw RuntimeException("unknown modifier ${modifier.lexeme}")
            }
        }
    }

    /**
     * validates that the [modifiers] can be used in front of a field and that each modifier is only present once
     */
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

    /**
     * validates that the [modifiers] can be used in front of a class and that each modifier is only present once
     */
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

    /**
     * parses a statement
     */
    private fun parseStatement(): AstNode {
        if (match(TokenType.K_PRINT)) return parsePrint()
        if (match(TokenType.K_LET)) return parseVariableDeclaration(false)
        if (match(TokenType.K_CONST)) return parseVariableDeclaration(true)
        if (match(TokenType.K_LOOP)) return parseLoop()
        if (match(TokenType.K_WHILE)) return parseWhileLoop()
        if (match(TokenType.K_RETURN)) return parseReturn()
        if (match(TokenType.K_BREAK)) return AstNode.Break(last(), listOf(last()))
        if (match(TokenType.K_CONTINUE)) return AstNode.Continue(last(), listOf(last()))

        return parseAssignment()
    }

    /**
     * parses the shorthand for operations and assignments (`+=`, `-=`, etc.)
     * @param variable the statement where the shorthand is applied
     * @param op the operation
     * @param num the right side of the statement
     */
    private fun parseVarAssignShorthand(variable: AstNode.Get, op: Token, num: AstNode): AstNode {
        when (op.tokenType) {
            TokenType.STAR_EQ, TokenType.SLASH_EQ -> return AstNode.VarAssignShorthand(
                variable.from,
                variable.name,
                op,
                num,
                variable.relevantTokens
            )
            TokenType.PLUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(variable, num.literal.literal.toByte(), variable.relevantTokens)
                }
                return AstNode.VarAssignShorthand(
                    variable.from,
                    variable.name,
                    op,
                    num,
                    variable.relevantTokens
                )
            }
            TokenType.MINUS_EQ -> {
                if (num is AstNode.Literal && num.literal.literal is Int && num.literal.literal in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    return AstNode.VarIncrement(variable, (-num.literal.literal).toByte(),  variable.relevantTokens)
                }
                return AstNode.VarAssignShorthand(
                    variable.from,
                    variable.name,
                    op,
                    num,
                    variable.relevantTokens
                )
            }
            else -> throw RuntimeException("unreachable")
        }
    }

    /**
     * parses a return statement
     */
    private fun parseReturn(): AstNode.Return {
        val returnToken = last()
        if (matchNSFB(TokenType.SOFT_BREAK)) return AstNode.Return(null, returnToken, listOf(returnToken))
        val returnExpr = parseStatement()
        return AstNode.Return(returnExpr, returnToken, listOf(returnToken))
    }

    /**
     * parses a while loop
     */
    private fun parseWhileLoop(): AstNode {
        val whileToken = last()
        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after while")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val body = parseStatement()
        if (body is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(body, srcCode))
        }
        if (body is AstNode.YieldArrow) {
            artError(Errors.SyntaxError(
                body.relevantTokens[0],
                "Yield arrow can only be declared at the bottom of a block",
                srcCode
            ))
        }

        return AstNode.While(body, condition, listOf(whileToken))
    }

    /**
     * parses an if statement
     */
    private fun parseIf(): AstNode {
        val ifToken = last()

        consumeOrError(TokenType.L_PAREN, "Expected Parenthesis after if")
        val condition = parseStatement()
        consumeOrError(TokenType.R_PAREN, "Expected closing Parenthesis after condition")

        val ifStmt = parseStatement()
        if (ifStmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(ifStmt, srcCode))
        }
        if (ifStmt is AstNode.YieldArrow) {
            artError(Errors.SyntaxError(
                ifStmt.relevantTokens[0],
                "Yield arrow can only be declared at the bottom of a block",
                srcCode
            ))
        }

        if (!match(TokenType.K_ELSE)) return AstNode.If(ifStmt, null, condition, listOf(ifToken))

        val elseStmt = parseStatement()
        if (elseStmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(elseStmt, srcCode))
        }
        if (ifStmt is AstNode.YieldArrow) {
            artError(Errors.SyntaxError(
                elseStmt.relevantTokens[0],
                "Yield arrow can only be declared at the bottom of a block",
                srcCode
            ))
        }

        return AstNode.If(ifStmt, elseStmt, condition, listOf(ifToken))
    }

    /**
     * parses a loop statement
     */
    private fun parseLoop(): AstNode {
        val loopToken = last()
        val stmt = parseStatement()
        if (stmt is AstNode.VariableDeclaration) {
            artError(Errors.VarDeclarationWithoutBlockError(stmt, srcCode))
        }
        if (stmt is AstNode.YieldArrow) {
            artError(Errors.SyntaxError(
                stmt.relevantTokens[0],
                "Yield arrow can only be declared at the bottom of a block",
                srcCode
            ))
        }
        return AstNode.Loop(stmt, listOf(loopToken))
    }

    /**
     * parses a variable declaration
     * @param isConst true if the variable was declared using the const keyword
     */
    private fun parseVariableDeclaration(isConst: Boolean): AstNode.VariableDeclaration {
        val decToken = last()
        consumeOrError(TokenType.IDENTIFIER, "expected identifier after let/const")
        val name = last()
        if (name.lexeme == "this") syntaxError("'this' cannot be used a variable name", name)
        var type: AstNode.DatatypeNode? = null
        if (match(TokenType.COLON)) type = parseType()
        consumeOrError(TokenType.EQ, "initializer expected")
        val initializer = parseStatement()
        val stmt = AstNode.VariableDeclaration(name, initializer, isConst, decToken, listOf(decToken))
        stmt.explType = type
        return stmt
    }

    /**
     * parses an assignment
     */
    private fun parseAssignment(): AstNode {
        val left = parseOr()
        if (match(TokenType.EQ)) {
            val compToken = last()
            if (left is AstNode.Get) return AstNode.Assignment(left.from, left.name, parseStatement(), false, listOf(compToken))
            else {
                artError(Errors.InvalidAssignmentTargetError(left, srcCode))
                throw ParserResyncException()
            }
        }
        if (match(TokenType.WALRUS)) {
            val compToken = last()
            if (left is AstNode.Get) return AstNode.Assignment(left.from, left.name, parseStatement(), true, listOf(compToken))
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

    /**
     * parses `||`
     */
    private fun parseOr(): AstNode {
        var left = parseAnd()
        while (match(TokenType.D_OR)) {
            val operator = last()
            val right = parseComparison()
            left = AstNode.Binary(left, operator, right, listOf(operator))
        }
        return left
    }

    /**
     * parses `&&`
     */
    private fun parseAnd(): AstNode {
        var left = parseComparison()
        while (match(TokenType.D_AND)) {
            val operator = last()
            val right = parseComparison()
            left = AstNode.Binary(left, operator, right, listOf(operator))
        }
        return left
    }

    /**
     * parses number comparisons (==, >, >=, etc.)
     */
    private fun parseComparison(): AstNode {
        var left = parseTermExpression()
        while (match(TokenType.D_EQ, TokenType.LT, TokenType.LT_EQ, TokenType.GT, TokenType.GT_EQ, TokenType.NOT_EQ)) {
            val operator = last()
            val right = parseTermExpression()
            left = AstNode.Binary(left, operator, right, listOf(operator))
        }
        return left
    }

    /**
     * parses additions and subractions
     */
    private fun parseTermExpression(): AstNode {
        var left = parseFactorExpression()
        while (matchNSFB(TokenType.PLUS, TokenType.MINUS)) {
            val operator = last()
            val right = parseFactorExpression()
            left = AstNode.Binary(left, operator, right, listOf(operator))
        }
        return left
    }

    /**
     * parses multiplications, divisions and modulo(s)
     */
    private fun parseFactorExpression(): AstNode {
        var left = parseUnaryExpression()
        while (matchNSFB(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            val operator = last()
            val right = parseUnaryExpression()
            left = AstNode.Binary(left, operator, right, listOf(operator))
        }
        return left
    }

    /**
     * parses unary minus and unary not
     */
    private fun parseUnaryExpression(): AstNode {
        var cur: AstNode? = null
        if (match(TokenType.MINUS, TokenType.NOT)) {
            val operator = last()
            val exp = parseUnaryExpression()
            cur = AstNode.Unary(exp, operator, listOf(operator))
        }
        return cur ?: parseGetExpression()
    }

    /**
     * parses get-expressions, array-gets and array-sets
     */
    private fun parseGetExpression(): AstNode {
        var left = parseLiteralExpression()
        while (true) {
            if (match(TokenType.DOT)) {
                val dotToken = last()
                if (match(
                        TokenType.T_BYTE,
                        TokenType.T_SHORT,
                        TokenType.T_INT,
                        TokenType.T_LONG,
                        TokenType.T_FLOAT,
                        TokenType.T_DOUBLE
                )) {
                    left = AstNode.TypeConvert(left, last(), listOf(last(), dotToken))
                } else {
                    consumeOrError(TokenType.IDENTIFIER, "Expected indentifier after dot")
                    left = AstNode.Get(last(), left, listOf(last(), dotToken))
                }
            } else if (matchNSFB(TokenType.L_PAREN)) {
                left = parseFunctionCall(left)
            } else if (match(TokenType.D_PLUS)) {
                if (left !is AstNode.Get) {
                    artError(Errors.InvalidIncrementTargetError(left, srcCode))
                    throw ParserResyncException()
                }
                left = AstNode.VarIncrement(left, 1, listOf(last()))
            } else if (match(TokenType.D_MINUS)) {
                if (left !is AstNode.Get) {
                    artError(Errors.InvalidIncrementTargetError(left, srcCode))
                    throw ParserResyncException()
                }
                left = AstNode.VarIncrement(left, -1, listOf(last()))
            } else if (match(TokenType.L_BRACKET)) {
                val element = parseStatement()
                consumeOrError(TokenType.R_BRACKET, "Expected right bracket")
                if (match(TokenType.EQ)) {
                    val assignToken = last()
                    return AstNode.ArrSet(left, element, parseStatement(), false, listOf(assignToken))
                } else if (match(TokenType.WALRUS)) {
                    val assignToken = last()
                    return AstNode.ArrSet(left, element, parseStatement(), true, listOf(assignToken))
                } else {
                    left = AstNode.ArrGet(left, element, listOf(last()))
                }
            }
            else break
        }
        return left
    }

    /**
     * parses a literal expression (e.g. numbers, strings, variables, groups, etc.)
     */
    private fun parseLiteralExpression(): AstNode {
        if (match(TokenType.L_BRACE)) return parseBlock()
        if (match(TokenType.K_IF)) return parseIf()
        if (match(TokenType.IDENTIFIER)) return AstNode.Get(last(), null, listOf(last()))
        if (match(TokenType.K_NULL)) return AstNode.Null(listOf(last()))
        if (match(TokenType.L_PAREN)) return groupExpression()
        if (match(TokenType.L_BRACKET)) return parseArrayLiteral()
        if (match(TokenType.YIELD_ARROW)) {
            val yieldArrow = last()
            return AstNode.YieldArrow(parseStatement(), listOf(yieldArrow))
        }

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
            val rBracket = last()
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
                        else -> throw RuntimeException("unreachable")
                    }, arrayOf(amount), listOf(primitive, rBracket)
            )
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
            artError(Errors.SyntaxError(consume(), "Expected a statement, got '${peek().lexeme}'", srcCode))
            throw ParserResyncException()
        }

        return AstNode.Literal(last(), listOf(last()))
    }

    /**
     * parses an array literal (`let x = [1, 2, 3, 4]`)
     */
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
        return AstNode.ArrayLiteral(elements, listOf(startToken, endToken))
    }

    /**
     * parses a function call
     */
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
        return AstNode.FunctionCall(func.from, func.name, args, listOf(last()))
    }

    /**
     * parse a group expression (`3 * (1 + 2)`)
     */
    private fun groupExpression(): AstNode {
        val firstToken = last()
        val exp = parseStatement()
        if (!match(TokenType.R_PAREN)) throw RuntimeException("Expected closing Parenthesis")
        return AstNode.Group(exp, listOf(firstToken, last()))
    }

    /**
     * parses a code-block
     */
    private fun parseBlock(): AstNode.Block {
        val lBrace = last()
        val statements = mutableListOf<AstNode>()
        while (!match(TokenType.R_BRACE)) {
            if (match(TokenType.EOF)) {
                syntaxError("Expected closing brace, reached end of file instead", peek())
                break
            }
            try {
                val statement = parseStatement()
                statements.add(statement)
                if (peek().tokenType != TokenType.R_BRACE) consumeExpectingSoftBreakOrError("Expected line break or semicolon")
                if (statement is AstNode.YieldArrow) {
                    consumeOrError(TokenType.R_BRACE, "the yield-arrow has to be the last statement in a block")
                    return AstNode.Block(statements.toTypedArray(), listOf(lBrace, last()))
                }
            } catch (e: ParserResyncException) {
                resync()
                continue
            }
        }
        return AstNode.Block(statements.toTypedArray(), listOf(lBrace, last()))
    }

    /**
     * attempts to resync the parser in the context of a block
     */
    private fun resync() {
        while (!matchNSFB(TokenType.SOFT_BREAK, TokenType.SEMICOLON, TokenType.EOF)) consume()
    }

    /**
     * parses a print statement
     */
    private fun parsePrint(): AstNode {
        val printToken = last()
        val exp = parseStatement()
        return AstNode.Print(exp, printToken, listOf(printToken))
    }

    /**
     * parses a type
     */
    private fun parseType(): AstNode.DatatypeNode {
        val primitives = arrayOf(
            TokenType.T_BYTE,
            TokenType.T_SHORT,
            TokenType.T_INT,
            TokenType.T_LONG,
            TokenType.T_FLOAT,
            TokenType.T_LONG,
            TokenType.T_DOUBLE,
            TokenType.T_BOOLEAN
        )

        val typeNode: AstNode.DatatypeNode

        if (match(*primitives)) typeNode = AstNode.PrimitiveTypeNode(tokenTypeToDataKind(last().tokenType))
        else if (match(TokenType.T_STRING)) {
            typeNode = AstNode.ObjectTypeNode(
                Token(TokenType.IDENTIFIER, "\$String", "\$String", "", -1 ,-1),
            )
        } else {
            consumeOrError(TokenType.IDENTIFIER, "Expected Type")
            typeNode = AstNode.ObjectTypeNode(last())
        }

        var arrDims = 0
        while (matchNSFB(TokenType.L_BRACKET)) {
            consumeOrError(TokenType.R_BRACKET, "Expected closing Bracket")
            arrDims++
        }
        typeNode.arrayDims = arrDims
        return typeNode
    }

    /**
     * translates from a tokenType to the corresponding data type
     */
    private fun tokenTypeToDataKind(type: TokenType) = when (type) {
        TokenType.T_BYTE -> Datakind.BYTE
        TokenType.T_SHORT -> Datakind.SHORT
        TokenType.T_INT -> Datakind.INT
        TokenType.T_LONG -> Datakind.LONG
        TokenType.T_DOUBLE -> Datakind.DOUBLE
        TokenType.T_FLOAT -> Datakind.FLOAT
        TokenType.T_BOOLEAN -> Datakind.BOOLEAN
        TokenType.T_STRING -> Datakind.OBJECT
        else -> throw RuntimeException("invalid type")
    }

    /**
     * checks whether the type of the current token matches any of the types in [types] and consumes the current token
     * it is the case
     *
     * ignores soft breaks before the token
     * @return true if the types matched
     */
    private fun match(vararg types: TokenType): Boolean {
        val start = cur
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        for (type in types) if (tokens[cur].tokenType == type) {
            if (type != TokenType.EOF) cur++
            return true
        }
        cur = start
        return false
    }

    /**
     * like [match], but instead of matching the type it matches the lexeme of an identifier token
     */
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

    /**
     * match no soft break; TODO: come up with better name
     *
     * like [match], but doesn't ignore soft breaks
     */
    private fun matchNSFB(vararg types: TokenType): Boolean {
        for (type in types) if (tokens[cur].tokenType == type) {
            if (type != TokenType.EOF) cur++
            return true
        }
        return false
    }

    /**
     * consumes all soft breaks
     */
    private fun consumeSoftBreaks() {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
    }

    /**
     * consumes the current token
     */
    private fun consume(): Token {
        consumeSoftBreaks()
        if (peek().tokenType == TokenType.EOF) return peek()
        cur++
        return last()
    }

    /**
     * consumes all soft breaks, but produces a syntax error if no soft break is encountered
     * @param message the message of the syntax error
     */
    private fun consumeExpectingSoftBreakOrError(message: String) {
        if (tokens[cur].tokenType !in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
        while (tokens[cur].tokenType in arrayOf(TokenType.SOFT_BREAK, TokenType.SEMICOLON)) cur++
    }

    /**
     * tries to consume a token of type [type] and produces a syntax error with message [message] if the type does not
     * match
     */
    private fun consumeOrError(type: TokenType, message: String) {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        if (tokens[cur].tokenType == type) cur++
        else {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
    }

    /**
     * like [consumeOrError], but instead of matching the tokenType it matches the lexeme of an identifier token
     */
    private fun consumeIdentOrError(identifier: String, message: String) {
        while (tokens[cur].tokenType == TokenType.SOFT_BREAK) cur++
        if (tokens[cur].tokenType == TokenType.IDENTIFIER && tokens[cur].lexeme == identifier) cur++
        else {
            artError(Errors.SyntaxError(consume(), message, srcCode))
            throw ParserResyncException()
        }
    }

    private fun syntaxError(message: String, token: Token) {
        artError(Errors.SyntaxError(
            token,
            message,
            srcCode
        ))
    }

    /**
     * returns the last token
     */
    private fun last(): Token {
        return tokens[cur - 1]
    }

    /**
     * returns the current token without consuming it
     */
    private fun peek(): Token {
        return tokens[cur]
    }

    /**
     * thrown when a syntax error is encountered, causes the parser to resync
     */
    class ParserResyncException : RuntimeException()

}
