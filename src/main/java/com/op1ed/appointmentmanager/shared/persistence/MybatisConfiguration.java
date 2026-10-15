package com.op1ed.appointmentmanager.shared.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Service 直接调用 Mapper；标注 @Mapper 的接口由 MyBatis 实现并执行 SQL。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.op1ed.appointmentmanager", annotationClass = Mapper.class)
public class MybatisConfiguration {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
