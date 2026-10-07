package com.fc.v2.service.ctld.notify;

import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyOutcome;

/**
 * 短信口子。库里没有接短信服务商时，由 {@link LogTaskSmsSender} 兜底：当场回一句走不通的原由，
 * 好让主渠道按第 7 条就地回落站内消息。真接了服务商，另立 bean 替下兜底即可，调度本体不动。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface TaskSmsSender {

    /** 当场递一句；递不出去回 fail 并带上那句原由，不许抛出去撂下整趟 */
    NotifyOutcome send(String text);
}
