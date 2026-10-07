package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.common.log.Log;
import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 农时催办《安排单》早班「翻单」Controller。触发口只此一个：
 * 窗口开不开、捞哪几行、去重认不认同一桩、渠道转不转备胎、趟尾三数从何处取——全认后端引擎本次返回，
 * 本层不先把数字端出来再回头拼依据，也不收别的零散逐行入口。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "农时催办早班翻单")
@Controller
@RequestMapping("/ctldTaskDispatch")
public class CtldTaskDispatchController extends BaseController {

    @Autowired
    private ITCtldTaskDispatchService taskDispatchService;

    @Log(title = "农时催办早班翻单", action = "runEarlyShift")
    @ApiOperation(value = "跑一趟早班翻单", notes = "只在 06:30-09:00 窗口内动单；闸门外固定回「本轮未动单」。同趟号第二回照头一趟回话")
    @PostMapping("/run")
    @RequiresPermissions("ctldTaskDispatch:run")
    @ResponseBody
    public AjaxResult run(@RequestParam(required = false) String roundNo,
                          @RequestParam(required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date at) {
        try {
            TaskDispatchResult r = taskDispatchService.runEarlyShift(roundNo, at);
            String msg = !r.isGateOpen()
                    ? com.fc.v2.service.ctld.TaskDispatchEngine.GATE_CLOSED_REPLY
                    : (r.isReplayed() ? "同一趟第二回，照头一趟回话" : r.getReplyText());
            return AjaxResult.success(msg).put("data", r);
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    @ApiOperation(value = "照趟号复述一趟", notes = "趟尾三数以趟账当场落下的那份为准，页面只是复述")
    @GetMapping("/replay")
    @RequiresPermissions("ctldTaskDispatch:list")
    @ResponseBody
    public AjaxResult replay(@RequestParam String roundNo) {
        try {
            return AjaxResult.success("ok").put("data", taskDispatchService.replay(roundNo));
        } catch (CaseFlowException e) {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }

    private static String TaskDispatchReplyClosed(TaskDispatchResult r) {
        // 闸门外只许引用那句固定回话，不拼别的
        return com.fc.v2.service.ctld.TaskDispatchEngine.GATE_CLOSED_REPLY;
    }
}
