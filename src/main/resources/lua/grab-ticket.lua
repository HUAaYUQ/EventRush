local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
local quantity = tonumber(ARGV[2] or '1')

if stock < 0 then
    return -3
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

if quantity <= 0 or stock < quantity then
    return -1
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('SADD', KEYS[2], ARGV[1])

return stock - quantity
