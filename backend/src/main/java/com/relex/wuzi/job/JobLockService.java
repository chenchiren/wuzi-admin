package com.relex.wuzi.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.SysJobLock;
import com.relex.wuzi.mapper.SysJobLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 分布式任务锁（DB 实现）：多实例部署时同一任务名仅一实例执行。
 * 表：sys_job_lock，主键 job_name。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLockService {

    private final SysJobLockMapper lockMapper;

    /** 尝试抢锁；成功返回 true，需在 finally 中 release。 */
    public boolean tryLock(String jobName, int leaseMinutes) {
        LocalDateTime now = LocalDateTime.now();
        SysJobLock row = lockMapper.selectById(jobName);
        if (row == null) {
            row = new SysJobLock();
            row.setJobName(jobName);
            row.setLockedBy(instanceId());
            row.setLockedAt(now);
            row.setExpireAt(now.plusMinutes(Math.max(1, leaseMinutes)));
            try {
                lockMapper.insert(row);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        if (row.getExpireAt() != null && row.getExpireAt().isBefore(now)) {
            row.setLockedBy(instanceId());
            row.setLockedAt(now);
            row.setExpireAt(now.plusMinutes(Math.max(1, leaseMinutes)));
            lockMapper.updateById(row);
            return true;
        }
        return false;
    }

    public void release(String jobName) {
        SysJobLock row = lockMapper.selectById(jobName);
        if (row != null && instanceId().equals(row.getLockedBy())) {
            row.setExpireAt(LocalDateTime.now().minusSeconds(1));
            lockMapper.updateById(row);
        }
    }

    /** 是否由当前实例持有（调试用） */
    public boolean isHeld(String jobName) {
        SysJobLock row = lockMapper.selectOne(new LambdaQueryWrapper<SysJobLock>()
                .eq(SysJobLock::getJobName, jobName));
        return row != null && instanceId().equals(row.getLockedBy())
                && row.getExpireAt() != null && row.getExpireAt().isAfter(LocalDateTime.now());
    }

    private String instanceId() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName() + ":" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "local:" + ProcessHandle.current().pid();
        }
    }
}
