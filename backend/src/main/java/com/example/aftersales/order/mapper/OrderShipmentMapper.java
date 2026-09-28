package com.example.aftersales.order.mapper;

import com.example.aftersales.order.domain.po.OrderShipmentPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderShipmentMapper {
    List<OrderShipmentPO> findByOwnedOrder(@Param("userId") long userId, @Param("orderId") long orderId);
}
