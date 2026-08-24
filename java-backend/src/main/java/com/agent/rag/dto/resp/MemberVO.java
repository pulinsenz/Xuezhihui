package com.agent.rag.dto.resp;

import com.agent.rag.entity.User;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库协作者信息
 *
 * @author pulinsenz
 */
@Data
public class MemberVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userAccount;

    private String userName;

    private String userAvatar;

    /**
     * 加入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static MemberVO from(User user, LocalDateTime createTime) {
        MemberVO vo = new MemberVO();
        vo.setUserId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        vo.setCreateTime(createTime);
        return vo;
    }
}
