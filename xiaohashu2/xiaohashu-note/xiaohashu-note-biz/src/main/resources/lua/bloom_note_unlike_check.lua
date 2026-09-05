local key = KEYS[1] --操作redis的key
local noteId = ARGV[1] --笔记id

--使用exists命令检查布隆过滤器是否存在，不存在返回 -1
local exists = redis.call('exists',key)
if exists == 0 then
    return -1
end

--校验该篇笔记是否被点赞过（1表示已经点赞，0表示未点赞）
return redis.call('bf.exists',key,noteId)