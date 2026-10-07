package com.fc.v2.common.conf;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.service.ctld.TaskDispatchEngine.ChannelException;
import com.fc.v2.service.ctld.TaskInnerPort;
import com.fc.v2.service.ctld.TaskSmsPort;

/**
 * 农时催办翻单两条渠道的默认落法。
 * 口径（先短信、当场不通就地回落站内、两条都不通才记发不成）定死在 {@link com.fc.v2.service.ctld.TaskDispatchEngine}，
 * 这里只给两条渠道各自一个能换的口：真接短信网关时各起一个自己的 {@link TaskSmsPort} Bean 即可顶掉默认。
 * 默认短信口未接网关（不谎称已发），一律抛原由回落；默认站内口写进 t_sys_notice 公告表。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Configuration
public class CtldTaskChannelConfig {

    @Bean
    @ConditionalOnMissingBean(TaskSmsPort.class)
    public TaskSmsPort defaultTaskSmsPort() {
        return text -> {
            throw new ChannelException("短信网关未接通（默认口，未接真实网关）");
        };
    }

    @Bean
    @ConditionalOnMissingBean(TaskInnerPort.class)
    public TaskInnerPort defaultTaskInnerPort(TSysNoticeMapper noticeMapper) {
        return text -> {
            try {
                TSysNotice notice = new TSysNotice();
                notice.setTitle("农时催办");
                notice.setContent(text);
                notice.setType(0);
                noticeMapper.insert(notice);
            } catch (RuntimeException e) {
                // 站内也写不进去：把原由抛回去，由引擎记「两条都不通」
                throw new ChannelException("站内消息落库失败：" + e.getMessage());
            }
        };
    }
}
