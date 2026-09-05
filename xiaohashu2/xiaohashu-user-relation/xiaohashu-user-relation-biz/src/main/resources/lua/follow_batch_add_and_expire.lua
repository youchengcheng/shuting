-- 操作的 Key
local key = KEYS[1]

-- 准备批量添加数据的参数
local zaddArgs = {}

-- 遍历 ARGV 参数，将分数和值按顺序插入到 zaddArgs 变量中
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs,ARGV[i]) --关注时间
    table.insert(zaddArgs,ARGV[i+1]) --关注的用户id
end

-- 调用 ZADD 批量插入数据
redis.call('ZADD', key,unpack(zaddArgs))

-- 设置 ZSet 的过期时间
local expireTime = ARGV[#ARGV] --最后一个参数为过期时间值
redis.call('EXPIRE',key,expireTime)

return 0

