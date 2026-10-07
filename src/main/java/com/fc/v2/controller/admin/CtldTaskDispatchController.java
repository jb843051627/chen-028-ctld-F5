package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchReply;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 县局农艺调度室「早班翻单」Controller——触发口只此一个。
 * 十条口径只认后端调度引擎这一处的裁决：本轮捞哪几行、窗口开不开闸、去重键认不认作同一桩、
 * 短信不通转不转站内、同轮两回幂等、趟尾三个数从何处取。
 * 本层只照纸传话：不自行点行、不自行拼数；屏上不得先把数字端出来再回头拼依据。
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

    @Log(title = "农时催办早班翻单", action = "run")
    @ApiOperation(value = "翻一趟早班单", notes = "06:30-09:00 之外只回「本轮未动单」；同日第二回照头一趟回话；三数只认本次返回")
    @PostMapping("/run")
    @RequiresPermissions("ctldTaskDispatch:run")
    @ResponseBody
    public AjaxResult run() {
        // 当口时刻在此取一次带入，筛行与回写共用这把尺
        TaskDispatchReply reply = taskDispatchService.runOnce(new Date());
        if (!reply.isGateOpen()) {
            return AjaxResult.success(reply.getResultMsg()).put("data", reply);
        }
        return AjaxResult.success(reply.isReplayed() ? "同一日第二遍，照头一趟回话" : "早班翻单完毕")
                .put("data", reply);
    }
}
