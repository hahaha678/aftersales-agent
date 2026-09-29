package com.example.aftersales.agent.service;

import com.example.aftersales.agent.domain.vo.RunVO;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.conversation.domain.dto.SendMessageDTO;
import com.example.aftersales.conversation.service.ConversationService;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理一次回答的执行、超时和取消，通过 SSE 向前端发送进度与结果。 */
@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AgentService {

    private final ModelGateway model;
    private final AgentTools tools;
    private final ConversationService conversations;
    // 只保存当前进程的活动任务，服务重启后不会恢复；历史和终态以数据库记录为准。
    private final ConcurrentMap<String, Execution> executions = new ConcurrentHashMap<>();
    // 限制并发和排队数量，避免模型请求堆积耗尽服务资源。
    private final ExecutorService workers = new ThreadPoolExecutor(
        4,
        4,
        0,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8),
        r -> {
            var thread = new Thread(r, "aftersales-agent");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
    // 定时线程负责超时和 SSE 心跳，不承担模型推理请求。
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        var thread = new Thread(r, "agent-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public AgentService(ModelGateway model, AgentTools tools, ConversationService conversations) {
        this.model = model;
        this.tools = tools;
        this.conversations = conversations;
    }

    public Map<String, Object> status() {
        return Map.of(
            "available",
            model.available(),
            "model",
            model.model(),
            "message",
            model.available() ? "已启用" : "尚未启用 AI，请配置后端 AI_ENABLED 和 DEEPSEEK_API_KEY"
        );
    }

    /** 创建或复用任务后立即返回；模型请求在 workers 中执行，前端另行订阅 events。 */
    public RunVO send(String conversation, SendMessageDTO body, String token) {
        if (!model.available()) throw new ApiRequestException(
            503,
            "AI_NOT_CONFIGURED",
            "智能助手尚未配置，请在后端设置 AI_ENABLED=true 和 DEEPSEEK_API_KEY"
        );
        // begin 在事务中检查会话归属、请求键、并发和频率；重试同一请求不会重复调用模型。
        var start = conversations.begin(conversation, body, model.model());
        if (!start.fresh()) return RunVO.of(start.run());
        String id = start.run().id();
        var execution = new Execution(id);
        executions.put(id, execution);
        execution.emit("status", Map.of("message", "正在理解你的问题…"));
        try {
            // 从任务入队开始计时，排队时间也计入 90 秒预算。
            execution.timeout = scheduler.schedule(
                () -> execution.end("FAILED", "处理超过 90 秒，任务已停止，请稍后重试", true),
                90,
                TimeUnit.SECONDS
            );
            execution.future = workers.submit(() -> {
                try {
                    if (!execution.active()) return;
                    model.stream(
                        conversations.history(conversation),
                        body.content(),
                        tools.forRun(id, token, execution::active, execution::emit),
                        // 网关只回调数据，本层统一处理累计、状态检查和 SSE 转发。
                        chunk -> {
                            synchronized (execution) {
                                // 与 end 共用任务锁，防止取消后又追加正文或发送新的文本事件。
                                if (!execution.active()) {
                                    if (chunk.text().isEmpty()) return;
                                    throw new CancellationException();
                                }
                                execution.input.addAndGet(chunk.inputTokens());
                                execution.output.addAndGet(chunk.outputTokens());
                                if (!chunk.text().isEmpty()) {
                                    if (
                                        execution.content.length() + chunk.text().length() > 16000
                                    ) throw new IllegalStateException("回答长度超限");
                                    execution.content.append(chunk.text());
                                    execution.emit("delta", Map.of("text", chunk.text()));
                                }
                            }
                        }
                    );
                    if (execution.content.isEmpty()) execution.end("FAILED", "模型没有返回有效回答，请重试", false);
                    else execution.end("SUCCEEDED", null, false);
                } catch (Exception ex) {
                    execution.end("FAILED", "智能助手暂时无法完成请求，请检查模型服务、网络或登录状态后重试", false);
                }
            });
        } catch (RejectedExecutionException ex) {
            // 队列满时也将已创建的数据库任务置为失败，不能让它一直停留在 RUNNING。
            execution.end("FAILED", "当前请求较多，请稍后重试", false);
        }
        return RunVO.of(conversations.internalRun(id));
    }

    public RunVO get(String id) {
        return RunVO.of(conversations.ownedRun(id));
    }

    /** 停止回答不会回滚已经生成的草稿，也不会撤销正式售后申请。 */
    public RunVO cancel(String id) {
        var row = conversations.ownedRun(id);
        var execution = executions.get(id);
        if (execution != null) execution.end("CANCELLED", "用户停止了本次回答", true);
        else if (row.status().equals("RUNNING")) conversations.finish(
            id,
            "CANCELLED",
            row.assistantContent(),
            "用户停止了本次回答",
            row.inputTokens(),
            row.outputTokens()
        );
        return RunVO.of(conversations.internalRun(id));
    }

    /** 订阅已存在的任务；连接或重连只读取结果，不重新发起模型请求。 */
    public SseEmitter events(String id) {
        var row = conversations.ownedRun(id);
        var emitter = new SseEmitter(110_000L);
        var execution = executions.get(id);
        if (execution == null) {
            // 首次读取后，工作线程可能刚好完成并移除了内存任务，需要重新读取数据库。
            row = conversations.ownedRun(id);
            if (row.status().equals("RUNNING")) throw new ApiRequestException(
                409,
                "RUN_NOT_ATTACHED",
                "任务暂不可连接，请刷新历史；服务重启后任务将在租约到期时结束"
            );
            // 快速完成的任务可能早于前端订阅，使用完整快照补齐正文，再通知完成。
            sendEvent(emitter, "snapshot", Map.of("text", row.assistantContent()));
            sendEvent(emitter, "done", RunVO.of(row));
            emitter.complete();
            return emitter;
        }
        execution.attach(emitter);
        return emitter;
    }

    private static void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ex) {
            // 客户端断开只关闭这条连接；回答任务继续执行，用户稍后可从历史恢复。
            emitter.complete();
        }
    }

    private record Event(String name, Object data) {}

    // 单次任务的内存状态；数据库保存最终结果，replay 用于连接建立后的事件补发。
    private final class Execution {

        final String id;
        final StringBuilder content = new StringBuilder();
        final AtomicInteger input = new AtomicInteger(),
            output = new AtomicInteger();
        final List<Event> replay = new ArrayList<>();
        final List<SseEmitter> subscribers = new ArrayList<>();
        // volatile 让工作线程与定时线程能看到最新状态；复合操作仍由 synchronized 保护。
        volatile boolean ended;
        volatile Future<?> future;
        volatile ScheduledFuture<?> timeout;

        Execution(String id) {
            this.id = id;
        }

        boolean active() {
            return !ended && !Thread.currentThread().isInterrupted();
        }

        // 先记录再广播，订阅较晚的前端也能补收到已发生的进度、正文和草稿事件。
        synchronized void emit(String name, Object data) {
            if (ended) return;
            replay.add(new Event(name, data));
            for (var subscriber : List.copyOf(subscribers)) sendEvent(subscriber, name, data);
        }

        // 补发历史与注册订阅在同一把锁内完成，避免两者之间遗漏新事件。
        synchronized void attach(SseEmitter emitter) {
            for (var event : replay) sendEvent(emitter, event.name(), event.data());
            if (ended) {
                sendEvent(emitter, "done", RunVO.of(conversations.internalRun(id)));
                emitter.complete();
                return;
            }
            subscribers.add(emitter);
            // 模型等待期间可能长时间没有正文，用心跳维持连接活跃；心跳不写入聊天记录。
            var heartbeat = scheduler.scheduleAtFixedRate(
                () -> sendEvent(emitter, "heartbeat", Map.of()),
                10,
                10,
                TimeUnit.SECONDS
            );
            // 每条连接单独清理心跳和订阅，不取消共享的回答任务。
            Runnable detach = () -> {
                heartbeat.cancel(false);
                synchronized (this) {
                    subscribers.remove(emitter);
                }
            };
            emitter.onCompletion(detach);
            emitter.onTimeout(() -> {
                detach.run();
                emitter.complete();
            });
            emitter.onError(ex -> detach.run());
        }

        synchronized void end(String state, String error, boolean interrupt) {
            // 完成、超时和用户取消可能同时发生，只允许一次终态写入。
            if (ended) return;
            ended = true;
            if (timeout != null) timeout.cancel(false);
            if (interrupt && future != null) future.cancel(true);
            try {
                // 先持久化再发送 done，前端收到终态后即可读取到相应历史消息。
                conversations.finish(id, state, content.toString(), error, input.get(), output.get());
                var finalRow = RunVO.of(conversations.internalRun(id));
                for (var subscriber : List.copyOf(subscribers)) {
                    sendEvent(subscriber, "done", finalRow);
                    subscriber.complete();
                }
            } finally {
                executions.remove(id, this);
            }
        }
    }

    // Spring 容器关闭时结束本进程任务并释放线程池，避免任务长时间显示为运行中。
    @PreDestroy
    void close() {
        for (var execution : List.copyOf(executions.values()))
            execution.end("FAILED", "服务正在关闭，请重新发送", true);
        workers.shutdownNow();
        scheduler.shutdownNow();
    }
}
