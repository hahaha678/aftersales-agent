package com.example.aftersales.aftersales.mapper;

import com.example.aftersales.aftersales.domain.po.*;
import com.example.aftersales.order.domain.po.OrderPO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AftersaleMapper {
    // 一次 SQL 汇总占用量并筛选可申请商品，避免模型逐订单调用资格工具。
    @Select(
        """
        SELECT CAST(o.id AS CHAR) AS order_id, o.order_number, CAST(i.id AS CHAR) AS order_item_id,
            i.product_name, CAST(i.quantity-COALESCE(SUM(a.quantity),0) AS SIGNED) AS available_quantity,
            CAST(i.paid_amount-COALESCE(SUM(a.amount),0) AS CHAR) AS remaining_amount
        FROM trade_order o JOIN order_item i ON i.order_id=o.id
        LEFT JOIN aftersale_request a ON a.order_item_id=i.id
            AND a.status IN ('PENDING','APPROVED','RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED')
        WHERE o.user_id=#{userId} AND o.status='COMPLETED'
            AND o.signed_at <= #{now} AND o.signed_at > DATE_SUB(#{now}, INTERVAL 7 DAY)
            AND i.paid_amount > 0
        GROUP BY o.id,o.order_number,o.created_at,i.id,i.product_name,i.quantity,i.paid_amount
        HAVING available_quantity > 0
        ORDER BY o.created_at DESC,o.id DESC,i.id
        LIMIT 11 OFFSET #{offset}
        """
    )
    List<com.example.aftersales.aftersales.domain.vo.EligibleItemsVO.Item> eligibleItems(
        @Param("userId") long userId,
        @Param("now") java.time.LocalDateTime now,
        @Param("offset") long offset
    );

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
