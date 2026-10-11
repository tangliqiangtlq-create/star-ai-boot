-- otp_send_rate.lua
-- Multi-dimension rate limit for email OTP send.
-- KEYS[1] = per-email key (otp_rate:{otp}:email:xxx)
-- KEYS[2] = per-ip key (otp_rate:{otp}:ip:xxx)
-- KEYS[3] = global key (otp_rate:{otp}:global)
-- NOTE: the "{otp}" hash tag is mandatory so all keys hash to the same slot on
-- Redis Cluster (Huawei Cloud DCS 7.0 Cluster); without it EVAL fails with CROSSSLOT.
-- ARGV[1] = per-email limit (1)
-- ARGV[2] = per-email ttl seconds (60)
-- ARGV[3] = per-ip limit (10)
-- ARGV[4] = per-ip ttl seconds (3600)
-- ARGV[5] = global limit (1000)
-- ARGV[6] = global ttl seconds (60)
-- Returns: 0 = allowed, 1 = per-email exceeded, 2 = per-ip exceeded, 3 = global exceeded

local function checkAndIncr(key, limit, ttl)
    local current = redis.call('GET', key)
    if current then
        if tonumber(current) >= tonumber(limit) then
            return true
        end
    end
    return false
end

local perEmailKey = KEYS[1]
local perIpKey = KEYS[2]
local globalKey = KEYS[3]

local perEmailLimit = ARGV[1]
local perEmailTtl = ARGV[2]
local perIpLimit = ARGV[3]
local perIpTtl = ARGV[4]
local globalLimit = ARGV[5]
local globalTtl = ARGV[6]

if checkAndIncr(perEmailKey, perEmailLimit, perEmailTtl) then
    return 1
end

if checkAndIncr(perIpKey, perIpLimit, perIpTtl) then
    return 2
end

if checkAndIncr(globalKey, globalLimit, globalTtl) then
    return 3
end

local perEmailCount = redis.call('INCR', perEmailKey)
if perEmailCount == 1 then
    redis.call('EXPIRE', perEmailKey, perEmailTtl)
end

local perIpCount = redis.call('INCR', perIpKey)
if perIpCount == 1 then
    redis.call('EXPIRE', perIpKey, perIpTtl)
end

local globalCount = redis.call('INCR', globalKey)
if globalCount == 1 then
    redis.call('EXPIRE', globalKey, globalTtl)
end

return 0
