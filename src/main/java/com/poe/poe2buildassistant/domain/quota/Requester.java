package com.poe.poe2buildassistant.domain.quota;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Quem está pedindo uma build, para fins de cota. O visitante é identificado só pelo hash do IP
 * com salt: o IP puro é dado pessoal e nunca sai daqui.
 */
public sealed interface Requester permits Requester.Anonymous, Requester.Authenticated {

    /** Chave estável usada no contador ({@code anon:<hash>} ou {@code user:<id>}). */
    String key();

    /** Tipo para logs e regras de limite: {@code anonymous} ou {@code authenticated}. */
    String type();

    record Anonymous(String ipHash) implements Requester {
        public Anonymous {
            if (ipHash == null || ipHash.isBlank()) {
                throw new IllegalArgumentException("visitante sem identificação");
            }
        }

        @Override
        public String key() {
            return "anon:" + ipHash;
        }

        @Override
        public String type() {
            return "anonymous";
        }
    }

    record Authenticated(String userId) implements Requester {
        public Authenticated {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("usuário sem id");
            }
        }

        @Override
        public String key() {
            return "user:" + userId;
        }

        @Override
        public String type() {
            return "authenticated";
        }
    }

    /** Visitante a partir do IP: guarda só SHA-256(salt + IP). */
    static Requester anonymousFromIp(String ip, String salt) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP do visitante ausente");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((salt + "\u0000" + ip.strip()).getBytes(StandardCharsets.UTF_8));
            return new Anonymous(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
