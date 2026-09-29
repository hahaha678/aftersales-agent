package com.example.aftersales.aftersales.controller;

import com.example.aftersales.aftersales.domain.vo.EvidenceVO;
import com.example.aftersales.aftersales.service.*;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "售后图片凭证")
public class EvidenceController {

    private final ObjectProvider<EvidenceService> services;

    public EvidenceController(ObjectProvider<EvidenceService> services) {
        this.services = services;
    }

    private EvidenceService service() {
        var service = services.getIfAvailable();
        if (service == null) throw new ContractNotImplementedException();
        return service;
    }

    @PostMapping(value = "/aftersales/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "上传本人待审核申请的图片凭证",
        description = "最多5张，每张5MB；JPEG/PNG；相同文件重试返回原记录。上传后保留，不支持修改删除。"
    )
    public EvidenceVO upload(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        service().list(id, false); // 解码前先验证申请归属。
        return service().upload(id, EvidenceImage.read(file));
    }

    @GetMapping("/aftersales/{id}/evidence")
    @Operation(summary = "查询本人申请的图片列表")
    public List<EvidenceVO> list(@PathVariable long id) {
        return service().list(id, false);
    }

    @GetMapping("/staff/aftersales/{id}/evidence")
    @Operation(summary = "客服查询申请图片列表")
    public List<EvidenceVO> staffList(@PathVariable long id) {
        return service().list(id, true);
    }

    @GetMapping("/aftersales/{id}/evidence/{imageId}/content")
    @Operation(summary = "读取本人申请图片")
    public ResponseEntity<byte[]> content(@PathVariable long id, @PathVariable String imageId) {
        return image(id, imageId, false);
    }

    @GetMapping("/staff/aftersales/{id}/evidence/{imageId}/content")
    @Operation(summary = "客服读取申请图片")
    public ResponseEntity<byte[]> staffContent(@PathVariable long id, @PathVariable String imageId) {
        return image(id, imageId, true);
    }

    private ResponseEntity<byte[]> image(long id, String imageId, boolean staff) {
        var metadata = service()
            .list(id, staff)
            .stream()
            .filter(item -> item.id().equals(imageId))
            .findFirst()
            .orElseThrow(() ->
                new com.example.aftersales.common.exception.ApiRequestException(404, "RESOURCE_NOT_FOUND", "图片不存在")
            );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(metadata.mediaType()))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(service().content(id, imageId, staff));
    }
}
