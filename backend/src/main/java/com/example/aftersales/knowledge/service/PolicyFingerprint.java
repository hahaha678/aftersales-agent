package com.example.aftersales.knowledge.service;

import com.example.aftersales.knowledge.domain.po.PolicyPO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;

/** 比较客户端看到的完整版本，避免静默覆盖别人的草稿。此值不是身份凭据。 */
public final class PolicyFingerprint {

    private PolicyFingerprint() {}

    public static String of(PolicyPO row) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    JsonMapper.builder().build().writeValueAsString(row).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成政策指纹", ex);
        }
    }
}
