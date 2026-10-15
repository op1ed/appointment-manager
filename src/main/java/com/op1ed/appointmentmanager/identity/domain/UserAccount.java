package com.op1ed.appointmentmanager.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_accounts")
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String displayName;

    private String passwordHash;

    private AccountRole role;

    protected UserAccount() {
    }

    public UserAccount(String username, String displayName, String passwordHash, AccountRole role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
}
