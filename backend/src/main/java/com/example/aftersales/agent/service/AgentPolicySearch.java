package com.example.aftersales.agent.service;

import com.example.aftersales.agent.domain.query.PolicyToolQuery;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.knowledge.domain.query.PolicySearchQuery;
import com.example.aftersales.knowledge.domain.vo.PolicySearchVO;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** 每轮对话独立创建；只对空结果允许一次改写检索，不降低阈值或跨商品范围。 */
public final class AgentPolicySearch {

    private final Function<PolicySearchQuery, PolicySearchVO> search;
    private final AtomicBoolean fallbackUsed = new AtomicBoolean();
    private final AtomicReference<String> productScope = new AtomicReference<>();

    public AgentPolicySearch(Function<PolicySearchQuery, PolicySearchVO> search) {
        this.search = search;
    }

    public PolicySearchVO search(PolicyToolQuery query) {
        String scope = query.scope().toUpperCase(Locale.ROOT);
        // 单轮只咨询一个商品范围；GLOBAL 通用流程不占用商品范围。
        // 这能拦截模型先查耳机、再切换鼠标找依据，但不能证明第一次选的范围就是正确的。
        if (!scope.equals("GLOBAL")) {
            productScope.compareAndSet(null, scope);
            if (!scope.equals(productScope.get())) throw new ApiRequestException(
                409,
                "POLICY_SCOPE_CONFLICT",
                "本轮已选择商品范围 " +
                    productScope.get() +
                    "，不能切换到其他商品寻找政策依据。请说明当前商品的资料是否充分；不同商品应分轮咨询。"
            );
        }
        var result = search.apply(new PolicySearchQuery(query.question(), query.scope()));
        String fallback = query.fallbackQuestion();
        // 异常直接交回统一工具错误处理，不把服务故障当作检索不到而重试。
        if (
            result.sources().isEmpty() &&
            fallback != null &&
            !fallback.isBlank() &&
            !fallback.trim().equals(query.question().trim()) &&
            fallbackUsed.compareAndSet(false, true)
        ) {
            var retried = search.apply(new PolicySearchQuery(fallback.trim(), query.scope()));
            return new PolicySearchVO(
                retried.message() + " 本轮空结果改写检索已使用，改写问题：" + fallback.trim(),
                retried.sources()
            );
        }
        return result;
    }
}
