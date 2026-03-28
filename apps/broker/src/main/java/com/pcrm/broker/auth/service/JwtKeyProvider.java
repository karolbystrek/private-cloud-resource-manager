package com.pcrm.broker.auth.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@Getter
public class JwtKeyProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtKeyProvider(
            @Value("${application.security.jwt.private-key}") String privateKeyPath,
            @Value("${application.security.jwt.public-key}") String publicKeyPath
    ) {
        this.privateKey = loadPrivateKey(privateKeyPath);
        this.publicKey = loadPublicKey(publicKeyPath);
    }

    private PrivateKey loadPrivateKey(String privateKeyPath) {
        try {
            String keyContent = Files.readString(Path.of(privateKeyPath))
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Private Key during startup", e);
        }
    }

    private PublicKey loadPublicKey(String publicKeyPath) {
        try {
            String keyContent = Files.readString(Path.of(publicKeyPath))
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Public Key during startup", e);
        }
    }
}
