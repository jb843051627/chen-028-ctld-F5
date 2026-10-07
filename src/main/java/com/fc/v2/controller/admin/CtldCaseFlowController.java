package com.fc.v2.controller.admin;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TCtldCaseFlow;
import com.fc.v2.service.ITCtldCaseFlowService;
import com.fc.v2.service.ctld.CaseFlowResult;
import com.fc.v2.service.ctld.CaseFlowResult.OpenCmd;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 超标地块纳入受限与解除办件流转单 Controller（state-machine 形状：流转入口）。
 * 五问口径只认后端 service 这一处，屏上不预设口径；本层只照纸传话，被拒把缺的档/栏点名叫响。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "超标地块纳入受限与解除办件流转单")
@Controller
@RequestMapping("/ctldCaseFlow")
public class CtldCaseFlowController extends BaseController {

    private final String prefix = "admin/ctldCaseFlow";

    @Autowired
    private ITCtldCaseFlowService ctldCaseFlowService;

    /** 被拒回话：固定码 + 点到具体档/栏的话，不回笼统一句「信息不全」 */
    private AjaxResult reject(CaseFlowException e) {
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("ctldCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "超标地块纳入受限与解除办件流转单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "台账与未闭条数同一回给出")
    @GetMapping("/list")
    @RequiresPermissions("ctldCaseFlow:list")
    @ResponseBody
    public ResultTable list(TCtldCaseFlow record) {
        QueryWrapper<TCtldCaseFlow> queryWrapper = new QueryWrapper<TCtldCaseFlow>();
        queryWrapper.eq("del_flag", 0).orderByDesc("create_time");
        startPage();
        com.github.pagehelper.PageInfo<TCtldCaseFlow> page =
                new com.github.pagehelper.PageInfo<TCtldCaseFlow>(ctldCaseFlowService.selectTCtldCaseFlowList(queryWrapper));
        ResultTable table = pageTable(page.getList(), page.getTotal());
        // 屏上那一个数与后端查出来的数同一回给出，前端不得另起一套数法
        table.setOpenCount(ctldCaseFlowService.countOpen());
        return table;
    }

    @ApiOperation(value = "未闭条数", notes = "没闭上那一类的条数，只由这一处数")
    @GetMapping("/openCount")
    @RequiresPermissions("ctldCaseFlow:list")
    @ResponseBody
    public AjaxResult openCount() {
        return AjaxResult.success("ok").put("openCount", ctldCaseFlowService.countOpen());
    }

    @Log(title = "超标地块纳入受限与解除办件流转单起件", action = "open")
    @ApiOperation(value = "起件", notes = "四要件齐身才起得来：地块码/分区代号/镉实测值/措施名目")
    @PostMapping("/open")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult open(@RequestParam(required = false) String plotNo,
                           @RequestParam(required = false) String zoneNo,
                           @RequestParam(required = false) BigDecimal cdValue,
                           @RequestParam(required = false) String measureName,
                           @RequestParam(required = false) String submitter,
                           @RequestParam(required = false) String reqNo) {
        try {
            OpenCmd cmd = new OpenCmd();
            cmd.setPlotNo(plotNo);
            cmd.setZoneNo(zoneNo);
            cmd.setCdValue(cdValue);
            cmd.setMeasureName(measureName);
            cmd.setSubmitter(submitter);
            cmd.setReqNo(reqNo);
            CaseFlowResult r = ctldCaseFlowService.open(cmd);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单本档按语", action = "note")
    @ApiOperation(value = "本档落按语", notes = "一档一句，同档第二句不覆不存")
    @PostMapping("/note")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult note(Long id, String note, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.note(id, note, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "纳限档核收", action = "acceptLimit")
    @ApiOperation(value = "纳入受限档核收", notes = "记成「可」才许往措施核查开工")
    @PostMapping("/acceptLimit")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult acceptLimit(Long id, boolean pass, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.acceptLimit(id, pass, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "解除复核见证落笔", action = "witness")
    @ApiOperation(value = "见证岗落笔", notes = "两名见证岗各落一笔，同人不许顶两笔")
    @PostMapping("/witness")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult witness(Long id, String witnessPost, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.witness(id, witnessPost, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "措施核验凭证写回", action = "voucher")
    @ApiOperation(value = "凭证串写回本行", notes = "解除复核门槛之一")
    @PostMapping("/voucher")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult voucher(Long id, String voucherNo, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.voucher(id, voucherNo, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单视同收口·收回", action = "withdraw")
    @ApiOperation(value = "递件人收回", notes = "递件人本人收回反映，就此收口")
    @PostMapping("/withdraw")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult withdraw(Long id, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.withdraw(id, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单视同收口·分区并走", action = "closeZone")
    @ApiOperation(value = "分区并走收口", notes = "所属分区已并走撤出，就此收口")
    @PostMapping("/closeZone")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult closeZone(Long id, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.closeByZoneMerged(id, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单推进（带门槛回话）", action = "forward")
    @ApiOperation(value = "推进一档·并列入口", notes = "把挡门的那一条点清楚；与老 advance 同走一处口径")
    @PostMapping("/forward")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult forward(Long id, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.stepForward(id, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单回退（带按语转存回话）", action = "back")
    @ApiOperation(value = "回退一档·并列入口", notes = "一次只收一档，被收按语转进追溯串")
    @PostMapping("/back")
    @RequiresPermissions("ctldCaseFlow:rollback")
    @ResponseBody
    public AjaxResult back(Long id, @RequestParam(required = false) String reqNo) {
        try {
            CaseFlowResult r = ctldCaseFlowService.stepBack(id, reqNo);
            return AjaxResult.success(r.getCode(), r.getMsg(), r);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "超标地块纳入受限与解除办件流转单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "老入口签名不改：一次只跨一档，越档/门槛不闭返回操作失败，件留原地")
    @PostMapping("/advance")
    @RequiresPermissions("ctldCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(ctldCaseFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "超标地块纳入受限与解除办件流转单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "老入口签名不改：一次只收一档，头一档不得再收")
    @PostMapping("/rollback")
    @RequiresPermissions("ctldCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(ctldCaseFlowService.rollback(id, remark) != null ? 1 : 0);
    }

    @Log(title = "流转单内容更正", action = "edit")
    @ApiOperation(value = "内容更正", notes = "已封住的行不张口，更正不接")
    @PostMapping("/edit")
    @RequiresPermissions("ctldCaseFlow:rollback")
    @ResponseBody
    public AjaxResult edit(Long id, String remark) {
        try {
            return toAjax(ctldCaseFlowService.updateContent(id, remark) ? 1 : 0);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @Log(title = "流转单整条抽回", action = "remove")
    @ApiOperation(value = "整条抽回", notes = "已封住的件在册面露脸但不给抽回的把手")
    @DeleteMapping("/remove")
    @RequiresPermissions("ctldCaseFlow:rollback")
    @ResponseBody
    public AjaxResult remove(Long id) {
        try {
            return toAjax(ctldCaseFlowService.remove(id) ? 1 : 0);
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }

    @ApiOperation(value = "变更履历", notes = "本件追溯串，按时序，同刻按 seq 分先后")
    @GetMapping("/trace")
    @RequiresPermissions("ctldCaseFlow:list")
    @ResponseBody
    public AjaxResult trace(Long id) {
        return AjaxResult.success("ok").put("data", ctldCaseFlowService.trace(id));
    }

    @ApiOperation(value = "只读回放", notes = "从末档倒着演，只看结果不写回；缺第几档、缺哪一栏逐一点名")
    @GetMapping("/replay")
    @RequiresPermissions("ctldCaseFlow:list")
    @ResponseBody
    public AjaxResult replay(Long id) {
        try {
            return AjaxResult.success("ok").put("data", ctldCaseFlowService.replay(id));
        } catch (CaseFlowException e) {
            return reject(e);
        }
    }
}
