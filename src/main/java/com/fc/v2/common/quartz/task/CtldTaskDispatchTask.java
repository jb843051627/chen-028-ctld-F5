package com.fc.v2.common.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchReply;

import java.util.Date;

/**
 * 农时催办「早班翻单」定时工种。
 * 石英钟每天 06:30 唤这一口即可；闸由后端引擎自己把（06:30 起 09:00 收），
 * 早叫、晚叫、夜里误叫都只回「本轮未动单」，不穿透静默口子。
 * 触发仍只认 service 那一个口，与页面手工按的是同一条通道、同一套筛法。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Component("ctldTaskDispatchTask")
public class CtldTaskDispatchTask {

    private static final Logger logger = LoggerFactory.getLogger(CtldTaskDispatchTask.class);

    @Autowired
    private ITCtldTaskDispatchService taskDispatchService;

    /** 早班那一趟：由程序来翻，落到谁名下归谁去做 */
    public void runEarly() {
        TaskDispatchReply reply = taskDispatchService.runOnce(new Date());
        if (!reply.isGateOpen()) {
            logger.info("早班翻单：{}", reply.getResultMsg());
            return;
        }
        logger.info("早班翻单完毕{}：挑中{}行，递出{}行，未成{}行",
                reply.isReplayed() ? "（照头一趟回话）" : "",
                reply.getPickedRows(), reply.getSentRows(), reply.getFailedRows());
    }
}
