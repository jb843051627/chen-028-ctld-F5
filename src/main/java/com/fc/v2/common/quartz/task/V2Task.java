package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSysUserService;
import com.fc.v2.service.ctld.ITCtldTaskDispatchService;
import com.fc.v2.service.ctld.TaskDispatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITCtldTaskDispatchService taskDispatchService;

	/**
	 * 早班翻单：只在 06:30-09:00 窗口内动单（窗口由后端引擎把）。
	 * 定时器可在窗口内多次触发；闸门外触发只回「本轮未动单」，不挑不发不落账。
	 * 趟号按触发时刻生成，窗口内重复触发各是各趟；要人工重放同一趟，显式传同一个趟号。
	 */
	public void runEarlyTaskShift()
	{
		TaskDispatchResult r = taskDispatchService.runEarlyShift(null, null);
		System.out.println("农时催办早班翻单：" + (r.isGateOpen() ? r.getReplyText() : "本轮未动单"));
	}
    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }
}
