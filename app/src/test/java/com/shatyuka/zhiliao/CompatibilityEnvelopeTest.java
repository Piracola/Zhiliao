package com.shatyuka.zhiliao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * 云端配置信封的校验测试。这里最要紧的是两条：
 * 改一个字符就该验不过（摘要），换个私钥签的也该验不过（签名）——
 * 云端配置是唯一一条"从外面进来"的数据，验不严等于把适配逻辑交出去。
 */
public class CompatibilityEnvelopeTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private static final String PAYLOAD = "{\"schemaVersion\":1,\"revision\":7,\"symbols\":{"
            + "\"article.contentMixAdapter\":[\"com.zhihu.android.mix.adapter.a\"],"
            + "\"searchAd.converters\":[\"retrofit2.b.a.c\"]}}";

    @BeforeClass
    public static void makeKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    private static String envelope(String payload, PrivateKey signer) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        String digest = toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(signer);
        signature.update(bytes);
        String encoded = Base64.getEncoder().encodeToString(signature.sign());
        JSONObject json = new JSONObject();
        json.put("payload", payload);
        json.put("sha256", digest);
        json.put("signature", encoded);
        return json.toString();
    }

    @Test
    public void validEnvelopeYieldsPayload() throws Exception {
        CompatibilityEnvelope.Verified verified =
                CompatibilityEnvelope.verify(envelope(PAYLOAD, keyPair.getPrivate()), keyPair.getPublic());
        assertEquals(7, verified.revision);
        assertEquals(2, verified.symbols.size());
        assertEquals("com.zhihu.android.mix.adapter.a", verified.symbols.get("article.contentMixAdapter")[0]);
    }

    @Test(expected = SecurityException.class)
    public void tamperedPayloadIsRejected() throws Exception {
        String json = envelope(PAYLOAD, keyPair.getPrivate());
        JSONObject envelope = new JSONObject(json);
        envelope.put("payload", PAYLOAD.replace("mix.adapter.a", "mix.adapter.b"));
        CompatibilityEnvelope.verify(envelope.toString(), keyPair.getPublic());
    }

    @Test(expected = SecurityException.class)
    public void signatureFromAnotherKeyIsRejected() throws Exception {
        CompatibilityEnvelope.verify(envelope(PAYLOAD, otherKeyPair.getPrivate()), keyPair.getPublic());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownSchemaVersionIsRejected() throws Exception {
        String payload = PAYLOAD.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        CompatibilityEnvelope.verify(envelope(payload, keyPair.getPrivate()), keyPair.getPublic());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveRevisionIsRejected() throws Exception {
        String payload = PAYLOAD.replace("\"revision\":7", "\"revision\":0");
        CompatibilityEnvelope.verify(envelope(payload, keyPair.getPrivate()), keyPair.getPublic());
    }

    @Test
    public void revisionOnlyMovesForward() {
        assertTrue(CompatibilityEnvelope.isNewer(4, 5));
        assertFalse(CompatibilityEnvelope.isNewer(5, 5));
        assertFalse(CompatibilityEnvelope.isNewer(5, 4));
    }

    @Test(expected = SecurityException.class)
    public void brokenDigestStringIsRejected() throws Exception {
        String json = envelope(PAYLOAD, keyPair.getPrivate());
        JSONObject envelope = new JSONObject(json);
        envelope.put("sha256", "not-a-hex-digest");
        CompatibilityEnvelope.verify(envelope.toString(), keyPair.getPublic());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @Test(expected = IllegalArgumentException.class)
    public void payloadWithoutUsableSymbolsIsRejected() throws Exception {
        // 字段名不合规的配置等于什么都没配，不该拿它覆盖本地名单
        String payload = "{\"schemaVersion\":1,\"revision\":3,\"symbols\":{\"bad key!\":[\"x\"]}}";
        CompatibilityEnvelope.verify(envelope(payload, keyPair.getPrivate()), keyPair.getPublic());
    }
}
