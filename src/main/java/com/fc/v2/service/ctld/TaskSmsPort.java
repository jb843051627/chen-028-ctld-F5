package com.fc.v2.service.ctld;

/**
 * 农时催办主渠道：短信。当场递不出去抛 {@link TaskDispatchEngine.ChannelException}，
 * 原由照原样带出来（回落站内时写回本行事由记要）。口径（先走短信、不通才回落）定死在引擎，不在渠道里。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface TaskSmsPort {

    /** 把这句话术从短信那条渠道递出去；走不通就抛 ChannelException 并带原由 */
    void send(String text);
}
