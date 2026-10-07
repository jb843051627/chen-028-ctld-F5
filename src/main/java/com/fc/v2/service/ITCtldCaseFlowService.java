package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TCtldCaseFlow;
import com.fc.v2.model.auto.TCtldCaseFlowTrace;
import com.fc.v2.service.ctld.CaseFlowResult;
import com.fc.v2.service.ctld.CaseFlowReplayer;
import com.fc.v2.service.ctld.CaseFlowResult.OpenCmd;

/**
 * 超标地块纳入受限与解除办件流转单 Service接口。
 * 五问（当下阶、放不放行、封口吞不吞按语、同档二次按语怎么落、倒捋数几阶）只由本件后端这一处回答。
 * 老入口 advance/rollback/updateContent/remove 的入参出参一字不改，只把走法改正；
 * 想要另一种挪法，另起新方法并列去担，不回头动老签名。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITCtldCaseFlowService {

    /** 按主键查询单据 */
    TCtldCaseFlow selectTCtldCaseFlowById(Long id);

    /** 列表查询（流转台账） */
    List<TCtldCaseFlow> selectTCtldCaseFlowList(QueryWrapper<TCtldCaseFlow> queryWrapper);

    /**
     * 没闭上那一类的条数：起一条加一、闭一条减一。只此一处数，前端不另起数法。
     */
    long countOpen();

    /**
     * 起一件：四要件（地块码、分区代号、镉实测值、措施名目）齐身才起得来，缺样逐一点名。
     * 一处地块任何一刻只许一条没闭上的件；同 reqNo 重递幂等。
     */
    CaseFlowResult open(OpenCmd cmd);

    /** 本档落一句按语；同档第二次投来不采信（不盖头一句、不另存副本） */
    CaseFlowResult note(Long id, String note, String reqNo);

    /** 纳入受限档核收：可/不可；不记「可」不许往措施核查开工 */
    CaseFlowResult acceptLimit(Long id, boolean pass, String reqNo);

    /** 解除复核门槛：见证岗落一笔，两名须不同岗 */
    CaseFlowResult witness(Long id, String witnessPost, String reqNo);

    /** 解除复核门槛：措施核验凭证串写回本行 */
    CaseFlowResult voucher(Long id, String voucherNo, String reqNo);

    /** 视同收口：递件人本人收回反映，不必再往前挪 */
    CaseFlowResult withdraw(Long id, String reqNo);

    /** 视同收口：所属分区已从分区册并走撤出，不必再往前挪 */
    CaseFlowResult closeByZoneMerged(Long id, String reqNo);

    /**
     * 推进一档（老签名一字不改：remark 作递件笔号用，空着即不走幂等）。
     * 一次只跨一档，门槛不放行不采信，整条留原地；跨进末档即闭上。
     */
    TCtldCaseFlow advance(Long id, String remark);

    /**
     * 回退一档（老签名一字不改：remark 作递件笔号用）。
     * 一次只收一档，被收档那句按语照原文转进追溯串末尾；已在头一档没得再收。
     */
    TCtldCaseFlow rollback(Long id, String remark);

    /** 并列新入口：推进一档，回话把挡门的那一条点清楚（与 advance 同走一处口径） */
    CaseFlowResult stepForward(Long id, String reqNo);

    /** 并列新入口：回退一档，回话说明被收按语去向（与 rollback 同走一处口径） */
    CaseFlowResult stepBack(Long id, String reqNo);

    /** 修改内容：已封住不得再改，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除（整条抽回）：已封住不得抽回，被拒返回 false */
    boolean remove(Long id);

    /** 取本件变更履历，按时序（同刻按 seq 分先后，两笔不并一条） */
    List<TCtldCaseFlowTrace> trace(Long id);

    /**
     * 只读回放：从末档一关一关往回倒着演，演完只看结果不写回；
     * 不动当下任何栏位，不给封住的行重新张口。缺第几档、缺哪一栏逐一点名。
     */
    CaseFlowReplayer.Result replay(Long id);
}
