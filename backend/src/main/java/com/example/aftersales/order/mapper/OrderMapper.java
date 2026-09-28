package com.example.aftersales.order.mapper;

import com.example.aftersales.order.domain.OrderStatus;
import com.example.aftersales.order.domain.po.OrderPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** userId 必须来自认证上下文，分页 offset 由校验后的 page/size 使用 long 计算。 */
@Mapper
public interface OrderMapper {
    OrderPO findOwnedById(@Param("userId") long userId, @Param("orderId") long orderId);

    List<OrderPO> findPageByUser(@Param("userId") long userId,
            @Param("status") OrderStatus status, @Param("orderNumber") String orderNumber,
            @Param("offset") long offset, @Param("limit") int limit);

    long countByUser(@Param("userId") long userId,
            @Param("status") OrderStatus status, @Param("orderNumber") String orderNumber);
}
