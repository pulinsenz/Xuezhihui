package com.agent.rag.dto.resp;

import com.agent.rag.entity.User;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息（管理端，不含密码）
 *
 * @author pulinsenz
 */
@Data
public class AdminUserVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    /**
     * 是否删除：0=正常、1=已删除
     */
    private Integer isDelete;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static AdminUserVO from(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        vo.setUserProfile(user.getUserProfile());
        vo.setUserRole(user.getUserRole());
        vo.setIsDelete(user.getIsDelete());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
