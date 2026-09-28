package com.example.aftersales;

import com.example.aftersales.identity.domain.po.UserSessionPO;
import com.example.aftersales.identity.mapper.UserMapper;
import com.example.aftersales.identity.mapper.UserSessionMapper;
import com.example.aftersales.order.domain.OrderStatus;
import com.example.aftersales.order.mapper.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/** 仅显式提供隔离 MySQL 时运行。Flyway 建表，测试数据每次回滚，不启动 HTTP 服务器。 */
@EnabledIfEnvironmentVariable(named = "MAPPER_TEST_URL", matches = ".+")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${MAPPER_TEST_URL}",
        "spring.datasource.username=${MAPPER_TEST_USERNAME}",
        "spring.datasource.password=${MAPPER_TEST_PASSWORD}"
})
@Transactional
class MapperIntegrationTest {
    private static final long OWNER = 900001L;
    private static final long OTHER = 900002L;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 28, 3, 0, 0, 123_000_000);
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired UserSessionMapper sessions;
    @Autowired OrderMapper orders;
    @Autowired OrderItemMapper items;
    @Autowired OrderShipmentMapper shipments;
    @Autowired ShipmentEventMapper events;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO app_user(id,username,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,?)",
                OWNER, "mapper_customer", "test-only-hash", "测试买家", "CUSTOMER", true);
        jdbc.update("INSERT INTO app_user(id,username,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,?)",
                OTHER, "mapper_disabled", "test-only-hash", "停用用户", "STAFF", false);
        for (long id = 910001; id <= 910004; id++) {
            jdbc.update("INSERT INTO trade_order(id,user_id,order_number,status,paid_amount,created_at,paid_at) VALUES(?,?,?,?,?,?,?)",
                    id, id == 910004 ? OTHER : OWNER, "MAPPER-" + id,
                    id == 910003 ? "SHIPPED" : "PAID", new BigDecimal("19.90"), TIME, TIME);
            jdbc.update("INSERT INTO order_item(id,order_id,sku_id,product_name,specification,quantity,paid_amount) VALUES(?,?,?,?,?,?,?)",
                    id + 10000, id, 501L, "测试商品", "黑色", 2, new BigDecimal("19.90"));
        }
        jdbc.update("INSERT INTO order_shipment(id,order_id,carrier,tracking_number,status,shipped_at) VALUES(?,?,?,?,?,?)",
                930001L, 910003L, "模拟物流", "MAPPER-TRACK", "IN_TRANSIT", TIME.plusHours(1));
        for (long id : new long[] {940002L, 940001L}) {
            jdbc.update("INSERT INTO shipment_event(id,shipment_id,occurred_at,description) VALUES(?,?,?,?)",
                    id, 930001L, TIME.plusHours(2), "模拟轨迹" + id);
        }
    }

    @Test
    void mapsUserAndPreservesCaseAndDisabledState() {
        var user = users.findByUsername("mapper_customer");
        assertThat(user.getId()).isEqualTo(OWNER);
        assertThat(user.getDisplayName()).isEqualTo("测试买家");
        assertThat(user.getPasswordHash()).isEqualTo("test-only-hash");
        assertThat(user.getEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(users.findByUsername("MAPPER_CUSTOMER")).isNull();
        assertThat(users.findById(OTHER).getEnabled()).isFalse();
        assertThat(users.findById(OTHER).getRole()).isEqualTo("STAFF");
        assertThat(users.findById(Long.MAX_VALUE)).isNull();
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
    }

    @Test
    void sessionKeyRoundTripIdempotentRevocationAndBoundedCleanup() {
        var session = new UserSessionPO();
        byte[] hash = new byte[32];
        hash[0] = 42;
        session.setUserId(OWNER);
        session.setTokenHash(hash);
        session.setCreatedAt(TIME);
        session.setExpiresAt(TIME.plusHours(2));
        assertThat(sessions.insert(session)).isEqualTo(1);
        assertThat(session.getId()).isPositive();
        var saved = sessions.findByTokenHash(hash);
        assertThat(saved.getId()).isEqualTo(session.getId());
        assertThat(saved.getTokenHash()).containsExactly(hash);
        assertThat(saved.getCreatedAt()).isEqualTo(TIME);
        assertThat(saved.getRevokedAt()).isNull();
        assertThatThrownBy(() -> sessions.insert(session)).isInstanceOf(DuplicateKeyException.class);
        assertThat(sessions.revokeByTokenHash(hash, TIME.plusMinutes(1))).isEqualTo(1);
        assertThat(sessions.revokeByTokenHash(hash, TIME.plusMinutes(2))).isZero();
        assertThat(sessions.findByTokenHash(hash).getRevokedAt()).isEqualTo(TIME.plusMinutes(1));
        assertThat(sessions.deleteExpired(TIME.plusHours(1), 1)).isZero();
        assertThat(sessions.deleteExpired(TIME.plusHours(2), 1)).isEqualTo(1);
        assertThat(sessions.findByTokenHash(hash)).isNull();

        // 两个尚未撤销的过期会话，验证过期时不能注销且清理尊重批大小。
        hash[0] = 43;
        sessions.insert(session);
        hash[0] = 44;
        sessions.insert(session);
        assertThat(sessions.revokeByTokenHash(hash, TIME.plusHours(2))).isZero();
        assertThat(sessions.deleteExpired(TIME.plusHours(2), 1)).isEqualTo(1);
        assertThat(sessions.findByTokenHash(hash)).isNotNull();
        assertThat(sessions.deleteExpired(TIME.plusHours(2), 1)).isEqualTo(1);
    }

    @Test
    void ownedOrdersFiltersAndStablePagination() {
        assertThat(orders.countByUser(OWNER, null, null)).isEqualTo(3);
        assertThat(orders.findPageByUser(OWNER, null, null, 0, 2))
                .extracting("id").containsExactly(910003L, 910002L);
        assertThat(orders.findPageByUser(OWNER, null, null, 2, 2))
                .extracting("id").containsExactly(910001L);
        assertThat(orders.findPageByUser(OWNER, null, null, 20, 2)).isEmpty();
        assertThat(orders.countByUser(OWNER, OrderStatus.PAID, null)).isEqualTo(2);
        assertThat(orders.findPageByUser(OWNER, OrderStatus.PAID, "MAPPER-910001", 0, 20))
                .extracting("id").containsExactly(910001L);
        assertThat(orders.countByUser(OWNER, null, "MAPPER-910004")).isZero();
        assertThat(orders.findPageByUser(OWNER, null, "x' OR 1=1 --", 0, 20)).isEmpty();
        var order = orders.findOwnedById(OWNER, 910001);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(new BigDecimal("19.90"));
        assertThat(order.getCreatedAt()).isEqualTo(TIME);
        assertThat(order.getSignedAt()).isNull();
        assertThat(orders.findOwnedById(OTHER, 910001)).isNull();
    }

    @Test
    void childQueriesEnforceOwnershipAndHandleEmptyCollections() {
        var item = items.findByOwnedOrder(OWNER, 910001).getFirst();
        assertThat(item.getSkuId()).isEqualTo(501L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getPaidAmount()).isEqualTo(new BigDecimal("19.90"));
        assertThat(items.findByOwnedOrder(OTHER, 910001)).isEmpty();
        assertThat(items.findByOwnedOrders(OWNER, List.of(910001L, 910004L)))
                .extracting("orderId").containsExactly(910001L);
        assertThat(items.findByOwnedOrders(OWNER, List.of())).isEmpty();
        assertThat(items.findByOwnedOrders(OWNER, null)).isEmpty();
        var shipment = shipments.findByOwnedOrder(OWNER, 910003).getFirst();
        assertThat(shipment.getTrackingNumber()).isEqualTo("MAPPER-TRACK");
        assertThat(shipment.getShippedAt()).isEqualTo(TIME.plusHours(1));
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipments.findByOwnedOrder(OWNER, 910001)).isEmpty();
        assertThat(shipments.findByOwnedOrder(OTHER, 910003)).isEmpty();
        assertThat(events.findByOwnedShipment(OWNER, 930001))
                .extracting("id").containsExactly(940001L, 940002L);
        assertThat(events.findByOwnedShipment(OWNER, 930001).getFirst().getOccurredAt())
                .isEqualTo(TIME.plusHours(2));
        assertThat(events.findByOwnedShipment(OTHER, 930001)).isEmpty();
    }
}
