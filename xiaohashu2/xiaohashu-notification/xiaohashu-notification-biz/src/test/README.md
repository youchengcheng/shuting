# 通知模块验证

先在 `xiaohashu2` 目录执行 `mvn -o -pl xiaohashu-notification/xiaohashu-notification-biz -am -DskipTests test-compile`。此命令只编译，不执行既有 MQ 实验测试。避免同时运行其他 Maven 构建写入相同 target。

在 PowerShell 执行 `& ./xiaohashu-notification/xiaohashu-notification-biz/src/test/run-review-tests.ps1`。脚本使用明确版本的本机缓存依赖和 JUnit Platform，只运行命名的通知测试，不扫描整个 Maven 缓存、不启动 Spring 服务或 MQ。Maven 的 surefire-junit-platform 未缓存时也能执行，并在 `target/review-test-counts.json` 输出真实数量。缺少某个缓存依赖会直接报错。

真实 MySQL 测试默认不会运行。只允许独立临时实例：使用 `mysqld --no-defaults` 初始化全新 `notification-biz/target/mysql-review-UUID` 数据目录，绑定 `127.0.0.1` 的独立端口，禁用 mysqlx，创建全新 `notification_review_test_UUID` schema。禁止使用 3306/3307、`xiaohashu` 或任何既有业务数据库。测试在执行 DDL 前还会核对 JDBC URL、schema 命名、服务器端口和实际 `@@datadir`。

临时实例的 JSON 元数据应包含 `testUrl` 与绝对 `dataDir`。仅在已核实隔离的实例上执行 `run-review-tests.ps1 -WithMySql -MySqlMetadataPath <metadata.json>`。集成测试使用真实 Mapper、InnoDB 事务与锁，但用户资料、预览和 Redis 广播均为 mock。每次运行使用不同测试接收人，保留临时库作为验证证据。

完成后重新核对 `@@datadir` 与临时 schema，再通过该独立 TCP 端口调用 `mysqladmin --no-defaults ... shutdown`。不要停止既有 MySQL 服务或递归删除数据目录。

2026-11-14 已验证：Java 17、MySQL 8.0.44，40 项测试实际发现、启动并通过，0 失败、跳过或中止（33 项纯单测、7 项真实 MySQL 测试）。首次真实并发运行复现状态行初始化后的共享锁升级死锁；改为 `ON DUPLICATE KEY UPDATE recipient_id=recipient_id` 后，同一组并发重复、多操作者及已读水位测试全部通过。临时实例已通过身份核对后正常关闭，失败和成功日志、数量 JSON、数据目录保留在本机 target。
