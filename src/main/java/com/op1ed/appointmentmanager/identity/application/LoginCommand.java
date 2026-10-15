package com.op1ed.appointmentmanager.identity.application;

/**
 * 登录用例的输入，通常由 LoginRequest.toCommand() 创建。
 * 这里检查输入格式；用户名是否存在、密码是否正确由 IdentityService.login 查询并判断。
 */
public record LoginCommand(String username, String password) {
    public LoginCommand {
        // 登录与注册复用相同的用户名整理规则，确保查找的是同一种格式的用户名。
        username = IdentityInputs.username(username);
        // 密码保留原样；如果改大小写或裁剪空格，就可能改变用户实际提交的密码。
        password = IdentityInputs.password(password);
    }

    // 覆盖 record 自动生成的字符串表示，隐藏密码字段。
    @Override
    public String toString() {
        return "LoginCommand[username=" + username + ", password=[REDACTED]]";
    }
}
