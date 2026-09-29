package com.example.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aftersales.agent.service.AgentExecutionException;
import org.junit.jupiter.api.Test;

class AgentExecutionExceptionTest {

    @Test
    void unwrapsBudgetAndTimeoutWithoutExposingProviderMessages() {
        assertThat(
            AgentExecutionException.publicMessage(
                new RuntimeException("secret", new AgentExecutionException("TOOL_BUDGET_EXCEEDED"))
            )
        )
            .contains("8 次上限")
            .doesNotContain("secret");
        assertThat(
            AgentExecutionException.publicMessage(
                new IllegalStateException(new java.util.concurrent.TimeoutException("secret"))
            )
        )
            .contains("超时")
            .doesNotContain("secret");
        assertThat(AgentExecutionException.publicMessage(new RuntimeException("secret")))
            .contains("模型服务或网络")
            .doesNotContain("secret");
    }
}
