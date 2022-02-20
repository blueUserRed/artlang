package tokenizer

data class Token(val tokenType: TokenType, val lexeme: String, val literal: Any?, val file: String, val pos: Int)

enum class TokenType {
    INT, FLOAT, STRING,
    K_FN, K_PRINT,
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, SEMICOLON
}