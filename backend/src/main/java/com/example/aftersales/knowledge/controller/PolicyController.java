package com.example.aftersales.knowledge.controller;

import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.conversation.service.ConversationService;
import com.example.aftersales.knowledge.domain.dto.*;
import com.example.aftersales.knowledge.domain.po.PolicyPO;
import com.example.aftersales.knowledge.domain.query.*;
import com.example.aftersales.knowledge.domain.vo.*;
import com.example.aftersales.knowledge.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "06 政策知识库", description = "客服维护与发布；登录用户可检索并查看来源")
@SecurityRequirement(name = "bearerAuth")
public class PolicyController {

    private final ObjectProvider<PolicyService> services;
    private final ObjectProvider<ConversationService> conversations;

    public PolicyController(ObjectProvider<PolicyService> services, ObjectProvider<ConversationService> conversations) {
        this.services = services;
        this.conversations = conversations;
    }

    private PolicyService service() {
        var s = services.getIfAvailable();
        if (s == null) throw new ContractNotImplementedException();
        return s;
    }

    @GetMapping("/knowledge/status")
    @Operation(summary = "查询向量服务是否配置，不进行网络探测")
    public Map<String, Object> status() {
        return service().status();
    }

    @GetMapping("/staff/policies")
    @Operation(summary = "客服查看最近100个政策版本")
    public List<PolicyVO> list() {
        return service().list().stream().map(PolicyVO::of).toList();
    }

    @PostMapping("/staff/policies")
    @Operation(summary = "客服录入政策草稿，支持纯文本和Markdown原文")
    public ResponseEntity<PolicyVO> create(@Valid @RequestBody PolicyDTO body) {
        var row = service().create(body);
        return ResponseEntity.created(URI.create("/api/policies/" + row.id())).body(PolicyVO.of(row));
    }

    @PutMapping("/staff/policies/{id}")
    @Operation(summary = "编辑未发布草稿，使用expectedFingerprint防止覆盖他人修改")
    public PolicyVO edit(@PathVariable String id, @Valid @RequestBody EditPolicyDTO body) {
        return PolicyVO.of(service().edit(id, body));
    }

    @PostMapping("/staff/policies/{id}/publication")
    @Operation(summary = "向量化并原子发布；替换同政策同范围的已发布版本")
    public PolicyVO publish(@PathVariable String id) {
        return PolicyVO.of(service().publish(id));
    }

    @PostMapping("/staff/policies/{id}/archival")
    @Operation(summary = "下线政策，不再参与新检索")
    public PolicyVO archive(@PathVariable String id) {
        return PolicyVO.of(service().archive(id));
    }

    @GetMapping("/policies/{id}")
    @Operation(summary = "查看引用原文；草稿仅客服可查看")
    public PolicyVO detail(@PathVariable String id) {
        return PolicyVO.of(service().detail(id));
    }

    @PostMapping("/knowledge/searches")
    @Operation(summary = "检索当前范围与通用范围内有效政策，最多返回4段")
    public PolicySearchVO search(@Valid @RequestBody PolicySearchQuery body) {
        return service().search(body);
    }

    @GetMapping("/agent-runs/{id}/sources")
    @Operation(summary = "查看本人回答检索使用的政策来源")
    public List<PolicySourceVO> sources(@PathVariable String id) {
        service();
        conversations.getObject().ownedRun(id);
        return service().sources(id);
    }
}
