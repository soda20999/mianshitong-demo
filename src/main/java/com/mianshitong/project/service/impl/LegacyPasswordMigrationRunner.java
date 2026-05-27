package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.entity.po.UserPo;
import com.mianshitong.project.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyPasswordMigrationRunner implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth-security.auto-migrate-legacy-passwords:true}")
    private boolean autoMigrateLegacyPasswords;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoMigrateLegacyPasswords) {
            return;
        }
        List<UserPo> users = userMapper.selectList(new LambdaQueryWrapper<>());
        for (UserPo user : users) {
            String password = user.getPassword();
            if (password == null || password.isBlank() || isBcryptHash(password)) {
                continue;
            }
            user.setPassword(passwordEncoder.encode(password));
            userMapper.updateById(user);
        }
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
