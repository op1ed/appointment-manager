package com.op1ed.appointmentmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 全局阅读第一批：整个 Java 应用的启动入口。
 *
 * <p>先读根目录 pom.xml，认识项目使用的库；再看这里，最后看 application.properties。
 * 全部阅读路线见 docs/project-reading-guide.md。
 *
 * <p>启动后由 Spring 组装项目中的组件：Controller 接收请求，Service 执行业务，
 * Repository 访问数据库。后续批次会用具体请求把这些组件串起来。
 */
// @SpringBootApplication 启用 Spring Boot 自动配置，并扫描当前包及其子包中的组件。
// 本类位于 com.op1ed.appointmentmanager，doctor、appointment、identity、agent 都在它的子包中。
// exclude 排除默认用户服务的自动配置：本项目使用 identity 模块中的账号与 Bearer 令牌登录。
// 访问权限的具体配置在 identity.web.security.SecurityConfiguration，后面的权限批次再读。
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AppointmentManagerApplication {

    // Java 从 main 方法进入；SpringApplication.run 负责启动 Spring Boot 应用。
    // 启动过程会加载配置、创建和关联组件，并按本项目的配置连接 MySQL、执行数据库迁移等。
    // 请求处理方法写在各个 Controller 中，启动后由框架根据 HTTP 路径分派调用。
    public static void main(String[] args) {
        SpringApplication.run(AppointmentManagerApplication.class, args);
    }
}
