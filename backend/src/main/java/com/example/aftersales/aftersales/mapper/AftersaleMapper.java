package com.example.aftersales.aftersales.mapper;

import com.example.aftersales.aftersales.domain.po.*;
import com.example.aftersales.order.domain.po.OrderPO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AftersaleMapper {
    @Select("SELECT * FROM trade_order WHERE id=#{id} FOR UPDATE")
    OrderPO lockOrder(long id);

    // 已完成的商品已处理，不得重新释放为可申请数量；金额使用同一口径。
    @Select(
        "SELECT COALESCE(SUM(quantity),0) FROM aftersale_request WHERE order_item_id=#{id} AND status IN ('PENDING','APPROVED','RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED')"
    )
    int occupied(long id);

    @Select(
        "SELECT COALESCE(SUM(amount),0) FROM aftersale_request WHERE order_item_id=#{id} AND status IN ('PENDING','APPROVED','RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED')"
    )
    BigDecimal reservedAmount(long id);

    AftersalePO find(long id);
    AftersalePO findByKey(@Param("userId") long userId, @Param("key") String key);
    long count(@Param("userId") Long userId, @Param("status") String status);
    List<AftersalePO> page(
        @Param("userId") Long userId,
        @Param("status") String status,
        @Param("offset") long offset,
        @Param("size") int size
    );

    @Insert(
        "INSERT INTO aftersale_request(user_id,order_id,order_item_id,request_key,quantity,amount,reason,description,status) VALUES(#{userId},#{orderId},#{orderItemId},#{requestKey},#{quantity},#{amount},#{reason},#{description},'PENDING')"
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AftersalePO value);

    @Update(
        "UPDATE aftersale_request SET status=#{status},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='PENDING'"
    )
    int transition(@Param("id") long id, @Param("status") String status);

    @Update(
        "UPDATE aftersale_request SET status='RETURN_SHIPPED',return_carrier=#{carrier},return_tracking_number=#{tracking},return_registered_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='APPROVED'"
    )
    int registerReturn(@Param("id") long id, @Param("carrier") String carrier, @Param("tracking") String tracking);

    @Update(
        "UPDATE aftersale_request SET status='RETURN_RECEIVED',received_at=UTC_TIMESTAMP(3),receipt_note=#{note},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='RETURN_SHIPPED'"
    )
    int receiveReturn(@Param("id") long id, @Param("note") String note);

    @Insert("INSERT INTO aftersale_event(request_id,actor_id,action,note) VALUES(#{id},#{actor},#{action},#{note})")
    int event(
        @Param("id") long id,
        @Param("actor") long actor,
        @Param("action") String action,
        @Param("note") String note
    );

    @Select("SELECT action,note,occurred_at FROM aftersale_event WHERE request_id=#{id} ORDER BY occurred_at,id")
    List<AftersaleEventPO> events(long id);
}
