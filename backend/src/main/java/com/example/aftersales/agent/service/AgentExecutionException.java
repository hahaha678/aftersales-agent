package com.example.aftersales.agent.service;

/** 可识别的执行终止原因；只输出固定文案，不透出底层异常或模型请求内容。 */
public class AgentExecutionException extends IllegalStateException {

    public AgentExecutionException(String code) {
        super(code);
    }

    public static String publicMessage(Throwable error) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof AgentExecutionException) {
                return "TOOL_BUDGET_EXCEEDED".equals(cause.getMessage())
                    ? "本次工具调用已达到 8 次上限，查询未完成。请缩小查询范围或指定订单号后重试。"
                    : "任务已结束，后续工具调用已停止。请重新发送请求。";
            }
            if (
                cause instanceof java.util.concurrent.TimeoutException ||
                cause instanceof java.net.http.HttpTimeoutException
            ) {
                return "模型响应超时，任务已停止，请稍后重试。";
            }
            if (
                cause instanceof com.example.aftersales.identity.service.AuthFailure
            ) return "登录状态已失效，请重新登录后重试。";
        }
        return "模型服务或网络暂时异常，本次请求未完成，请稍后重试。";
    }
}
