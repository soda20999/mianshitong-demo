# 面试通（xiaozhi）

一个基于 Spring Boot + Vue 3 + AI 的面试训练平台，支持简历解析、JD 分析、题目生成、模拟面试、报告导出和管理端运营能力。

## 1. 技术栈

### 后端
- Java 17
- Spring Boot 3.4.2
- Spring Security + JWT
- Spring AI（OpenAI 兼容接口）
- MyBatis-Plus
- MySQL 8
- Redis 7

### 前端
- Vue 3
- Vue Router 4
- Pinia
- Element Plus
- Axios
- Vite 6

## 2. 功能概览

### 用户端
- 注册、登录、退出登录
- 个人资料维护（含头像）
- 简历上传与解析
- JD 分析
- 面试题生成
- 模拟面试（追问与评分）
- 报告查看与 PDF 导出
- 历史记录与题库练习

### 管理端
- 用户管理
- 简历管理
- 面试记录管理
- Prompt 模板管理
- 限流与风控配置
- 敏感词管理
- 审核内容管理
- 系统调用情况（含 AI 调用日志）
- 管理员个人中心

## 3. 项目结构

```text
xiaozhi/
├─ src/                    # 后端源码（Spring Boot）
├─ web/                    # 前端源码（Vue 3）
├─ nginx/                  # Nginx 配置
├─ docker-compose.yml      # Docker 编排
├─ Dockerfile              # 后端镜像构建
└─ .env.example            # 环境变量示例
```

## 4. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+（建议 20/22）
- MySQL 8.x
- Redis 7.x

## 5. 配置说明

先复制环境变量模板并按需修改：

```bash
cp .env.example .env
```

关键变量：
- `JWT_SECRET`：必填，至少 32 字节随机字符串
- `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`：数据库连接
- `NVIDIA_OPENAI_BASE_URL` / `NVIDIA_API_KEY` / `NVIDIA_MODEL`：AI 模型接入
- `CORS_ALLOWED_ORIGINS`：CORS 白名单（逗号分隔）
- `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES`：登录失败锁定策略
- `AUTO_MIGRATE_LEGACY_PASSWORDS`：是否自动迁移历史明文密码为 BCrypt

前端可选变量：
- `VITE_API_BASE_URL`（默认 `/api`）
- `VITE_API_TIMEOUT`（默认 `120000`）
- `VITE_AI_API_TIMEOUT`（默认 `300000`）

## 6. 本地开发启动

### 6.1 启动基础依赖（MySQL / Redis）

如本机未安装，可用 Docker 启动：

```bash
docker compose up -d mysql redis
```

### 6.2 启动后端

```bash
mvn spring-boot:run
```

默认端口：`8080`

说明：
- 默认激活 `dev` 配置（`spring.profiles.active=dev`）
- `dev` 下会自动执行 `schema.sql + data.sql`

### 6.3 启动前端

```bash
cd web
npm install
npm run dev
```

默认端口：`5173`

## 7. Docker 一键部署

```bash
docker compose up --build
```

启动后访问：
- 前端：`http://localhost`
- 后端 API：`http://localhost:8080/api`

说明：
- `web` 容器内 Nginx 会将 `/api/*` 反向代理到 `backend:8080`
- `prod` 配置下默认关闭 SQL 初始化（`spring.sql.init.mode=never`）

## 8. 管理员初始化

项目已移除默认管理员账号。首次请先注册普通用户，再手动提升角色：

```sql
UPDATE xz_user
SET role = 'ADMIN'
WHERE email = 'your-email@example.com';
```

## 9. 安全设计（当前版本）

- 密码使用 BCrypt 存储
- 登录失败按邮箱/IP 计数并锁定
- JWT 鉴权 + 黑名单登出机制
- 请求时回查用户状态与角色
- CORS 使用白名单配置
- 上传文件支持扩展名 + 文件头校验（简历/题库）
- 支持启动时自动迁移历史明文密码

## 10. 常见问题

### Q1：前端提示网络错误
- 检查后端是否已启动（`8080`）
- 检查 `VITE_API_BASE_URL` 配置
- 检查浏览器控制台和后端日志

### Q2：启动时报 JWT 密钥错误
- 设置 `JWT_SECRET`，且长度至少 32 字节
- 不要使用弱示例值

### Q3：Docker 启动失败提示环境变量缺失
- 按 `.env.example` 提供 `MYSQL_*`、`JWT_SECRET` 等必填变量

## 11. 开发建议

提交前建议执行：

```bash
mvn -DskipTests compile
cd web && npm run build
```

公网部署建议：
- 使用强随机密钥与强口令
- 配置 HTTPS
- 收紧 `CORS_ALLOWED_ORIGINS`
