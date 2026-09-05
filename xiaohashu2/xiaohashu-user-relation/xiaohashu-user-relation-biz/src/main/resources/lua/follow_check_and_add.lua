--lua脚本:校验并添加关注关系

local key = KEYS[1] --操作的 redis key
local followUserId = ARGV[1] --关注的用户id
local timestamp = ARGV[2] --时间戳

--使用EXISTS 命令检查zset是否存在
local exists = redis.call('EXISTS',key)
if exists == 0 then
    return -1
end

--校验关注人数是否达到上限（1000）
local size = redis.call('ZCARD',key)
if size >= 1000 then
    return -2
end

--校验目标用户是否已经关注
if redis.call('ZSCORE',key,followUserId) then
    return -3
end

--ZADD 添加关注关系
redis.call('ZADD',key,timestamp,followUserId)
return 0
