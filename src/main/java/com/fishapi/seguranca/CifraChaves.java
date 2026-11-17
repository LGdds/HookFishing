package com.fishapi.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Guarda o valor da API Key cifrado (AES-GCM), além do hash usado na autenticação.
 *
 * Motivo: o login devolve SEMPRE a mesma chave do usuário. Antes, cada login gerava um valor novo e derrubava
 * as outras sessões do mesmo usuário (ex.: entrar no Swagger invalidava o front-end aberto).
 * O segredo vem da variável de ambiente HOOKFISHING_SEGREDO_CHAVES; em produção, use um valor longo e aleatório.
 */
@Component
public class CifraChaves {

    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG = 128;

    private final SecretKeySpec chaveAes;
    private final SecureRandom random = new SecureRandom();

    public CifraChaves(@Value("${hookfishing.chaves.segredo}") String segredo) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            this.chaveAes = new SecretKeySpec(bytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível preparar a cifra das API Keys", e);
        }
    }

    public String cifrar(String valor) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            random.nextBytes(iv);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chaveAes, new GCMParameterSpec(TAMANHO_TAG, iv));
            byte[] cifrado = cifra.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar a API Key", e);
        }
    }

    /** Vazio se o valor não puder ser decifrado (ex.: o segredo mudou). */
    public Optional<String> decifrar(String valorCifrado) {
        if (valorCifrado == null || valorCifrado.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] dados = Base64.getDecoder().decode(valorCifrado);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chaveAes, new GCMParameterSpec(TAMANHO_TAG, dados, 0, TAMANHO_IV));
            byte[] claro = cifra.doFinal(dados, TAMANHO_IV, dados.length - TAMANHO_IV);
            return Optional.of(new String(claro, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
