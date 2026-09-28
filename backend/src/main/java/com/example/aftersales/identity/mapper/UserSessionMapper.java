package com.example.aftersales.identity.mapper;

import com.example.aftersales.identity.domain.po.UserSessionPO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {
    /** 写入成功后回填自增 id；时间必须使用 UTC，摘要必须为 32 字节。 */
    int insert(UserSessionPO session);

    /** 包含已撤销和已过期记录，供 Service 分别处理认证和重复注销。 */
    UserSessionPO findByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 只撤销尚有效的会话；返回 0 不能直接推断为不存在。 */
    int revokeByTokenHash(@Param("tokenHash") byte[] tokenHash, @Param("now") LocalDateTime now);

    /** 分批清理过期记录，batchSize 应由内部清理任务提供正整数。 */
    int deleteExpired(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);
}
