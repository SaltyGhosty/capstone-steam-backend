package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limite per IP davanti a registrazione, reinvio, verifica e anteprima: {@code perMinute} richieste al minuto
 * e per indirizzo (di default 20, {@code app.auth.ip-limit.per-minute}). Frena chi tenta di riempire la mappa del
 * limite per email o di indovinare i link da una sola macchina. Dietro il proxy di Render l'IP reale lo lascia
 * {@code server.forward-headers-strategy: native} (in prod).
 */
@Component
public class IpRateLimiter extends SlidingWindowLimiter {

    static final int MAX_ENTRIES = 100_000;
    private static final java.util.regex.Pattern IPV6_LITERAL =
            java.util.regex.Pattern.compile("^[0-9A-Fa-f:.]+(%[A-Za-z0-9._-]+)?$");

    @Autowired
    public IpRateLimiter(Clock clock, IpLimitProperties properties) {
        this(clock, properties.perMinute());
    }

    /**
     * Controlla l'indirizzo di una richiesta. Gli IPv6 si raggruppano per prefisso /64 (una connessione domestica riceve un
     * /64 intero, quindi altrimenti ogni indirizzo dell'attaccante sarebbe una chiave nuova); gli IPv4 non cambiano.
     */
    public void checkAddress(String remoteAddr) {
        check(keyOf(remoteAddr));
    }

    static String keyOf(String remoteAddr) {
        // solo un literal IPv6 viene interpretato: InetAddress.getByName con altro potrebbe fare una query DNS
        if (remoteAddr == null || !remoteAddr.contains(":") || !IPV6_LITERAL.matcher(remoteAddr).matches()) {
            return remoteAddr == null ? "?" : remoteAddr;
        }
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(remoteAddr);
            if (address instanceof java.net.Inet4Address) {
                return address.getHostAddress(); // ::ffff:a.b.c.d
            }
            byte[] bytes = address.getAddress();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
                if (i % 2 == 1 && i < 7) {
                    sb.append(':');
                }
            }
            return sb + "::/64";
        } catch (java.net.UnknownHostException | RuntimeException e) {
            return remoteAddr;
        }
    }

    IpRateLimiter(Clock clock, int perMinute) {
        super(clock, perMinute, Duration.ofMinutes(1), MAX_ENTRIES, "auth.tooManyRequests", true);
    }
}
