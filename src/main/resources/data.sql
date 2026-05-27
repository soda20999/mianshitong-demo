INSERT INTO xz_hot_question (position, tag, content, answer, views, favorites, practices)
SELECT 'Java后端工程师', 'Redis', '缓存穿透、缓存击穿、缓存雪崩分别是什么？如何组合治理？',
       '缓存穿透可以用参数校验、空值缓存和布隆过滤器治理；缓存击穿可以用互斥锁、逻辑过期和热点预热治理；缓存雪崩可以通过过期时间随机化、多级缓存、限流降级和缓存预热降低影响。',
       10, 2, 3
WHERE NOT EXISTS (
    SELECT 1 FROM xz_hot_question
    WHERE position = 'Java后端工程师' AND tag = 'Redis' AND content = '缓存穿透、缓存击穿、缓存雪崩分别是什么？如何组合治理？'
);

INSERT INTO xz_hot_question (position, tag, content, answer, views, favorites, practices)
SELECT 'Java后端工程师', 'MySQL', 'Explain 执行计划里 type=ref 和 type=range 的区别是什么？',
       'ref 表示使用非唯一索引进行等值匹配，通常能定位到一组匹配行；range 表示使用索引范围扫描，例如 between、>、<、in 等条件。一般 ref 的过滤范围更精确，但仍需结合 rows、key、Extra 等字段判断。',
       10, 2, 3
WHERE NOT EXISTS (
    SELECT 1 FROM xz_hot_question
    WHERE position = 'Java后端工程师' AND tag = 'MySQL' AND content = 'Explain 执行计划里 type=ref 和 type=range 的区别是什么？'
);

INSERT INTO xz_hot_question (position, tag, content, answer, views, favorites, practices)
SELECT 'Java后端工程师', 'JVM', '线上 Full GC 频繁，你会按什么顺序排查？',
       '先确认 GC 日志和监控指标，再判断是内存泄漏、对象分配过快、老年代空间不足还是参数不合理。常用工具包括 jstat、jmap、jcmd、MAT，并结合业务流量和发布变更定位原因。',
       10, 2, 3
WHERE NOT EXISTS (
    SELECT 1 FROM xz_hot_question
    WHERE position = 'Java后端工程师' AND tag = 'JVM' AND content = '线上 Full GC 频繁，你会按什么顺序排查？'
);

INSERT INTO xz_hot_question (position, tag, content, answer, views, favorites, practices)
SELECT 'Java后端工程师', 'Spring', 'Spring 事务失效的常见场景有哪些？',
       '常见场景包括方法不是 public、同类内部方法调用绕过代理、异常被捕获未抛出、抛出非回滚异常、事务注解加在不生效的位置、数据库引擎不支持事务等。',
       10, 2, 3
WHERE NOT EXISTS (
    SELECT 1 FROM xz_hot_question
    WHERE position = 'Java后端工程师' AND tag = 'Spring' AND content = 'Spring 事务失效的常见场景有哪些？'
);

INSERT INTO xz_hot_question (position, tag, content, answer, views, favorites, practices)
SELECT 'Java后端工程师', '项目经验', '描述一次你做过的高并发优化，指标前后变化如何？',
       '回答时建议按背景、瓶颈定位、优化动作、压测方法、核心指标变化和风险兜底展开，重点给出 QPS、响应时间、错误率、资源占用等量化结果。',
       10, 2, 3
WHERE NOT EXISTS (
    SELECT 1 FROM xz_hot_question
    WHERE position = 'Java后端工程师' AND tag = '项目经验' AND content = '描述一次你做过的高并发优化，指标前后变化如何？'
);

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'resume-parse', '简历解析 Prompt', '提取技能、项目经历、教育背景和可追问点，输出结构化 JSON。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'resume-parse');

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'jd-analyze', 'JD 分析 Prompt', '从岗位描述中提取核心技能、关键词、面试关注点和补强建议。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'jd-analyze');

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'question-generate', '题目生成 Prompt', '按项目深挖、Redis、MySQL、并发、Spring 等方向生成中高阶面试题。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'question-generate');

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'answer-score', '回答评分 Prompt', '从正确性、完整性、条理性、表达和技术深度评分，并给出改进建议。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'answer-score');

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'interview-follow-up', '追问生成 Prompt', '基于当前问题与用户回答生成一条高质量追问，要求具体、可深化、可评估。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'interview-follow-up');

INSERT INTO xz_prompt_template (module, name, content, updated_at)
SELECT 'report-generate', '报告生成 Prompt', '根据整场问答生成复盘报告，包含强弱点、复习路线和标准表达。', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM xz_prompt_template WHERE module = 'report-generate');

INSERT INTO xz_risk_config (
    id,
    rate_limit_per_minute,
    max_question_generate_per_day,
    max_report_generate_per_day,
    upload_max_mb,
    upload_allow_types,
    input_max_length,
    prompt_injection_check,
    output_safety_check,
    idempotency_check
) VALUES (1, 10, 50, 5, 10, 'pdf,doc,docx', 20000, 1, 1, 1)
ON DUPLICATE KEY UPDATE id = id;

INSERT IGNORE INTO xz_sensitive_word (word, enabled, created_at)
VALUES
    ('违法内容', 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    ('仇恨言论', 1, DATE_SUB(NOW(), INTERVAL 5 DAY));
