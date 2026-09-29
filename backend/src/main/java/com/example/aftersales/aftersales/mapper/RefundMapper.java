package com.example.aftersales.aftersales.mapper;

import com.example.aftersales.aftersales.domain.po.RefundPO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RefundMapper {
    @Select("SELECT * FROM aftersale_refund WHERE request_id=#{id} ORDER BY id DESC")
    List<RefundPO> list(long id);

    @Insert(
        "INSERT INTO aftersale_refund(request_id,request_key,previous_key,operation_number,amount,mode,status,actor_id) VALUES(#{requestId},#{requestKey},#{previousKey},#{operationNumber},#{amount},#{mode},#{status},#{actorId})"
    )
    int insert(RefundPO row);

    @Update(
        "UPDATE aftersale_refund SET status=#{status},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='UNKNOWN'"
    )
    int resolve(@Param("id") long id, @Param("status") String status);

    @Update(
        "UPDATE aftersale_request SET status=#{target},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status=#{source}"
    )
    int transition(@Param("id") long id, @Param("source") String source, @Param("target") String target);

    // 成功与结果未知都计入渠道额度，未知结果不能被当作失败而再次退款。
    @Select(
        "SELECT COALESCE(SUM(r.amount),0) FROM aftersale_refund r JOIN aftersale_request a ON a.id=r.request_id WHERE a.order_item_id=#{id} AND r.status IN ('SUCCEEDED','UNKNOWN')"
    )
    BigDecimal committedAmount(long id);
}
