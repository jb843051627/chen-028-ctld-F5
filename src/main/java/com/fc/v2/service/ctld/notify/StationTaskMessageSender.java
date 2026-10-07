package com.fc.v2.service.ctld.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.service.ITSysNoticeService;
import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyOutcome;

/**
 * 站内消息口子（备胎渠道）：把那句话落一条站内公告（t_sys_notice）。
 * 当场落不进库就回 fail 带原由，由调度记「两条皆不通」；绝不抛出去撂下整趟。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Component
public class StationTaskMessageSender {

    private static final Logger logger = LoggerFactory.getLogger(StationTaskMessageSender.class);

    private final ITSysNoticeService noticeService;

    public StationTaskMessageSender(ITSysNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 当场递一句站内消息；title 固定名目，content 落话术原话 */
    public NotifyOutcome send(String text) {
        try {
            TSysNotice n = new TSysNotice();
            n.setTitle("农时催办（短信不通回落站内）");
            n.setContent(text);
            n.setType(1);
            int rows = noticeService.insertTSysNotice(n);
            if (rows > 0) {
                return NotifyOutcome.ok();
            }
            return NotifyOutcome.fail("站内消息落库 0 行");
        } catch (RuntimeException e) {
            logger.error("站内消息当场递不出去：{}", e.getMessage(), e);
            return NotifyOutcome.fail("站内消息落库出错：" + e.getMessage());
        }
    }
}
