package com.fc.v2.service.ctld.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyOutcome;

/**
 * 短信口子兜底实现：本库未接短信服务商，当场按「走不通」回话并写明原由，
 * 由调度按第 7 条就地回落站内消息——绝不假装递出，也绝不抛错撂下整趟。
 * 真接了服务商：另写一个 {@link TaskSmsSender} 实现注册为 bean 并标 {@code @Primary} 替下本兜底，调度本体不动。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Component
public class LogTaskSmsSender implements TaskSmsSender {

    private static final Logger logger = LoggerFactory.getLogger(LogTaskSmsSender.class);

    @Override
    public NotifyOutcome send(String text) {
        logger.warn("短信通道未接通（未配置短信服务商），本句按走不通论，回落站内消息：{}", text);
        return NotifyOutcome.fail("短信通道未接通（未配置短信服务商）");
    }
}
