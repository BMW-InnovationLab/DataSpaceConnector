package org.eclipse.dataspaceconnector.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspaceconnector.spi.iam.PublicKeyResolver;
import org.eclipse.dataspaceconnector.spi.iam.TokenValidationService;
import org.eclipse.dataspaceconnector.spi.result.Result;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenValidationServiceImplTest {

    private TokenValidationService tokenValidationService;
    private RSAKey key;
    private JwtClaimValidationRule rule;
    private Date now;
    private String publicKeyId;

    @BeforeEach
    public void setUp() throws JOSEException {
        key = testKey();
        rule = mock(JwtClaimValidationRule.class);
        var publicKey = (RSAPublicKey) key.toPublicKey();
        publicKeyId = UUID.randomUUID().toString();
        var resolver = new PublicKeyResolver() {
            @Override
            public @Nullable
            RSAPublicKey resolveKey(String id) {
                return id.equals(publicKeyId) ? publicKey : null;
            }
        };
        tokenValidationService = new TokenValidationServiceImpl(resolver, Collections.singletonList(rule));
        now = new Date();
    }

    @Test
    void validationSuccess() throws JOSEException {
        var claims = createClaims(now);
        when(rule.checkRule(any())).thenReturn(Result.success(claims));

        var result = tokenValidationService.validate(createJwt(publicKeyId, claims, key.toPrivateKey()));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().getClaims())
                .containsEntry("foo", "bar")
                .containsEntry("exp", now.toString());
    }

    @Test
    void validationFailure_cannotResolvePublicKey() throws JOSEException {
        var claims = createClaims(now);
        when(rule.checkRule(any())).thenReturn(Result.failure("Rule validation failed!"));

        var result = tokenValidationService.validate(createJwt("unknown-key", claims, key.toPrivateKey()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).containsExactly("Failed to resolve public key with id: unknown-key");
    }

    @Test
    void validationFailure_validationRuleKo() throws JOSEException {
        var claims = createClaims(now);
        when(rule.checkRule(any())).thenReturn(Result.failure("Rule validation failed!"));

        var result = tokenValidationService.validate(createJwt(publicKeyId, claims, key.toPrivateKey()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).containsExactly("Rule validation failed!");
    }

    private JWTClaimsSet createClaims(Date exp) {
        return new JWTClaimsSet.Builder()
                .claim("foo", "bar")
                .expirationTime(exp)
                .build();
    }

    private static String createJwt(String publicKeyId, JWTClaimsSet claimsSet, PrivateKey pk) {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(publicKeyId).build();
        try {
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(new RSASSASigner(pk));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new AssertionError(e);
        }
    }

    private static RSAKey testKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
                .keyID(UUID.randomUUID().toString()) // give the key a unique ID
                .generate();
    }
}