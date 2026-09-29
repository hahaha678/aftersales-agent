package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.agent.service.ToolAuditSummary;
import org.junit.jupiter.api.Test;

class ToolAuditSummaryTest {

    @Test
    void onlyWhitelistedStructuredInputsAreRetained() {
        String value = ToolAuditSummary.input(
            "{\"id\":\"DEMO-1002\",\"page\":1,\"quantity\":2,\"token\":\"private-token\",\"description\":\"13800138000\",\"question\":\"private-question\"}"
        );
        assertThat(value)
            .contains("DEMO-1002", "quantity")
            .doesNotContain("private-token", "13800138000", "private-question");
        assertThat(
            ToolAuditSummary.input("{\"id\":\"secret-value\",\"reason\":\"secret-value\",\"scope\":\"secret-value\"}")
        ).doesNotContain("secret-value");
        assertThat(ToolAuditSummary.input("bad-secret-json")).doesNotContain("bad-secret-json");
    }

    @Test
    void resultAndErrorSummariesDoNotCopySensitiveContent() {
        assertThat(
            ToolAuditSummary.result("{\"id\":\"123\",\"status\":\"READY\",\"description\":\"private-description\"}")
        )
            .contains("resourceId", "123", "READY")
            .doesNotContain("private-description");
        assertThat(ToolAuditSummary.result("{\"id\":\"private-id\",\"status\":\"private-status\"}")).doesNotContain(
            "private-id",
            "private-status"
        );
        assertThat(ToolAuditSummary.errorCode("{\"error\":\"INVALID_REQUEST\",\"message\":\"secret\"}")).isEqualTo(
            "INVALID_REQUEST"
        );
        assertThat(ToolAuditSummary.errorCode("{\"error\":\"private-token\"}")).isEqualTo("TOOL_ERROR");
        assertThat(ToolAuditSummary.errorCode("{\"items\":[]}")).isNull();
        assertThat(ToolAuditSummary.result("{\"items\":[{\"address\":\"private-address\"}],\"sources\":[]}"))
            .contains("itemsCount")
            .doesNotContain("private-address");
        assertThat(ToolAuditSummary.errorCode("bad-secret-json")).isEqualTo("INVALID_TOOL_RESULT");
    }
}
