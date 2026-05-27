package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mianshitong.project.enum_.UserRole;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xz_user")
public class UserPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private String targetPosition;
    private Integer points;
    private Boolean enabled;
    private UserRole role;
    private LocalDateTime createdAt;
}
