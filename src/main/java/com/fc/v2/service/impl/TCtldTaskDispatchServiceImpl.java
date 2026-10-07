package com.fc.v2.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TCtldTaskDispatchMapper;
import com.fc.v2.mapper.auto.TCtldTaskItemMapper;
import com.fc.v2.mapper.auto.TCtldTaskOffDayMapper;
import com.fc.v2.mapper.auto.TCtldTaskSendLogMapper;
import com.fc.v2.model.auto.TCtldTaskDispatch;
import com.fc.v2.model.auto.TCtldTaskItem;
import com.fc.v2.model.auto.TCtldTaskOffDay;
import com.fc.v2.model.auto.TCtldTaskSendLog;
import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchEngine;
import com.fc.v2.service.ctld.TaskDispatchEngine.DispatchResult;
import com.fc.v2.service.ctld.TaskDispatchEngine.ItemRow;
import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyGate;
import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyOutcome;
import com.fc.v2.service.ctld.TaskDispatchEngine.RowOutcome;
import com.fc.v2.service.ctld.TaskDispatchReply;
import com.fc.v2.service.ctld.TaskDispatchReply.RowView;
import com.fc.v2.service.ctld.notify.StationTaskMessageSender;
import com.fc.v2.service.ctld.notify.TaskSmsSender;

