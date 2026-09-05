local key = KEYS[1] --操作的redis key
local fansUserId = ARGV[1] --粉丝id
local timestemp = ARGV[2] --时间戳

 --使用exists命令检查zset 粉丝列表是否存在
local exists = redis.call('EXISTS',key)
if exists == 0 then
    return -1
end

--获取粉丝列表大小
local size = redis.call('ZCARD',key)

--若超过5000 个粉丝，移除最早关注的粉丝
if size >= 5000 then
    redis.call('ZPOPMIN',key)
end

--添加新的粉丝关系
redis.call('ZADD',key,timestemp,fansUserId)
return 0