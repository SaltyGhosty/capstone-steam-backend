package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.capstone.steam.common.exception.TooManyRequestsException;

class IpRateLimiterTest {

    @Test
    void ipv4KeepsItsOwnKey() {
        assertThat(IpRateLimiter.keyOf("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(IpRateLimiter.keyOf("203.0.113.8")).isNotEqualTo(IpRateLimiter.keyOf("203.0.113.7"));
        assertThat(IpRateLimiter.keyOf(null)).isEqualTo("?");
    }

    @Test
    void ipv6AddressesOfTheSame64PrefixShareAKey() {
        String a = IpRateLimiter.keyOf("2001:db8:1:2:aaaa:bbbb:cccc:dddd");
        String b = IpRateLimiter.keyOf("2001:0db8:0001:0002:1111:2222:3333:4444");
        String otherPrefix = IpRateLimiter.keyOf("2001:db8:1:3:aaaa:bbbb:cccc:dddd");

        assertThat(a).isEqualTo(b).isEqualTo("2001:0db8:0001:0002::/64");
        assertThat(otherPrefix).isNotEqualTo(a);
    }

    @Test
    void aNonLiteralStringIsUsedAsIsWithoutAnyLookup() {
        // «unknown:1» no es una IP: no se pasa a InetAddress (podría hacer una consulta DNS), se usa tal cual
        assertThat(IpRateLimiter.keyOf("unknown:1")).isEqualTo("unknown:1");
        assertThat(IpRateLimiter.keyOf("host.example.com:8080")).isEqualTo("host.example.com:8080");
    }

    @Test
    void theIpLimitRetryAfterIsTheRealTimeUntilThereIsRoom() {
        IpRateLimiter limiter = new IpRateLimiter(Clock.systemUTC(), 1);
        limiter.checkAddress("203.0.113.1");

        assertThatThrownBy(() -> limiter.checkAddress("203.0.113.1"))
                .isInstanceOfSatisfying(com.capstone.steam.common.exception.RateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 61L));
    }

    @Test
    void aLimitOfZeroRejectsEverythingInsteadOfFailingWithANullPointer() {
        IpRateLimiter limiter = new IpRateLimiter(Clock.systemUTC(), 0);

        assertThatThrownBy(() -> limiter.checkAddress("203.0.113.1"))
                .isInstanceOfSatisfying(com.capstone.steam.common.exception.RateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
    }

    @Test
    void thePropertiesRejectALimitBelowOne() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new IpLimitProperties(0))).isNotEmpty();
        assertThat(validator.validate(new IpLimitProperties(60))).isEmpty();
    }

    @Test
    void anIpv4MappedIpv6AddressUsesTheIpv4Key() {
        assertThat(IpRateLimiter.keyOf("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void changingAddressInsideTheSame64DoesNotEscapeTheLimit() {
        IpRateLimiter limiter = new IpRateLimiter(Clock.systemUTC(), 3);
        limiter.checkAddress("2001:db8:1:2::1");
        limiter.checkAddress("2001:db8:1:2::2");
        limiter.checkAddress("2001:db8:1:2:ffff:ffff:ffff:ffff");

        assertThatThrownBy(() -> limiter.checkAddress("2001:db8:1:2::99")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkAddress("2001:db8:1:9::1")).doesNotThrowAnyException();
    }
}
