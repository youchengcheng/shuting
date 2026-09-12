--lua脚本: 校验是否已关注

local key = KEYS[1] -- 关注列表 key
local followUserId = ARGV[1] -- 目标用户 id

-- 关注列表缓存不存在，返回 -1，由应用层回源数据库
if redis.call('EXISTS', key) == 0 then
    return -1
end

-- 已关注，返回 1
if redis.call('ZSCORE', key, followUserId) then
    return 1
end

-- 未关注，返回 0
return 0
