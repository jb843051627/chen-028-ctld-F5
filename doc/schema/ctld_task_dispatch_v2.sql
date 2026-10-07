-- ctld 农时催办「早班翻单」增量迁移（chen-028）
-- 与基线 doc/schema/ctld.sql 配套：本脚本只做增量，已在库上执行一次即可。
-- 口径（十条）：本轮捞哪几行、06:30-09:00 开不开闸、去重键认不认作同一桩、短信走不通转不转站内、
--   同轮跑两回幂等、趟尾三个数从何处取——只认后端早班调度这一处给的裁决。
-- 老的一条（TCtldTaskItemServiceImpl 里 listDue/runOnce 那套）冻住不动；若要按分区限一趟，另立名目，莫在本体上动刀。
-- 库：chen_028

-- ============ 一、催办条目增列（一行只读本行的字，四要件各落各格） ============
ALTER TABLE t_ctld_task_item
  ADD COLUMN plot_no varchar(64) DEFAULT NULL COMMENT '本行所对地块码（去重键前段，只读本行）' AFTER item_no,
  ADD COLUMN measure_name varchar(128) DEFAULT NULL COMMENT '事由名目/措施名目（去重键后段，只读本行）' AFTER plot_no,
  ADD COLUMN main_crop varchar(64) DEFAULT NULL COMMENT '主栽作物（话术四要件之一，取不着不代填）' AFTER measure_name,
  ADD COLUMN assignee_post varchar(64) DEFAULT NULL COMMENT '承办岗（话术四要件之一）' AFTER main_crop;

-- ============ 二、停办日历：法定节假日与县里挂出的停办日，提前量里都不计日 ============
CREATE TABLE IF NOT EXISTS t_ctld_task_off_day (
  id bigint NOT NULL COMMENT '主键',
  off_date date NOT NULL COMMENT '停办那一一日',
  off_kind varchar(16) NOT NULL COMMENT '类别 HOLIDAY法定节假日 SUSPEND县里挂出停办日',
  off_name varchar(128) DEFAULT NULL COMMENT '名目（哪一个节/县里哪一桩停办）',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_off_date_kind (off_date, off_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办停办日历（法定节假日+县里停办日，计提前量时剔出）';

-- ============ 三、趟账：早班一日一趟，三个数与逐行记号同一次算定后落此 ============
-- 同一日闸门内按两回：第二回照头一趟回话（幂等），uk 把后到者挡在门外。
CREATE TABLE IF NOT EXISTS t_ctld_task_dispatch (
  id bigint NOT NULL COMMENT '主键',
  run_date date NOT NULL COMMENT '本轮所属那一一日（闸门内一日只认头一趟）',
  run_at datetime NOT NULL COMMENT '头一趟动手那一刻（闸门内）',
  gate_open int NOT NULL COMMENT '本趟闸是否开着 0闭 1开（只有 1 才会落行）',
  picked_rows int NOT NULL COMMENT '趟末数一：本轮挑中几行',
  sent_rows int NOT NULL COMMENT '趟末数二：实际递出几行（短信+站内）',
  failed_rows int NOT NULL COMMENT '趟末数三：几行未成（两条渠道皆不通/缺项按下/同键去重）',
  result_msg varchar(255) DEFAULT NULL COMMENT '趟末回话原文（三个数的复述，数据源仍是本账三栏）',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_date (run_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办早班趟账（一日一份，三数同源于一次点算）';

-- ============ 四、发出流水：逐行渠道与原由，也是同地块同事由一日一条的去重账 ============
-- 只有 status=1 已发出的行才占 success_slot 唯一槽；发不成（2）/按下未发（0）不占去重名额，次日条件够再试。
CREATE TABLE IF NOT EXISTS t_ctld_task_send_log (
  id bigint NOT NULL COMMENT '主键',
  run_id bigint NOT NULL COMMENT '所属趟账 id',
  run_date date NOT NULL COMMENT '本流水所属那一一日',
  item_id bigint NOT NULL COMMENT '催办条目 id',
  item_no varchar(64) DEFAULT NULL COMMENT '条目代号（照抄本行，便于追溯）',
  plot_no varchar(64) DEFAULT NULL COMMENT '地块码（本行那格，不向左邻右舍借）',
  measure_name varchar(128) DEFAULT NULL COMMENT '事由名目（本行那格）',
  due_date date DEFAULT NULL COMMENT '届期那一一日（话术照原样落）',
  send_date date NOT NULL COMMENT '本回发出那一一日',
  channel varchar(8) DEFAULT NULL COMMENT '实际走通的渠道 SMS短信 STATION站内消息；未递出为空',
  result_kind varchar(16) NOT NULL COMMENT '本行结局 SENT_SMS短信递出 SENT_STATION站内递出 FAILED两条皆不通 HELD_MISSING缺项按下 DEDUP_SKIP同键一日内已递过',
  status int NOT NULL COMMENT '记到条目上的情形 1已发出 2发不成 0按下未发/未递出',
  msg_text varchar(500) DEFAULT NULL COMMENT '递出去那句原话（四项齐全才成形）',
  fail_reason varchar(500) DEFAULT NULL COMMENT '走不通那句原由/缺项清单（短信回落站内时，短信原由也落此）',
  success_slot varchar(255) GENERATED ALWAYS AS (CASE WHEN status = 1 THEN CONCAT(plot_no, '#', measure_name, '#', send_date) ELSE NULL END) VIRTUAL COMMENT '去重槽=地块码#事由名目#日期（最长64+1+128+1+10=204）；只许已发出行占位',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plot_measure_day (success_slot),
  KEY idx_run_date (run_date),
  KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办发出流水（兼同地块同事由一日一条去重账）';

-- ============ 五、法定节假日种子（2026 国庆；调休补班日不计入，周末也不剔） ============
INSERT IGNORE INTO t_ctld_task_off_day (id, off_date, off_kind, off_name, del_flag, create_by, create_time)
VALUES (20261001, '2026-10-01', 'HOLIDAY', '国庆节法定节假日第一日', 0, 'seed', NOW()),
       (20261002, '2026-10-02', 'HOLIDAY', '国庆节法定节假日第二日', 0, 'seed', NOW()),
       (20261003, '2026-10-03', 'HOLIDAY', '国庆节法定节假日第三日', 0, 'seed', NOW());
