package tokenizer

data class Token(val tokenType: TokenType, val lexeme: String, val literal: Any?, val file: String, val pos: Int) {

    override fun toString(): String {
        return "$tokenType(lexeme='$lexeme', literal='$literal' @ '$file':$pos)"
//        return tokenType.toString()
//        return "$tokenType($lexeme)"
//        return tokenType.toString() + "(" + lexeme + ")"
    }

}

enum class TokenType {

    INT, FLOAT, STRING, IDENTIFIER, BOOLEAN,

    K_FN, K_PRINT, K_PRINTLN, K_CLASS, K_LET, K_CONST, K_PRIVATE, K_PUBLIC, K_ABSTRACT, K_STATIC,
    K_FOR, K_ELSE, K_LOOP, K_WHILE, K_IF,

    L_PAREN, R_PAREN, L_BRACE, R_BRACE, SEMICOLON,
    PLUS, MINUS, D_PLUS, D_MINUS, STAR, SLASH,
    EQ, GT, LT, GT_EQ, LT_EQ, D_EQ, NOT_EQ, NOT, D_AND, D_OR,
    PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ,

    EOF

}
