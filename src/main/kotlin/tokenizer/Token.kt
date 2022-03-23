package tokenizer

/**
 * represents a token
 * @param tokenType the type of token
 * @param lexeme the text the token was generated from
 * @param literal if the token is a literal (e.g an int (1, 2) or a string ("hello", "world")) it contains the value
 * of the token, else null
 * @param file the file the token originates from
 * @param pos the position of the first character of the lexeme in the line
 * @param line the line of the first character of the lexeme
 */
data class Token(
    val tokenType: TokenType,
    val lexeme: String,
    val literal: Any?,
    val file: String,
    val pos: Int,
    val line: Int
) {

    override fun toString(): String {
        return "$tokenType(lexeme='${
            if (lexeme == "\n") "\\n" else lexeme
        }', literal='$literal' @ '$file':$pos)"
    }

}

/**
 * contains all the different type of tokens
 */
enum class TokenType {

    //literals
    INT, FLOAT, STRING, IDENTIFIER, BOOLEAN,

    //keywords
    K_FN, K_PRINT, K_CLASS, K_LET, K_CONST,
    K_FOR, K_ELSE, K_LOOP, K_WHILE, K_IF, K_RETURN, K_BREAK, K_CONTINUE,

    //parentheses
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET,

    //characters
    SEMICOLON, COLON, COMMA, DOT,

    //operators
    PLUS, MINUS, D_PLUS, D_MINUS, STAR, SLASH, MOD,
    EQ, GT, LT, GT_EQ, LT_EQ, D_EQ, NOT_EQ, NOT, D_AND, D_OR,
    PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, WALRUS,

    SOFT_BREAK, //line break

    T_INT, T_STRING, T_BOOLEAN, //types

    EOF

}
