package com.gamedead.server.auth;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TokenCodecTest {
    private final TokenCodec codec = new TokenCodec(new SecureRandom());
    @Test void generatesIndependentUrlSafeTokensWith256Bits() {
        String first=codec.generate(), second=codec.generate();
        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
    }
    @Test void hashesUsingSha256() {
        assertThat(codec.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
