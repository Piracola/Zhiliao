package com.shatyuka.zhiliao;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * 云端兼容性配置的信封校验。
 *
 * 信封长这样：{"payload":"<原样 JSON 字符串>","sha256":"<hex>","signature":"<base64>"}
 * 校验顺序：大小封顶 → payload 的 SHA-256 对得上 → ECDSA 验签通过 → payload 本身合 schema。
 * 选 ECDSA P-256 而不是 Ed25519，是因为后者要 API 33+，而本模块 minSdk 26。
 *
 * 这里全程只做"验"和"读"，不执行任何来自云端的东西——云端最多只能影响"用哪个候选名"。
 */
public final class CompatibilityEnvelope {

    /** 信封本身的上限，防一个超大响应把内存吃掉。 */
    public static final int MAX_ENVELOPE_BYTES = 512 * 1024;
    /** payload 上限，与 CandidateRegistry 的解析上限一致。 */
    public static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    /** 只认这一个 schema 版本，将来改结构就换号，老客户端自然拒绝。 */
    public static final int SCHEMA_VERSION = 1;

    private CompatibilityEnvelope() {
    }

    public static final class Verified {
        public final String payload;
        public final int revision;
        public final Map<String, String[]> symbols;

        Verified(String payload, int revision, Map<String, String[]> symbols) {
            this.payload = payload;
            this.revision = revision;
            this.symbols = symbols;
        }
    }

    /** 验签通过后返回 payload 内容；任何一步不过都抛异常，调用方按"这份配置不可用"处理。 */
    public static Verified verify(String envelopeJson, PublicKey publicKey) throws Exception {
        if (envelopeJson == null || envelopeJson.isEmpty()) {
            throw new IllegalArgumentException("信封为空");
        }
        if (envelopeJson.length() > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("信封过大：" + envelopeJson.length() + " 字符");
        }
        JSONObject envelope = new JSONObject(envelopeJson);
        String payload = envelope.optString("payload", null);
        String digest = envelope.optString("sha256", null);
        String signature = envelope.optString("signature", null);
        if (payload == null || digest == null || signature == null) {
            throw new IllegalArgumentException("信封缺少 payload/sha256/signature");
        }
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload 过大：" + payloadBytes.length + " 字节");
        }

        byte[] expected = hex(digest);
        byte[] actual = MessageDigest.getInstance("SHA-256").digest(payloadBytes);
        if (expected == null || !MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("payload 摘要不匹配");
        }

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(payloadBytes);
        if (!verifier.verify(Base64.getDecoder().decode(signature))) {
            throw new SecurityException("签名校验失败");
        }

        JSONObject body = new JSONObject(payload);
        int schema = body.optInt("schemaVersion", -1);
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion 不支持：" + schema);
        }
        int revision = body.optInt("revision", -1);
        if (revision <= 0) {
            throw new IllegalArgumentException("revision 非法：" + revision);
        }
        // 复用手写的解析器：这里只做"结构是否合法"，候选名能不能用交给 TargetResolver 逐个验证
        Map<String, String[]> symbols = CandidateRegistry.parse(payload);
        if (symbols.isEmpty()) {
            // 一个候选都不剩，说明这份配置要么是坏的、要么是拿错了文件，别拿它覆盖本地名单
            throw new IllegalArgumentException("配置里没有任何可用候选");
        }
        return new Verified(payload, revision, symbols);
    }

    /** revision 只增：不比本地新的一律丢弃，避免拿旧配置把新版本"降"回去。 */
    public static boolean isNewer(int localRevision, int incomingRevision) {
        return incomingRevision > localRevision;
    }

    /** 公钥硬编码在 APK 里，从 Base64 的 X.509 编码还原。 */
    public static PublicKey loadPublicKey(String base64X509) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(base64X509);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static byte[] hex(String value) {
        String text = value.trim();
        if (text.length() != 64) {
            return null;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
