local key    = KEYS[1]
local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local current = redis.call("INCR", key)

-- 自愈:仅当无 TTL 时设置(创建窗口 / 修复崩溃残留,且不延长已有窗口)
redis.call("EXPIRE", key, window, "NX")

if current > limit then
    return 0
end
return 1
