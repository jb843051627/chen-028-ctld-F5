package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.Date;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.mapper.auto.TCtldTaskCalendarOffMapper;
import com.fc.v2.mapper.auto.TCtldTaskDedupMapper;
import com.fc.v2.mapper.auto.TCtldTaskItemMapper;
import com.fc.v2.mapper.auto.TCtldTaskRoundMapper;
import com.fc.v2.model.auto.TCtldTaskCalendarOff;
import com.fc.v2.model.auto.TCtldTaskDedup;
import com.fc.v2.model.auto.TCtldTaskItem;
import com.fc.v2.model.auto.TCtldTaskRound;
import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchEngine;
import com.fc.v2.service.ctld.TaskDispatchEngine.Gate;
import com.fc.v2.service.ctld.TaskDispatchEngine.ItemRow;
import com.fc.v2.service.ctld.TaskDispatchEngine.RoundResult;
import com.fc.v2.service.ctld.TaskDispatchEngine.RowOutcome;
import com.fc.v2.service.ctld.TaskDispatchResult;
import com.fc.v2.service.ctld.TaskInnerPort;
import com.fc.v2.service.ctld.TaskSmsPort;

/**
 * 农时催办早班「翻单」Service 实现。
 * 十条规矩的裁决全部出自 {@link TaskDispatchEngine}；本层只：读册 → 交给引擎 → 照裁决同事务落
 *（趟账 + 条目两处记号 + 去重账），回话三数只信趟账当场落下的那份。
 * 同一趟号第二回：照头一趟回话（幂等），不重发、不动一行、不另落一趟。
 * 闸门外：不读不发不落趟账，固定回「本轮未动单」（同一刻两回一字不差）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class TCtldTaskDispatchServiceImpl implements ITCtldTaskDispatchService {

    /** 全县按这一个时区当口（库内 datetime 也是 GMT+8） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @javax.annotation.Resource
    private TCtldTaskItemMapper itemMapper;
    @javax.annotation.Resource
    private TCtldTaskDedupMapper dedupMapper;
    @javax.annotation.Resource
    private TCtldTaskRoundMapper roundMapper;
    @javax.annotation.Resource
    private TCtldTaskCalendarOffMapper calendarOffMapper;
    @javax.annotation.Resource
    private TaskSmsPort smsPort;
    @javax.annotation.Resource
    private TaskInnerPort innerPort;

    private final TransactionTemplate transactionTemplate;

    public TCtldTaskDispatchServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================ 唯一触发口 ============================

    @Override
    public TaskDispatchResult runEarlyShift(String roundNo, Date at) {
        java.util.Date moment = at != null ? at : new java.util.Date();
        LocalDateTime ldt = moment.toInstant().atZone(ZONE).toLocalDateTime();

        // 已落过的同趟号：照头一趟回话，不重算、不重发（幂等回放）
        if (roundNo != null && !roundNo.trim().isEmpty()) {
            TCtldTaskRound existed = findRound(roundNo.trim());
            if (existed != null) {
                return toResult(existed, true);
            }
        }

        // 第 1 条：闸门外只回一句话，单上一行都不许起皱——不挑不发不落趟账
        if (!TaskDispatchEngine.gateOpen(ldt.toLocalTime())) {
            TaskDispatchResult closed = new TaskDispatchResult();
            closed.setRoundNo(roundNo);
            closed.setGateOpen(false);
            closed.setRunAt(moment);
            closed.setReplyText(TaskDispatchEngine.GATE_CLOSED_REPLY);
            return closed;
        }

        String useRoundNo = (roundNo == null || roundNo.trim().isEmpty())
                ? "ES" + ldt.toString().replace("-", "").replace(":", "").replace(".", "").replace("T", "")
                : roundNo.trim();

        // 第 4、7 条衔接：本趟翻单不碰发不成（2）的行；「次日条件仍够还得再试」——
        // 翻趟前只把上一日及更早记为发不成的行重新搁回（2→0），当日趟内重跑不重抛；
        // 搁回后够不够日子仍由引擎同一把尺重判，不够不会被挑出。
        rearmFailedRows(ldt.toLocalDate());

        // 读册：老条目与今年新条目同一条通道、同一套筛法，全量交给引擎，不因存得早另开一门
        List<TCtldTaskItem> items = itemMapper.selectList(new QueryWrapper<TCtldTaskItem>()
                .eq("del_flag", 0).orderByAsc("id"));
        final Map<Long, TCtldTaskItem> itemMap = new HashMap<>();
        List<ItemRow> rows = new ArrayList<>();
        for (TCtldTaskItem it : items) {
            itemMap.put(it.getId(), it);
            rows.add(toRow(it));
        }
        final Set<LocalDate> offDays = loadOffDays();

        Gate gate = new Gate() {
            @Override
            public boolean isOffDay(LocalDate day) {
                return offDays.contains(day);
            }

            @Override
            public boolean alreadySentToday(String plotCode, String measureName, LocalDate day) {
                // 去重账只认地块码+事由名目+一日三段，分区代号不参与
                QueryWrapper<TCtldTaskDedup> q = new QueryWrapper<TCtldTaskDedup>()
                        .eq("del_flag", 0)
                        .eq("plot_code", plotCode)
                        .eq("measure_name", measureName)
                        .eq("dedup_day", java.sql.Date.valueOf(day))
                        .last("limit 1");
                return dedupMapper.selectCount(q) > 0;
            }

            @Override
            public void sendSms(String text) {
                smsPort.send(text);
            }

            @Override
            public void sendInner(String text) {
                innerPort.send(text);
            }
        };

        // 十条规矩只在引擎这一处裁决；三数与逐行去向同一次算定
        RoundResult outcome = TaskDispatchEngine.dispatch(useRoundNo, ldt, rows, gate);

        try {
            return transactionTemplate.execute(status -> persist(outcome, moment, itemMap));
        } catch (DuplicateKeyException dup) {
            // 同趟号并发：uk_round_no 挡住后到者，整笔回滚，照头一趟回话
            TCtldTaskRound head = findRound(useRoundNo);
            if (head != null) {
                return toResult(head, true);
            }
            throw dup;
        }
    }

    @Override
    public TaskDispatchResult replay(String roundNo) {
        if (roundNo == null || roundNo.trim().isEmpty()) {
            throw new CaseFlowException("趟号空着，无趟可复述");
        }
        TCtldTaskRound r = findRound(roundNo.trim());
        if (r == null) {
            throw new CaseFlowException("查无此趟「" + roundNo + "」，无趟尾三数可复述");
        }
        return toResult(r, true);
    }

    // ============================ 同事务落库：趟账 + 两处记号 + 去重账 ============================

    /**
     * 把上一日及更早记为发不成的行重新搁回（2→0），独立一小笔事务。
     * 当日发不成的不搁回（同日不重抛）；已发出（1）的行永不搁回，去重账也另挡一道。
     */
    private void rearmFailedRows(LocalDate today) {
        transactionTemplate.executeWithoutResult(status -> {
            List<TCtldTaskItem> failed = itemMapper.selectList(new QueryWrapper<TCtldTaskItem>()
                    .eq("del_flag", 0).eq("status", TaskDispatchEngine.STATUS_FAILED));
            java.util.Date now = new java.util.Date();
            for (TCtldTaskItem it : failed) {
                java.util.Date last = it.getUpdateTime() != null ? it.getUpdateTime() : it.getCreateTime();
                LocalDate lastDay = last == null ? null : toLocalDate(last);
                if (lastDay != null && lastDay.isBefore(today)) {
                    it.setStatus(TaskDispatchEngine.STATUS_PENDING);
                    it.setChannel(null);
                    it.setFailReason(null);
                    it.setUpdateTime(now);
                    itemMapper.updateById(it);
                }
            }
        });
    }

    private TaskDispatchResult persist(RoundResult out, java.util.Date runAt, Map<Long, TCtldTaskItem> itemMap) {
        java.util.Date finish = new java.util.Date();
        TCtldTaskRound round = new TCtldTaskRound();
        round.setRoundNo(out.roundNo);
        round.setGateOpen(1);
        round.setPickedRows(out.picked);
        round.setSentRows(out.sent);
        round.setFailedRows(out.failed);
        round.setHeldRows(out.held);
        round.setDedupSkippedRows(out.dedupSkipped);
        round.setSmsRows(out.smsCount);
        round.setInnerRows(out.innerCount);
        round.setRunAt(runAt);
        round.setFinishAt(finish);
        round.setReplyText(out.replyText);
        round.setDelFlag(0);
        roundMapper.insert(round);

        TaskDispatchResult reply = new TaskDispatchResult();
        reply.setRoundId(round.getId());
        reply.setRoundNo(out.roundNo);
        reply.setGateOpen(true);
        reply.setReplayed(false);
        reply.setRunAt(runAt);
        reply.setFinishAt(finish);
        reply.setPickedRows(out.picked);
        reply.setSentRows(out.sent);
        reply.setFailedRows(out.failed);
        reply.setHeldRows(out.held);
        reply.setDedupSkippedRows(out.dedupSkipped);
        reply.setSmsRows(out.smsCount);
        reply.setInnerRows(out.innerCount);
        reply.setReplyText(out.replyText);

        for (RowOutcome ro : out.rows) {
            TCtldTaskItem item = itemMap.get(ro.itemId);
            TaskDispatchResult.RowReceipt rc = new TaskDispatchResult.RowReceipt();
            rc.setItemId(ro.itemId);
            rc.setItemNo(item == null ? null : item.getItemNo());
            rc.setKind(ro.kind);
            rc.setChannel(ro.channel);
            rc.setMessageText(ro.messageText);
            rc.setFailReason(ro.failReason);
            rc.setMissingFields(ro.missingFields);
            rc.setContentNote(ro.contentNote);

            if (ro.isSent()) {
                // 第 10 条：记号两处同落——情形改已发出 + 记要补本回时刻与渠道，少落下一趟会重发
                TCtldTaskDedup ledger = new TCtldTaskDedup();
                ledger.setItemId(ro.itemId);
                ledger.setPlotCode(ro.plotCode.trim());
                ledger.setMeasureName(ro.measureName.trim());
                ledger.setDedupKey(ro.plotCode.trim() + TaskDispatchEngine.KEY_JOINER + ro.measureName.trim());
                ledger.setDedupDay(java.sql.Date.valueOf(out.runDay));
                ledger.setChannel(ro.channel);
                ledger.setRoundNo(out.roundNo);
                ledger.setDelFlag(0);
                dedupMapper.insert(ledger);

                if (item != null) {
                    item.setStatus(TaskDispatchEngine.STATUS_SENT);
                    item.setChannel(ro.channel);
                    item.setFailReason(null);
                    item.setContent(appendNote(item.getContent(), ro.contentNote));
                    item.setUpdateTime(runAt);
                    itemMapper.updateById(item);
                }
            } else if ("FAILED".equals(ro.kind)) {
                // 第 7、9 条：本行标失败原由、情形记发不成；不进去重账，次日条件仍够再试；不连累别行
                if (item != null) {
                    item.setStatus(TaskDispatchEngine.STATUS_FAILED);
                    item.setChannel(null);
                    item.setFailReason(ro.failReason);
                    item.setUpdateTime(runAt);
                    itemMapper.updateById(item);
                }
            }
            // HELD（缺件按住）与 DEDUP_SKIP（今日已递）：不动本行记号，原样搁着
            reply.getRows().add(rc);
        }
        return reply;
    }

    private Set<LocalDate> loadOffDays() {
        Set<LocalDate> days = new HashSet<>();
        List<TCtldTaskCalendarOff> list = calendarOffMapper.selectList(
                new QueryWrapper<TCtldTaskCalendarOff>().eq("del_flag", 0).eq("status", 0));
        if (list != null) {
            for (TCtldTaskCalendarOff c : list) {
                if (c.getOffDate() != null) {
                    days.add(toLocalDate(c.getOffDate()));
                }
            }
        }
        return days;
    }

    private TCtldTaskRound findRound(String roundNo) {
        return roundMapper.selectOne(new QueryWrapper<TCtldTaskRound>()
                .eq("round_no", roundNo).eq("del_flag", 0).last("limit 1"));
    }

    /** 照趟账复述：三数只信这份，不另起一回逐行清点 */
    private TaskDispatchResult toResult(TCtldTaskRound r, boolean replayed) {
        TaskDispatchResult reply = new TaskDispatchResult();
        reply.setRoundId(r.getId());
        reply.setRoundNo(r.getRoundNo());
        reply.setGateOpen(r.getGateOpen() != null && r.getGateOpen() == 1);
        reply.setReplayed(replayed);
        reply.setRunAt(r.getRunAt());
        reply.setFinishAt(r.getFinishAt());
        reply.setPickedRows(nz(r.getPickedRows()));
        reply.setSentRows(nz(r.getSentRows()));
        reply.setFailedRows(nz(r.getFailedRows()));
        reply.setHeldRows(nz(r.getHeldRows()));
        reply.setDedupSkippedRows(nz(r.getDedupSkippedRows()));
        reply.setSmsRows(nz(r.getSmsRows()));
        reply.setInnerRows(nz(r.getInnerRows()));
        reply.setReplyText(r.getReplyText());
        // 逐行只回放出本趟真递出的（去重账带着趟号）；其余去向以趟尾三数为准
        List<TCtldTaskDedup> sent = dedupMapper.selectList(new QueryWrapper<TCtldTaskDedup>()
                .eq("round_no", r.getRoundNo()).eq("del_flag", 0).orderByAsc("id"));
        for (TCtldTaskDedup d : sent) {
            TaskDispatchResult.RowReceipt rc = new TaskDispatchResult.RowReceipt();
            rc.setItemId(d.getItemId());
            rc.setKind("SENT");
            rc.setChannel(d.getChannel());
            rc.setPlotCode(d.getPlotCode());
            reply.getRows().add(rc);
        }
        return reply;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private ItemRow toRow(TCtldTaskItem it) {
        ItemRow row = new ItemRow();
        row.id = it.getId();
        row.itemNo = it.getItemNo();
        row.plotCode = it.getPlotCode();
        row.measureName = it.getMeasureName();
        row.dueDate = it.getDueAt() == null ? null : toLocalDate(it.getDueAt());
        row.advanceDays = it.getAmount() == null ? null : it.getAmount().setScale(0, BigDecimal.ROUND_FLOOR);
        row.cropName = it.getCropName();
        row.handlerPost = it.getHandlerPost();
        row.status = it.getStatus();
        row.content = it.getContent();
        return row;
    }

    private LocalDate toLocalDate(java.util.Date d) {
        return d.toInstant().atZone(ZONE).toLocalDate();
    }

    /** 记要补一句：content 只有 255 格，新一句必须完整留着，旧的太长就从头上让位置 */
    private static String appendNote(String oldContent, String note) {
        if (oldContent == null || oldContent.trim().isEmpty()) {
            return note;
        }
        String merged = oldContent + "；" + note;
        if (merged.length() <= 255) {
            return merged;
        }
        int cut = merged.length() - 255;
        return "…" + merged.substring(cut + 1);
    }
}
