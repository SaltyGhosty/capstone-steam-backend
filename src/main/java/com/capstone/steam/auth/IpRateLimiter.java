package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite por IP delante de registro, reenvío, verificación y vista previa: {@code perMinute} peticiones por minuto
 * y dirección (por defecto 20, {@code app.auth.ip-limit.per-minute}). Frena a quien intente llenar el mapa del
 * límite por email o adivinar enlaces desde una sola máquina. Detrás del proxy de Render la IP real la deja
 * {@code server.forward-headers-strategy: native} (en prod).
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
     * Comprueba la dirección de una petición. Las IPv6 se agrupan por su prefijo /64 (una conexión doméstica recibe un
     * /64 entero, así que si no cada dirección del atacante sería una clave nueva); las IPv4 no cambian.
     */
    public void checkAddress(String remoteAddr) {
        check(keyOf(remoteAddr));
    }

    static String keyOf(String remoteAddr) {
        // solo un literal IPv6 se interpreta: InetAddress.getByName con otra cosa podría hacer una consulta DNS
        if (remoteAddr == null || !remoteAddr.contains(":") || !IPV6_LITERAL.matcher(remoteAddr).matches()) {
            return remoteAddr == null ? "?" : remoteAddr;
        }
        try {
            // es un literal IPv6: no se resuelve ningún nombre
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
