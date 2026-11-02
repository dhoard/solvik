package dev.solvik.compiler.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.diagnostic.SourcePos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Token and TokenKind")
class TokenTest {

    private static final SourcePos POS = new SourcePos(0, 1, 1);

    @Test
    void tokens_without_a_value_use_the_of_factory() {
        Token token = Token.of(TokenKind.VAR, "var", POS);
        assertThat(token.kind()).isEqualTo(TokenKind.VAR);
        assertThat(token.lexeme()).isEqualTo("var");
        assertThat(token.value()).isNull();
    }

    @Test
    void string_tokens_carry_decoded_content() {
        Token token = new Token(TokenKind.STRING_LITERAL, "\"a\\n\"", "a\n", POS);
        assertThat(token.value()).isEqualTo("a\n");
    }

    @Test
    void is_compares_kinds() {
        assertThat(Token.of(TokenKind.EOF, "", POS).is(TokenKind.EOF)).isTrue();
        assertThat(Token.of(TokenKind.EOF, "", POS).is(TokenKind.VAR)).isFalse();
    }

    @Test
    void toString_shows_the_kind_and_lexeme() {
        assertThat(Token.of(TokenKind.INT_LITERAL, "42", POS)).hasToString("INT_LITERAL('42')");
        assertThat(Token.of(TokenKind.EOF, "", POS)).hasToString("EOF");
    }

    @Test
    void fixed_text_and_keyword_classification() {
        assertThat(TokenKind.VAR.fixedText()).isEqualTo("var");
        assertThat(TokenKind.ASSIGN.fixedText()).isEqualTo("=");
        assertThat(TokenKind.IDENTIFIER.fixedText()).isNull();

        assertThat(TokenKind.VAR.isKeyword()).isTrue();
        assertThat(TokenKind.TYPE.isKeyword()).isTrue();
        assertThat(TokenKind.NULL.isKeyword()).isTrue();
        assertThat(TokenKind.STATIC.isKeyword()).isTrue();
        assertThat(TokenKind.STATIC.fixedText()).isEqualTo("static");
        assertThat(TokenKind.IDENTIFIER.isKeyword()).isFalse();
        assertThat(TokenKind.ASSIGN.isKeyword()).isFalse();
        assertThat(TokenKind.EOF.isKeyword()).isFalse();
    }
}
