package com.fc.v2.service.ctld;

import java.util.Date;

/**
 * 农时催办「早班翻单」Service（新的一条；老的 ITCtldTaskItemService 收什么回什么冻住，不在它身上动刀）。
 * 触发口只此一个：屏上不得先把数字端出来再回头拼依据；闸外连按两回，回话一字不差「本轮未动单」。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITCtldTaskDispatchService {

    /**
     * 翻一趟早班单。
     * 06:30-09:00 之外只回「本轮未动单」，单上一行不起皱；同一日第二回照头一趟回话（幂等）。
     *
     * @param at 当口时刻（由触发口带入，引擎不自取时刻，便于与筛行同一把尺对账）
     */
    TaskDispatchReply runOnce(Date at);
}
