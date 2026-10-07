package com.fc.v2.service.ctld;

import java.util.Date;

/**
 * 农时催办《安排单》早班「翻单」Service（新的一条，老 TCtldTaskItemService 那套冻住，不在它本体上动刀）。
 * 触发口只此一个：窗口、届期、去重、渠道回落、幂等、趟尾三数的裁决全在 {@link TaskDispatchEngine} 一处，
 * 本层只照裁决落库、传话，不先把数字端出来再回头拼依据，也不拉另一轮逐行清点做对冲。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITCtldTaskDispatchService {

    /**
     * 跑一趟早班翻单。
     *
     * @param roundNo 趟号；同一趟号第二回照头一趟回话（幂等）。传 null 按触发当日生成「早班一趟一号」
     * @param at      触发那一刻；传 null 取当下。窗口开不开、届期、提前日全按这一刻判
     */
    TaskDispatchResult runEarlyShift(String roundNo, Date at);

    /** 照趟号回头复述头一趟（三数只信趟账落下的那份）；查无此趟抛错 */
    TaskDispatchResult replay(String roundNo);
}
