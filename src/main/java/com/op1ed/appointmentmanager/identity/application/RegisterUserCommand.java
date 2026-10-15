package com.op1ed.appointmentmanager.identity.application;

import com.op1ed.appointmentmanager.shared.error.BusinessInputs;

/**
 * 注册用例的输入。Request 表达 HTTP JSON，Command 表达应用服务需要的数据。
 * 与创建医生的 Command 一样，构造成功就意味着输入已通过这里的基础校验。
 *
 * @param username 登录用户名，校验时去首尾空格并转小写
 * @param displayName 显示姓名，与用于登录的用户名分开保存
 * @param password 本次提交的原始密码；服务层散列后才存入数据库
 */
public record RegisterUserCommand(String username, String displayName, String password) {
    // record 的紧凑构造方法：先校验、整理参数，Java 随后将这些参数赋给同名字段。
    public RegisterUserCommand {
        // 身份模块自己的用户名规则：3～50 位字母、数字、下划线或连字符。
        username = IdentityInputs.username(username);
        displayName = BusinessInputs.requireName(displayName, "显示姓名");
        // 校验长度等条件，同时保留密码的大小写和空格；此处还没有执行密码散列。
        password = IdentityInputs.password(password);
    }

    // record 默认的 toString 会列出字段，这里覆盖它，避免打印对象时把密码带进日志。
    @Override
    public String toString() {
        return "RegisterUserCommand[username=" + username + ", password=[REDACTED]]";
    }
}
