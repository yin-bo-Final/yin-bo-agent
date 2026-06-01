package com.yinbo.agent.config;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

@Component
// MyBatis-Plus 创建时间和更新时间自动填充器。
public class MybatisPlusAutoFillConfig implements MetaObjectHandler {

    @Override
    // 插入数据时填充 createdAt 和 updatedAt。
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    // 更新数据时刷新 updatedAt。
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
