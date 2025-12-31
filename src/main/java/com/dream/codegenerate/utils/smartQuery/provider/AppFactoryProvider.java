package com.dream.codegenerate.utils.smartQuery.provider;

import com.dream.codegenerate.model.entity.App;
import com.dream.codegenerate.utils.smartQuery.EntityFactoryProvider;
import com.dream.codegenerate.utils.smartQuery.SmartQueryAssembler.EntityFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * App 实体工厂提供者
 * <p>
 * 自动注册到 SmartQueryConfig，提供极速对象构建能力。
 * </p>
 */
@Component
public class AppFactoryProvider implements EntityFactoryProvider<App> {

    @Override
    public Class<App> getEntityClass() {
        return App.class;
    }

    @Override
    public EntityFactory<App> getFactory() {
        return (row, prefix) -> {
            App app = new App();
            if (prefix == null || prefix.isEmpty()) {
                // 根对象构建 (无前缀)
                app.setId((Long) row.get("id"));
                app.setAppName((String) row.get("appName"));
                app.setCover((String) row.get("cover"));
                app.setInitPrompt((String) row.get("initPrompt"));
                app.setCodeGenType((String) row.get("codeGenType"));
                app.setDeployKey((String) row.get("deployKey"));
                app.setDeployedTime((LocalDateTime) row.get("deployedTime"));
                app.setPriority((Integer) row.get("priority"));
                app.setUserId((Long) row.get("userId"));
                app.setEditTime((LocalDateTime) row.get("editTime"));
                app.setCreateTime((LocalDateTime) row.get("createTime"));
                app.setUpdateTime((LocalDateTime) row.get("updateTime"));
                app.setIsDelete((Integer) row.get("isDelete"));
                app.setCurrentVersion((Long) row.get("currentVersion"));
            } else {
                // 嵌套对象构建 (带前缀)
                app.setId((Long) row.get(prefix + "id"));
                app.setAppName((String) row.get(prefix + "appName"));
                app.setCover((String) row.get(prefix + "cover"));
                app.setInitPrompt((String) row.get(prefix + "initPrompt"));
                app.setCodeGenType((String) row.get(prefix + "codeGenType"));
                app.setDeployKey((String) row.get(prefix + "deployKey"));
                app.setDeployedTime((LocalDateTime) row.get(prefix + "deployedTime"));
                app.setPriority((Integer) row.get(prefix + "priority"));
                app.setUserId((Long) row.get(prefix + "userId"));
                app.setEditTime((LocalDateTime) row.get(prefix + "editTime"));
                app.setCreateTime((LocalDateTime) row.get(prefix + "createTime"));
                app.setUpdateTime((LocalDateTime) row.get(prefix + "updateTime"));
                app.setIsDelete((Integer) row.get(prefix + "isDelete"));
                app.setCurrentVersion((Long) row.get(prefix + "currentVersion"));
            }
            return app;
        };
    }
}