/**
 * 农时催办「早班翻单」Service 实现。
 * 十条口径全部出自 {@link TaskDispatchEngine}；趟账、逐行流水、条目两处记号、站内回落同事务一起成一起回。
 * 闸外不查库不落库，只回「本轮未动单」；闸内同日按第二回照头一趟回话（uk_run_date 挡并发）。
 * 趟末三数只信引擎本次返回并落趟账的那份，replay 只读书，不拉另一轮逐行清点做对冲。
 * 往年存进库里的老条目与今年新条目同走这张表、这套筛法，不另开一门。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class TCtldTaskDispatchServiceImpl implements ITCtldTaskDispatchService {

    /** 事由与所对地块记要格上限（varchar(255)）：补句超长时留新句，不把库顶出错误撂整趟 */
    private static final int CONTENT_MAX = 255;
    /** 本行失败原由格上限（fail_reason/remark varchar(500)） */
    private static final int REASON_MAX = 500;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @javax.annotation.Resource
    private TCtldTaskItemMapper itemMapper;
    @javax.annotation.Resource
    private TCtldTaskOffDayMapper offDayMapper;
    @javax.annotation.Resource
    private TCtldTaskDispatchMapper dispatchMapper;
    @javax.annotation.Resource
    private TCtldTaskSendLogMapper sendLogMapper;
    @javax.annotation.Resource
    private TaskSmsSender smsSender;
    @javax.annotation.Resource
    private StationTaskMessageSender stationSender;

    private final TransactionTemplate transactionTemplate;

    public TCtldTaskDispatchServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================ 唯一触发口 ============================

    @Override
    public TaskDispatchReply runOnce(Date at) {
        Date now = at == null ? new Date() : at;
        // 第 1 条：闸外只回一句，不查库、不落库，同一刻按两回一字不差
        if (!TaskDispatchEngine.gateOpen(now)) {
            return closedQuiet(now, TaskDispatchEngine.dateOf(now));
        }

        // 事务外先照一面：今日头一趟已在，照头一趟回话
        LocalDate today = TaskDispatchEngine.dateOf(now);
        TCtldTaskDispatch existed = findRun(today);
        if (existed != null) {
            return replay(existed);
        }
        try {
            return transactionTemplate.execute(status -> {
                // 锁并发：事务内再照一面，后到者照头一趟回话
                TCtldTaskDispatch head = findRun(today);
                if (head != null) {
                    return replay(head);
                }
                return doDispatch(now, today);
            });
        } catch (DuplicateKeyException dup) {
            // uk_run_date 挡住同日并着进的第二回：照头一趟回话，不许再起一趟
            TCtldTaskDispatch head = findRun(today);
            return head == null ? closedQuiet(now, today) : replay(head);
        }
    }

    // ============================ 头一趟：同一次点算、同一事务落账 ============================

    private TaskDispatchReply doDispatch(Date now, LocalDate today) {
        Set<LocalDate> offDays = loadOffDays();
        Set<String> sentKeysToday = loadSentKeys(today);
        Set<Long> triedItemIdsToday = loadTriedItemIds(today);

        // 第 4 条候选：只从搁着的一叠＋发不成的行里取（已发出的翻出不进 SQL）；
        // 发不成但今日已经试过一回的，retryable=false，由引擎一处裁决「翻出来不再碰」，次日条件够才再试。
        List<TCtldTaskItem> rows = itemMapper.selectList(new QueryWrapper<TCtldTaskItem>()
                .in("status", TaskDispatchEngine.STATUS_WAIT, TaskDispatchEngine.STATUS_FAILED)
                .eq("del_flag", 0).orderByAsc("due_at", "id"));
        List<ItemRow> candidates = new ArrayList<>();
        for (TCtldTaskItem r : rows) {
            boolean retryable = r.getStatus() == TaskDispatchEngine.STATUS_WAIT
                    || !triedItemIdsToday.contains(r.getId());
            // 一行只读本行的字（第 5 条）：各格照抄本行，不借分区册、不借邻行
            candidates.add(new ItemRow(r.getId(), r.getItemNo(), r.getPlotNo(), r.getMeasureName(),
                    r.getMainCrop(), r.getAssigneePost(), r.getDueAt(), r.getAmount(), retryable));
        }

        // 先占当日槽位（uk_run_date）：并发的第二趟在此就被挡回去照头一趟回话，
        // 绝不许两边都发完短信再来抢账——占槽之前，一句都不递。
        TCtldTaskDispatch d = new TCtldTaskDispatch();
        d.setRunDate(sqlDate(today));
        d.setRunAt(now);
        d.setGateOpen(1);
        d.setPickedRows(0);
        d.setSentRows(0);
        d.setFailedRows(0);
        d.setResultMsg(null);
        d.setDelFlag(0);
        d.setCreateTime(now);
        dispatchMapper.insert(d);

        DispatchResult result = TaskDispatchEngine.dispatch(
                now, candidates, offDays, sentKeysToday, buildGate());

        // 三数与逐行记号同一次算定：引擎点算完再把这份数回填趟账（页面只信这一处）
        d.setPickedRows(result.getPickedRows());
        d.setSentRows(result.getSentRows());
        d.setFailedRows(result.getFailedRows());
        d.setResultMsg(result.getResultMsg());
        d.setUpdateTime(now);
        dispatchMapper.updateById(d);

        List<RowView> views = new ArrayList<>();
        for (RowOutcome o : result.getOutcomes()) {
            TCtldTaskSendLog log = new TCtldTaskSendLog();
            log.setRunId(d.getId());
            log.setRunDate(sqlDate(today));
            log.setItemId(o.getItemId());
            log.setItemNo(o.getItemNo());
            log.setPlotNo(o.getPlotNo());
            log.setMeasureName(o.getMeasureName());
            log.setDueDate(o.getDueDate() == null ? null : sqlDate(o.getDueDate()));
            log.setSendDate(sqlDate(today));
            log.setChannel(o.getChannel());
            log.setResultKind(o.getResultKind());
            log.setStatus(o.getItemStatus());
            log.setMsgText(o.getMsgText());
            log.setFailReason(cut(o.getReason(), REASON_MAX));
            log.setDelFlag(0);
            log.setCreateTime(now);
            sendLogMapper.insert(log);

            // 第 9/10 条：只回写本行；一处失手不抹他行，已发出的不回原状
            TCtldTaskItem item = itemMapper.selectById(o.getItemId());
            if (item == null) {
                // 条目在本趟途中被删：流水已照引擎点算落账，不为此撂下整趟
                continue;
            }
            if (o.getItemStatus() == TaskDispatchEngine.STATUS_SENT) {
                // 第 10 条记号两处：情形改已发出；事由与所对地块的记要再补一句时刻与渠道。
                // 只落前一处下一趟会再挑出来发一回——今年不许再现，故两处同事务落。
                item.setStatus(TaskDispatchEngine.STATUS_SENT);
                item.setContent(appendSendStamp(item.getContent(), now, o.getChannel()));
                if (o.getReason() != null) {
                    // 第 7 条：短信走不通才回落站内的，那句原由也写在本行
                    item.setRemark(cut(o.getReason(), REASON_MAX));
                }
                item.setUpdateTime(now);
                itemMapper.updateById(item);
            } else if (o.getItemStatus() == TaskDispatchEngine.STATUS_FAILED) {
                // 第 9 条：本行标一句失败原由，整趟不撂；次日条件仍够再试（流水不占去重槽）
                item.setStatus(TaskDispatchEngine.STATUS_FAILED);
                item.setRemark(cut(o.getReason(), REASON_MAX));
                item.setUpdateTime(now);
                itemMapper.updateById(item);
            }
            // HELD_MISSING / DEDUP_SKIP：本行按兵不动、无人代填，缺哪几项只在流水里照原样吐出

            views.add(toView(o));
        }

        return toReply(d, views, false);
    }

    /** 第 7 条渠道口径写死在此：先短信，当场不通就地回落站内；两条皆不通由引擎记发不成 */
    private NotifyGate buildGate() {
        return new NotifyGate() {
            @Override
            public NotifyOutcome send(String channel, String text) {
                if (TaskDispatchEngine.CHANNEL_SMS.equals(channel)) {
                    return smsSender.send(text);
                }
                if (TaskDispatchEngine.CHANNEL_STATION.equals(channel)) {
                    return stationSender.send(text);
                }
                return NotifyOutcome.fail("不认得的渠道：" + channel);
            }
        };
    }

    // ============================ 幂等回放（照头一趟，不重算、不重发） ============================

    private TaskDispatchReply replay(TCtldTaskDispatch d) {
        List<TCtldTaskSendLog> logs = sendLogMapper.selectList(new QueryWrapper<TCtldTaskSendLog>()
                .eq("run_id", d.getId()).eq("del_flag", 0).orderByAsc("id"));
        List<RowView> views = new ArrayList<>();
        for (TCtldTaskSendLog log : logs) {
            RowView v = new RowView();
            v.setItemId(log.getItemId());
            v.setItemNo(log.getItemNo());
            v.setPlotNo(log.getPlotNo());
            v.setMeasureName(log.getMeasureName());
            v.setDueDate(log.getDueDate());
            v.setChannel(log.getChannel());
            v.setResultKind(log.getResultKind());
            v.setStatus(log.getStatus());
            v.setMsgText(log.getMsgText());
            v.setFailReason(log.getFailReason());
            views.add(v);
        }
        return toReply(d, views, true);
    }

    private TaskDispatchReply closedQuiet(Date now, LocalDate today) {
        TaskDispatchReply closed = new TaskDispatchReply();
        closed.setRunDate(sqlDate(today));
        closed.setRunAt(now);
        closed.setGateOpen(false);
        closed.setResultMsg(TaskDispatchEngine.GATE_CLOSED_REPLY);
        return closed;
    }

    /** 回话只转述趟账与本次逐行记号；三数取自趟账那一处，不当场另数 */
    private TaskDispatchReply toReply(TCtldTaskDispatch d, List<RowView> views, boolean replayed) {
        TaskDispatchReply reply = new TaskDispatchReply();
        reply.setRunId(d.getId());
        reply.setRunDate(d.getRunDate());
        reply.setRunAt(d.getRunAt());
        reply.setGateOpen(d.getGateOpen() != null && d.getGateOpen() == 1);
        reply.setReplayed(replayed);
        reply.setPickedRows(d.getPickedRows());
        reply.setSentRows(d.getSentRows());
        reply.setFailedRows(d.getFailedRows());
        reply.setResultMsg(d.getResultMsg());
        reply.getRows().addAll(views);
        return reply;
    }

    private RowView toView(RowOutcome o) {
        RowView v = new RowView();
        v.setItemId(o.getItemId());
        v.setItemNo(o.getItemNo());
        v.setPlotNo(o.getPlotNo());
        v.setMeasureName(o.getMeasureName());
        v.setDueDate(o.getDueDate() == null ? null : sqlDate(o.getDueDate()));
        v.setChannel(o.getChannel());
        v.setResultKind(o.getResultKind());
        v.setStatus(o.getItemStatus());
        v.setMsgText(o.getMsgText());
        v.setFailReason(o.getReason());
        return v;
    }

    // ============================ 取数 ============================

    private TCtldTaskDispatch findRun(LocalDate runDate) {
        return dispatchMapper.selectOne(new QueryWrapper<TCtldTaskDispatch>()
                .eq("run_date", sqlDate(runDate)).eq("del_flag", 0).last("limit 1"));
    }

    private Set<LocalDate> loadOffDays() {
        Set<LocalDate> set = new HashSet<>();
        for (TCtldTaskOffDay d : offDayMapper.selectList(new QueryWrapper<TCtldTaskOffDay>()
                .eq("del_flag", 0))) {
            if (d.getOffDate() != null) {
                set.add(TaskDispatchEngine.dateOf(d.getOffDate()));
            }
        }
        return set;
    }

    /** 今日已递成的去重键：地块码#事由名目（第 6 条只认这两段接成的键） */
    private Set<String> loadSentKeys(LocalDate today) {
        Set<String> keys = new LinkedHashSet<>();
        List<TCtldTaskSendLog> logs = sendLogMapper.selectList(new QueryWrapper<TCtldTaskSendLog>()
                .eq("run_date", sqlDate(today)).eq("status", TaskDispatchEngine.STATUS_SENT)
                .eq("del_flag", 0));
        for (TCtldTaskSendLog log : logs) {
            String key = TaskDispatchEngine.dedupKey(log.getPlotNo(), log.getMeasureName());
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** 今日已经试过（无论递成/发不成/按下）的条目：发不成行今日不再翻出来 */
    private Set<Long> loadTriedItemIds(LocalDate today) {
        Set<Long> ids = new HashSet<>();
        for (TCtldTaskSendLog log : sendLogMapper.selectList(new QueryWrapper<TCtldTaskSendLog>()
                .eq("run_date", sqlDate(today)).eq("del_flag", 0))) {
            ids.add(log.getItemId());
        }
        return ids;
    }

    // ============================ 杂项 ============================

    /** 事由地块记要补一句：本回发出那一刻与走的是哪条渠道；超长留新句 */
    private static String appendSendStamp(String oldContent, Date at, String channel) {
        String channelName = TaskDispatchEngine.CHANNEL_STATION.equals(channel) ? "站内消息" : "短信";
        String stamp = "【" + TS_FMT.format(at.toInstant().atZone(TaskDispatchEngine.ZONE))
                + " 走" + channelName + "渠道发出】";
        String merged = oldContent == null || oldContent.trim().isEmpty()
                ? stamp : oldContent + "；" + stamp;
        if (merged.length() > CONTENT_MAX) {
            merged = "…" + merged.substring(merged.length() - (CONTENT_MAX - 1));
        }
        return merged;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static java.sql.Date sqlDate(LocalDate d) {
        return java.sql.Date.valueOf(d);
    }
}
