package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_job_lock")
public class SysJobLock {
    @TableId(type = IdType.INPUT)
    private String jobName;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime expireAt;
}
