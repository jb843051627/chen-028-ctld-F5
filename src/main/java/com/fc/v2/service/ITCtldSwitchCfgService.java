package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;

import java.util.List;

/**
 * 产地准出核验口径切换开关表 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITCtldSwitchCfgService {

    /** 按主键查询 */
    TCtldSwitchCfg selectTCtldSwitchCfgById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TCtldSwitchCfg> selectTCtldSwitchCfgList(Wrapper<TCtldSwitchCfg> queryWrapper);

    /** 新增 */
    int insertTCtldSwitchCfg(TCtldSwitchCfg record);

    /** 修改 */
    int updateTCtldSwitchCfg(TCtldSwitchCfg record);

    /** 批量删除 */
    int deleteTCtldSwitchCfgByIds(String ids);

    /** 推进一态（待核对→已核对→已冻住） */
    int advanceTCtldSwitchCfg(Long id);

    /** 退回一态（已核对→待核对；旧记录沉底） */
    int revertTCtldSwitchCfg(Long id);

    /** 按主键删除 */
    int deleteTCtldSwitchCfgById(Long id);
}
