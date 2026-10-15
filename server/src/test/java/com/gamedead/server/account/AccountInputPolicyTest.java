package com.gamedead.server.account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class AccountInputPolicyTest {
    private final AccountInputPolicy policy = new AccountInputPolicy();
    @Test void normalizesLoginWithoutDependingOnDefaultLocale() {
        assertThat(policy.normalizeLogin("  MaGe_1 ")).isEqualTo("mage_1");
    }
    @ParameterizedTest @ValueSource(strings={"ab","aaaaaaaaaaaaaaaaaaaaaaaaa","русский","a-b","", "a\nb"})
    void rejectsInvalidLogin(String login) {
        assertThatThrownBy(() -> policy.normalizeLogin(login)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsLoginBoundaries() {
        assertThat(policy.normalizeLogin("abc")).isEqualTo("abc");
        assertThat(policy.normalizeLogin("a".repeat(24))).hasSize(24);
    }
    @Test void countsNameInCodePointsAndSupportsCyrillic() {
        assertThat(policy.normalizeDisplayName("  Маг 🔥  ")).isEqualTo("Маг 🔥");
        assertThat(policy.normalizeDisplayName("🔥".repeat(24))).hasSize(48);
        assertThatThrownBy(() -> policy.normalizeDisplayName("🔥".repeat(25))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"ab","a\nb","a\u0000b","a\tb"})
    void rejectsShortOrControlNames(String name) {
        assertThatThrownBy(() -> policy.normalizeDisplayName(name)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsNullInputs() {
        assertThatThrownBy(() -> policy.normalizeLogin(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.normalizeDisplayName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validatePassword(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void passwordBoundariesUseUtf8BytesWithoutTrimming() {
        assertThatCode(() -> policy.validatePassword(" ".repeat(8))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validatePassword("я".repeat(36))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validatePassword("🔥".repeat(18))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validatePassword("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validatePassword("я".repeat(36)+"x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validatePassword("🔥".repeat(19))).isInstanceOf(IllegalArgumentException.class);
    }
}
