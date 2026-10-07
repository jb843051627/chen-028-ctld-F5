package com.fc.v2.service.ctld;

/**
 * 农时催办备胎渠道：站内消息。只有主渠道（短信）当场走不通时才由引擎回落到此；
 * 当场递不出去抛 {@link TaskDispatchEngine.ChannelException}，两条都不通才记发不成。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface TaskInnerPort {

    /** 把这句话术从站内消息那条渠道递出去；走不通就抛 ChannelException 并带原由 */
    void send(String text);
}
