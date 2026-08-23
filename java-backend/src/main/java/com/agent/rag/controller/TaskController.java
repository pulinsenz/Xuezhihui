package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.resp.TaskVO;
import com.agent.rag.service.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 长任务接口：前端轮询任务状态，规避长耗时任务（文档向量化）的 HTTP 超时
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private TaskService taskService;

    /**
     * 查询任务状态（需登录，归属校验：只能查自己的任务）
     */
    @GetMapping("/{id}")
    public Result<TaskVO> get(@PathVariable String id) {
        return Result.success(taskService.getTask(id));
    }
}
