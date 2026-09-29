package com.example.aftersales.aftersales.service;

import com.example.aftersales.aftersales.domain.po.AftersalePO;
import com.example.aftersales.aftersales.domain.vo.EvidenceVO;
import com.example.aftersales.aftersales.mapper.*;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.service.CurrentUserService;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class EvidenceService {

    private final CurrentUserService user;
    private final AftersaleMapper requests;
    private final EvidenceMapper images;

    public EvidenceService(CurrentUserService user, AftersaleMapper requests, EvidenceMapper images) {
        this.user = user;
        this.requests = requests;
        this.images = images;
    }

    private AftersalePO accessible(long id, boolean staff) {
        long uid = user.requireUserId();
        if (staff) user.requireStaff();
        var request = requests.find(id);
        if (request == null || (!staff && request.getUserId() != uid)) {
            throw new ApiRequestException(404, "RESOURCE_NOT_FOUND", "售后申请不存在或不可访问");
        }
        return request;
    }

    public List<EvidenceVO> list(long id, boolean staff) {
        accessible(id, staff);
        return images.list(id);
    }

    public byte[] content(long id, String imageId, boolean staff) {
        accessible(id, staff);
        var content = images.content(id, imageId);
        if (content == null) throw new ApiRequestException(404, "RESOURCE_NOT_FOUND", "图片不存在");
        return content.content();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvidenceVO upload(long id, EvidenceImage image) {
        var request = accessible(id, false);
        // 与审核、撤销使用相同的订单行锁，防止审核完成后又追加凭证或并发突破数量上限。
        requests.lockOrder(request.getOrderId());
        request = accessible(id, false);
        var existing = images.duplicate(id, image.hash());
        if (existing != null) return existing;
        if (!"PENDING".equals(request.getStatus())) throw new ApiRequestException(
            409,
            "INVALID_STATE",
            "仅待审核申请可以上传图片凭证"
        );
        if (images.list(id).size() >= 5) throw new ApiRequestException(
            409,
            "EVIDENCE_LIMIT",
            "每个申请最多上传 5 张图片"
        );
        String imageId = UUID.randomUUID().toString();
        // 图片和元数据在同一事务提交，失败不会留下孤立文件；相同内容重试返回原记录。
        images.insert(imageId, id, image.hash(), image.mediaType(), image.content().length, image.content());
        return images.duplicate(id, image.hash());
    }
}
