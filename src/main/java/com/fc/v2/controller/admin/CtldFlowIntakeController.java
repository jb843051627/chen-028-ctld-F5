package com.fc.v2.controller.admin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.common.log.Log;
import com.fc.v2.service.ctld.FlowIntakeCmd;
import com.fc.v2.service.ctld.FlowIntakeEngine;
import com.fc.v2.service.ctld.FlowIntakeResult;
import com.fc.v2.service.ctld.ITCtldFlowIntakeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 土壤-农产品协同检测流水「四节核收」Controller（整拨一次递齐，无零散逐行入口）。
 * 四问口径只认后端核收这一处：各行下场、重到门口的同批次码、整拨成不成、拨头三个数，
 * 本层只照纸传话，不自行写库、不自行改行，也不许先落流水再补结论。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "土壤-农产品协同检测流水核收")
@Controller
@RequestMapping("/ctldFlowIntake")
public class CtldFlowIntakeController extends BaseController {

    @Autowired
    private ITCtldFlowIntakeService flowIntakeService;

    @Log(title = "协同检测流水整拨核收", action = "intake")
    @ApiOperation(value = "整拨核收", notes = "整拨一次递齐：点行数、看数据、定去处、出回执一口气走完；同拨第二回照头一拨回话")
    @PostMapping("/intake")
    @RequiresPermissions("ctldFlowIntake:intake")
    @ResponseBody
    public AjaxResult intake(@RequestParam String batchNo,
                             @RequestParam(required = false) Integer periodNo,
                             @RequestParam(required = false) String declaredCap,
                             @RequestParam(required = false) String parseKind,
                             @RequestParam(required = false) String delimiter,
                             @RequestParam(required = false) String widths,
                             @RequestParam(required = false) String rawText,
                             @RequestParam(required = false) String putKind) {
        try {
            FlowIntakeCmd cmd = new FlowIntakeCmd();
            cmd.setBatchNo(batchNo);
            cmd.setPeriodNo(periodNo);
            cmd.setDeclaredCap(declaredCap);
            cmd.setParseKind(parseKind == null || parseKind.trim().isEmpty()
                    ? FlowIntakeEngine.PARSE_DELIM : parseKind.trim());
            cmd.setDelimiter(delimiter == null || delimiter.isEmpty() ? "|" : delimiter);
            cmd.setWidths(parseWidths(widths));
            cmd.setRawLines(splitRawText(rawText));
            cmd.setPutKind(putKind);
            FlowIntakeResult r = flowIntakeService.intake(cmd);
            return AjaxResult.success(r.isReplayed() ? "同一拨第二遍，照头一拨回话" : "核收完毕")
                    .put("data", r);
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    @ApiOperation(value = "一拨回执", notes = "拨头三栏以核收当场落下那份为准，页面只是复述")
    @GetMapping("/receipt")
    @RequiresPermissions("ctldFlowIntake:list")
    @ResponseBody
    public AjaxResult receipt(@RequestParam Long batchId) {
        try {
            return AjaxResult.success("ok").put("data", flowIntakeService.receipt(batchId));
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    @ApiOperation(value = "待核清单", notes = "去处一直空悬在未勾的行，回核的人逐行定去向，系统不替它收")
    @GetMapping("/pending")
    @RequiresPermissions("ctldFlowIntake:list")
    @ResponseBody
    public AjaxResult pending(@RequestParam(required = false) String batchNo) {
        return AjaxResult.success("ok").put("data", flowIntakeService.pendingList(batchNo));
    }

    @Log(title = "协同检测流水回核逐行定去向", action = "settlePending")
    @ApiOperation(value = "回核逐行定去向", notes = "只准对未勾行落笔：1收下 2退回；退回必须写明条款")
    @PostMapping("/settlePending")
    @RequiresPermissions("ctldFlowIntake:review")
    @ResponseBody
    public AjaxResult settlePending(@RequestParam Long rowId,
                                    @RequestParam int status,
                                    @RequestParam(required = false) String rejectCode) {
        try {
            return toAjax(flowIntakeService.settlePending(rowId, status, rejectCode) ? 1 : 0);
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    @ApiOperation(value = "异常标注单", notes = "切不开/带不上编码/只到一个数/题名比对不合，递给回核看的人")
    @GetMapping("/marks")
    @RequiresPermissions("ctldFlowIntake:list")
    @ResponseBody
    public AjaxResult marks(@RequestParam(required = false) String batchNo) {
        return AjaxResult.success("ok").put("data", flowIntakeService.markList(batchNo));
    }

    @ApiOperation(value = "抽行回核", notes = "本行去处与所属拨三栏相互印证；对不上逐行点名，不接受大致相符")
    @GetMapping("/verify")
    @RequiresPermissions("ctldFlowIntake:list")
    @ResponseBody
    public AjaxResult verify(@RequestParam Long rowId) {
        try {
            return AjaxResult.success("ok").put("data", flowIntakeService.verify(rowId));
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    /** 整段报文按行切开：换页符不归这里吞（它在引擎里按编码异常标注），\r\n/\r/\n 都认 */
    private static List<String> splitRawText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(rawText.split("\\r\\n|\\r|\\n", -1))
                .collect(Collectors.toList());
    }

    private static int[] parseWidths(String widths) {
        if (widths == null || widths.trim().isEmpty()) {
            return null;
        }
        String[] parts = widths.split(",");
        int[] ws = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ws[i] = Integer.parseInt(parts[i].trim());
        }
        return ws;
    }
}
