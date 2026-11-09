local result = redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2]), 'NX')
 if result then
     return 1
 end
 return 0
