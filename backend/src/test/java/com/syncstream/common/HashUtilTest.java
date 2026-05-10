package com.syncstream.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashUtilTest {
    @Test
    void sha256IsStable() {
        assertThat(HashUtil.sha256("syncstream"))
                .isEqualTo("84b2a7b2909e1538a7c66471a1c5ba38939cfb995c49ff454c8ec73c0c1f84d2");
    }

    @Test
    void randomTokensAreDifferent() {
        assertThat(HashUtil.randomToken()).isNotEqualTo(HashUtil.randomToken());
    }
}
