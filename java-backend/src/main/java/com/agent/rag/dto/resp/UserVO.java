package com.agent.rag.dto.resp;

import com.agent.rag.entity.User;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息（不含密码）
 *
 * @author pulinsenz
 */
@Data
public class UserVO implements Serializable {

    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        vo.setUserProfile(user.getUserProfile());
        vo.setUserRole(user.getUserRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
