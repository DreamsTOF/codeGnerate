package com.dream.codegenerate.generator;



import cn.hutool.v7.core.map.Dict;
import cn.hutool.v7.db.config.ConnectionConfig;
import cn.hutool.v7.db.ds.simple.SimpleDataSource;
import cn.hutool.v7.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Map;

/**
 * MyBatis Flex 代码生成器 (集成审计常量自动生成)
 */
public class MyBatisCodeGenerator {

    // 要生成的表名 (按需修改)
    private static final String[] TABLE_NAMES = {"app_version","chat_history","user","access_key"};

    public static void main(String[] args) {
        Dict dict = YamlUtil.loadByPath("application.yml");
        Map<String, Object> dataSourceConfig = dict.getByPath("mybatis-flex.datasource.master");

        if (dataSourceConfig == null) {
            throw new RuntimeException("未找到数据源配置，请检查 application.yml");
        }

        String url = String.valueOf(dataSourceConfig.get("url"));
        String username = String.valueOf(dataSourceConfig.get("username"));
        String password = String.valueOf(dataSourceConfig.get("password"));

        // 读取驱动类名 (适配 MySQL/PG/Oracle 等不同数据库)
        // 建议在 application.yml 中配置 driver-class-name: com.mysql.cj.jdbc.Driver
        Object driverClassObj = dataSourceConfig.get("driver-class-name");
        String driverClassName = driverClassObj != null ? String.valueOf(driverClassObj) : null;

        // 2. 配置数据源 (使用简易 DataSource 替代 HikariCP，彻底解决依赖和冲突问题)
        // 显式传入 driverClassName 以确保驱动被加载
        ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setDriver(driverClassName);
        connectionConfig.setUrl(url);
        connectionConfig.setUser(username);
        connectionConfig.setPass(password);
        DataSource dataSource = new SimpleDataSource(connectionConfig);

        // 3. 创建配置内容
        GlobalConfig globalConfig = createGlobalConfig();

        // 4. 创建并运行代码生成器
        Generator generator = new Generator(dataSource, globalConfig);
        generator.generate();
    }

    public static GlobalConfig createGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();


        // 1. 设置自定义模版路径 (关键)
        // 注意：默认是 ClassPath 加载，所以路径不要带 src/main/resources
        // 如果文件在 src/main/resources/templates/entity.tpl，则这里填 templates/entity.tpl
        // 务必确保 resources 目录已被标记为资源目录，或者已编译到 target/classes 中
        globalConfig.setEntityTemplatePath("templates/entity.tql");

        // 2. 设置根包
        globalConfig.getPackageConfig()
                .setBasePackage("com.dream.codegenerate.result");

        // 3. 策略配置
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                .setLogicDeleteColumn("isDelete");

        // 4. Entity 配置 (Lombok + Swagger)
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setWithSwagger(true) // 开启 Swagger
                .setJdkVersion(21);

        // 5. 其他层级配置
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();
        globalConfig.enableService();
        globalConfig.enableServiceImpl();
        globalConfig.enableController();
        globalConfig.enableTableDef();

        globalConfig.getJavadocConfig()
                .setAuthor("dream")
                .setSince("");

        return globalConfig;
    }
}

