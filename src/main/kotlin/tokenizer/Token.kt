package tokenizer

data class Token(val tokenType: TokenType, val lexeme: String, val literal: Any?, val file: String, val pos: Int) {

    override fun toString(): String {
        return "$tokenType(lexeme='$lexeme', literal='$literal' @ '$file':$pos)"
    }

}

enum class TokenType {
    INT, FLOAT, STRING, IDENTIFIER,
    K_FN, K_PRINT,
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, SEMICOLON,
    PLUS, MINUS, D_PLUS, D_MINUS, STAR, SLASH,
    EQ, GT, LT, GT_EU, LT_EU, D_EQ, NOT_EQ, NOT,
    PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ
}
