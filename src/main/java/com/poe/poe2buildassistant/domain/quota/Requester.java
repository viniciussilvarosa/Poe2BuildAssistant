package com.poe.poe2buildassistant.domain.quota;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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

    /**
     * Visitante a partir do IP: guarda só SHA-256(salt + forma canônica do cliente).
     *
     * <p>A forma canônica impede ganhar cota nova mudando só a escrita do endereço: o literal é
     * interpretado (sem DNS), IPv4 mapeado em IPv6 vira IPv4 e, no IPv6, conta o prefixo /64,
     * que é o bloco entregue a cada cliente pelos provedores (chaos R1/R2). Texto que não é IP
     * e salt vazio são recusados (R3/R4).
     */
    static Requester anonymousFromIp(String ip, String salt) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("salt do hash de IP ausente");
        }
        String client = canonicalClient(ip);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((salt + "\u0000" + client).getBytes(StandardCharsets.UTF_8));
            return new Anonymous(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private static String canonicalClient(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP do visitante ausente");
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(ip.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("endereço do visitante não é um IP válido", e);
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            bytes = Arrays.copyOfRange(bytes, 12, 16);
        }
        return bytes.length == 4
                ? "v4:" + HexFormat.of().formatHex(bytes)
                : "v6/64:" + HexFormat.of().formatHex(Arrays.copyOf(bytes, 8));
    }

    private static boolean isIpv4Mapped(byte[] v6) {
        for (int i = 0; i < 10; i++) {
            if (v6[i] != 0) {
                return false;
            }
        }
        return v6[10] == (byte) 0xff && v6[11] == (byte) 0xff;
    }
}
