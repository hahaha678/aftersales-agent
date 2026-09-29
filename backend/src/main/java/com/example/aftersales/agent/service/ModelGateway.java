package com.example.aftersales.agent.service;

import com.example.aftersales.agent.domain.po.RunPO;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.tool.ToolCallback;

/**
 * 项目自己的模型接口：AgentService 依赖此接口，不直接依赖 DeepSeek 的 SDK。
 * 生产实现调用真实模型，业务集成测试可替换为脚本实现，避免调用付费 API。
 */
public interface ModelGateway {
    /** 判断是否具备调用配置，不表示远程服务已经通过连通性检查。 */
    boolean available();
    /** 返回配置的模型名称，用于任务记录和前端展示。 */
    String model();
    /** 执行一轮问答，允许模型多次使用工具；返回前完成 sink 回调，失败时抛出异常。 */
    void stream(List<RunPO> history, String message, List<ToolCallback> tools, Consumer<Chunk> sink);

    /** text 为空时可仅上报用量；用量由上层累加，正文的分片方式由具体网关决定。 */
    record Chunk(String text, int inputTokens, int outputTokens) {}
}
