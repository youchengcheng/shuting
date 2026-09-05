-- LUA 脚本：日增量笔记发布，删除变更数据布隆过滤器

-- 操作的redis key
local key = KEYS[1]
-- redis value
local userId = ARGV[1]

-- 使用exists 命令检测布隆过滤器是否存在
local exists = redis.call('EXISTS',key)
if exists == 0 then
-- 返回结果为0 ，那么布隆过滤器不存在，创建布隆过滤器
    redis.call('BF.ADD',key,'')
-- 设置过期时间，一天以后过期
    redis.call('EXPIRE',key,20*60*60)
end

-- 校验该变更数据是否已经存在（1表示已经存在，0表示不存在）
return redis.call('BF.EXISTS',key,userId)
