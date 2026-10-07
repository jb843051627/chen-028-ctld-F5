-- ctld 农时催办《安排单》早班「翻单」增量迁移（chen-028）
-- 与基线 doc/schema/ctld.sql 配套：本脚本只做增量，老的一套收发口径（TCtldTaskItemServiceImpl 的 runOnce/listDue）冻住不改，
-- 翻单另立一条：TaskDispatchEngine + ITCtldTaskDispatchService，不在老本体上动刀。
-- 口径只认翻单这一处：窗口开不开、届期尺、提前几日怎么数、去重键认不认同一桩、主渠道不通转不转备胎、
-- 同一轮跑两回认不认头一回、趟尾三个数从何处取——十问同出一处，页面只复述，别处算出的不作依据。
-- 库：chen_028

-- ============ 一、安排单条目增列（既有列一字不动，只增列） ============
-- 一行只读本行的字：地块码、事由名目、主栽作物、承办岗各在本行落格，不许向左邻右舍借。
ALTER TABLE t_ctld_task_item
  ADD COLUMN plot_code varchar(64) DEFAULT NULL COMMENT '本行地块码（去重键前段，只读本行）' AFTER item_no,
  ADD COLUMN measure_name varchar(128) DEFAULT NULL COMMENT '本行事由名目（去重键后段，只读本行）' AFTER plot_code,
  ADD COLUMN crop_name varchar(128) DEFAULT NULL COMMENT '本行主栽作物（话术四要件之二）' AFTER amount,
  ADD COLUMN handler_post varchar(64) DEFAULT NULL COMMENT '承办岗（话术四要件之四）' AFTER crop_name,
  ADD COLUMN channel varchar(16) DEFAULT NULL COMMENT '本回实走渠道 SMS短信 INNER站内消息；发不成留空' AFTER handler_post,
  ADD COLUMN fail_reason varchar(500) DEFAULT NULL COMMENT '本行失败原由（短信/站内都走不通那句，照原样落本格）' AFTER channel;

-- ============ 二、去重账：同一处地块、同一桩事由，一日内只递一回 ============
-- 键只由本行地块码 + 事由名目两段接成；少接一段、或拿分区代号充当，都不算这个键。
-- 发不成的行不占去重名额：那条路不进这本账，次日条件仍够还得再试。
CREATE TABLE IF NOT EXISTS t_ctld_task_dedup (
  id bigint NOT NULL COMMENT '主键',
  item_id bigint NOT NULL COMMENT '头一回占下这个键的条目 id（留痕，键本身不靠它拼）',
  plot_code varchar(64) NOT NULL COMMENT '地块码（键前段）',
  measure_name varchar(128) NOT NULL COMMENT '事由名目（键后段）',
  dedup_key varchar(256) NOT NULL COMMENT '两段接成的键原文（地块码 + 分隔 + 事由名目），日后翻得出怎么接的',
  dedup_day date NOT NULL COMMENT '占键那一日（一日内只一回；隔日是另一笔，不挡）',
  channel varchar(16) NOT NULL COMMENT '占键这一回走的渠道 SMS/INNER',
  round_no varchar(64) DEFAULT NULL COMMENT '占键的那一趟号',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plot_measure_day (plot_code, measure_name, dedup_day),
  KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办一日去重账（发不成不入账）';

-- ============ 三、趟账：一趟一份，三个数与逐行记号同一次算定 ============
-- 趟尾三栏（挑中/递出/未成）的唯一出处：页面那一屏只是复述，内存里的临时数不许拿来回话。
-- round_no 同一轮第二回照头一趟回话（幂等）：不另落一趟、不动一行、不重发。
CREATE TABLE IF NOT EXISTS t_ctld_task_round (
  id bigint NOT NULL COMMENT '主键',
  round_no varchar(64) NOT NULL COMMENT '趟号（同一轮的唯一凭据；第二回认这个号照头一趟回话）',
  gate_open int NOT NULL COMMENT '本趟窗口开不开 0闸门外未动单 1闸门内动了单',
  picked_rows int NOT NULL COMMENT '趟尾数栏一：本轮挑中几行（至多加满500，装满收手）',
  sent_rows int NOT NULL COMMENT '趟尾数栏二：实际递出几行（短信或站内，落了两处记号才算）',
  failed_rows int NOT NULL COMMENT '趟尾数栏三：几行未成（两条渠道都不通；不占去重名额）',
  held_rows int NOT NULL DEFAULT 0 COMMENT '文面缺件按兵不发几行（仍算挑中，缺项在明细逐行点名，不发也无人代填）',
  dedup_skipped_rows int NOT NULL DEFAULT 0 COMMENT '被一日去重挡下几行（同地块同事由今日已递，不占挑中名额）',
  sms_rows int NOT NULL DEFAULT 0 COMMENT '其中走短信几条',
  inner_rows int NOT NULL DEFAULT 0 COMMENT '其中回落站内几条',
  run_at datetime NOT NULL COMMENT '触发那一刻（按这一刻判窗口、判届期、排提前日）',
  finish_at datetime DEFAULT NULL COMMENT '趟子收尾那一刻',
  reply_text varchar(500) DEFAULT NULL COMMENT '趟尾回话原话（只复述本栏三数；闸门外固定一句：本轮未动单）',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_no (round_no),
  KEY idx_run_at (run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办早班翻单趟账（一趟一份，三数同源于一次算定）';

-- ============ 四、县里停办日历：数提前日时这些日子不计 ============
-- 凑不满一整日的零头不计、法定节假日不计、县里挂出停办的那几日也不计；只数本行自己那一格「至多提前几日」。
CREATE TABLE IF NOT EXISTS t_ctld_task_calendar_off (
  id bigint NOT NULL COMMENT '主键',
  off_date date NOT NULL COMMENT '停办那一一日',
  off_kind varchar(16) NOT NULL COMMENT '来路 STATUTORY法定节假日 COUNTY县里挂出停办',
  off_name varchar(128) DEFAULT NULL COMMENT '日名（如：国庆节、县秋收现场会停办）',
  status int NOT NULL DEFAULT 0 COMMENT '情形 0停办不计日 1已恢复计日',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_off_date (off_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='县里停办日历（数提前工作日时扣除）';

-- ============ 五、演练底册（可选，供人工照演，不影响既有 seed） ============
-- 两条搁着的条目：P9101 正好届期、四项齐全可递；P9102 主栽作物空着，按兵不发并点出缺项。
INSERT IGNORE INTO t_ctld_task_item
  (id, item_no, plot_code, measure_name, due_at, amount, crop_name, handler_post, content, status, del_flag, create_by, create_time)
VALUES (9101, 'TASK9101', 'P9101', '喷施钝化剂', DATE(NOW()), 0, '水稻', '北浚镇农办岗',
        'P9101 钝化剂喷施届期提醒', 0, 0, 'seed', NOW()),
       (9102, 'TASK9102', 'P9102', '田间灌水调控', DATE(NOW()), NULL, NULL, '北浚镇农办岗',
        'P9102 水调控届期提醒（主栽作物空着，话术缺件，按兵不发）', 0, 0, 'seed', NOW());
