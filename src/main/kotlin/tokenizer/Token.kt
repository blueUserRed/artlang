package tokenizer

data class Token(val tokenType: TokenType, val lexeme: String, val literal: Any?, val file: String, val pos: Int) {

    override fun toString(): String {
        return "$tokenType(lexeme='$lexeme', literal='$literal' @ '$file':$pos)"
    }

}

enum class TokenType {
    INT, FLOAT, STRING, IDENTIFIER,
    K_FN, K_PRINT,
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, SEMICOLON
}