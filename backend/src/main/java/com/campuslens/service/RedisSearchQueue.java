package com.campuslens.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisSearchQueue implements SearchQueue {
  private static final String READY_KEY = "campuslens:search:ready";
  private static final String READY_SET_KEY = "campuslens:search:ready:set";
  private static final String PROCESSING_KEY = "campuslens:search:processing";
  private static final String RECEIPTS_KEY = "campuslens:search:receipts";
  private static final String JOB_RECEIPTS_KEY = "campuslens:search:job-receipts";
  private static final String DELAYED_KEY = "campuslens:search:delayed";
  private static final String ACTIVE_KEY = "campuslens:search:active";

  private static final DefaultRedisScript<Long> OFFER_SCRIPT = script("""
      if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 1 then return 1 end
      if redis.call('SCARD', KEYS[3]) >= tonumber(ARGV[2]) then return 0 end
      redis.call('SADD', KEYS[3], ARGV[1])
      if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then
        redis.call('RPUSH', KEYS[1], ARGV[1])
      end
      return 1
      """, Long.class);
  private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = script("""
      redis.call('SADD', KEYS[7], ARGV[1])
      local old = redis.call('HGET', KEYS[5], ARGV[1])
      if old then
        redis.call('ZREM', KEYS[3], old)
        redis.call('HDEL', KEYS[4], old)
        redis.call('HDEL', KEYS[5], ARGV[1])
      end
      redis.call('ZREM', KEYS[6], ARGV[1])
      if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then
        redis.call('RPUSH', KEYS[1], ARGV[1])
      end
      return 1
      """, Long.class);
  private static final DefaultRedisScript<String> RESERVE_SCRIPT = script("""
      local job = redis.call('LPOP', KEYS[1])
      if not job then return nil end
      redis.call('SREM', KEYS[2], job)
      redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
      redis.call('HSET', KEYS[4], ARGV[1], job)
      redis.call('HSET', KEYS[5], job, ARGV[1])
      return job
      """, String.class);
  private static final DefaultRedisScript<Long> ACK_SCRIPT = script("""
      local job = redis.call('HGET', KEYS[2], ARGV[1])
      if not job then return 0 end
      redis.call('ZREM', KEYS[1], ARGV[1])
      redis.call('HDEL', KEYS[2], ARGV[1])
      if redis.call('HGET', KEYS[3], job) == ARGV[1] then
        redis.call('HDEL', KEYS[3], job)
      end
      if ARGV[2] == '1' then
        redis.call('SREM', KEYS[4], job)
        redis.call('ZREM', KEYS[5], job)
        redis.call('SREM', KEYS[7], job)
        redis.call('LREM', KEYS[6], 0, job)
      end
      return 1
      """, Long.class);
  private static final DefaultRedisScript<Long> RETRY_SCRIPT = script("""
      local job = redis.call('HGET', KEYS[2], ARGV[1])
      if not job then return 0 end
      redis.call('ZREM', KEYS[1], ARGV[1])
      redis.call('HDEL', KEYS[2], ARGV[1])
      if redis.call('HGET', KEYS[3], job) == ARGV[1] then
        redis.call('HDEL', KEYS[3], job)
      end
      redis.call('ZADD', KEYS[4], ARGV[2], job)
      return 1
      """, Long.class);
  private static final DefaultRedisScript<Long> PROMOTE_SCRIPT = script("""
      local jobs = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
      local count = 0
      for _, job in ipairs(jobs) do
        if redis.call('ZREM', KEYS[1], job) == 1 then
          if redis.call('SADD', KEYS[3], job) == 1 then
            redis.call('RPUSH', KEYS[2], job)
          end
          count = count + 1
        end
      end
      return count
      """, Long.class);

  private final StringRedisTemplate redis;
  private final int capacity;

  public RedisSearchQueue(
      StringRedisTemplate redis,
      @Value("${campuslens.search.queue-capacity:500}") int capacity) {
    this.redis = redis;
    this.capacity = capacity;
  }

  @Override
  public void enqueueNew(String jobId) {
    execute(OFFER_SCRIPT, List.of(READY_KEY, READY_SET_KEY, ACTIVE_KEY), jobId, String.valueOf(capacity));
  }

  @Override
  public void enqueueExisting(String jobId) {
    execute(ENQUEUE_SCRIPT, queueKeys(), jobId);
  }

  @Override
  public ReservedJob reserve(Duration reservationTimeout) {
    String receipt = UUID.randomUUID().toString();
    String jobId = execute(RESERVE_SCRIPT,
        List.of(READY_KEY, READY_SET_KEY, PROCESSING_KEY, RECEIPTS_KEY, JOB_RECEIPTS_KEY),
        receipt, String.valueOf(System.currentTimeMillis() + reservationTimeout.toMillis()));
    return jobId == null ? null : new ReservedJob(jobId, receipt);
  }

  @Override
  public void acknowledge(ReservedJob reservation, boolean terminal) {
    execute(ACK_SCRIPT, List.of(PROCESSING_KEY, RECEIPTS_KEY, JOB_RECEIPTS_KEY, ACTIVE_KEY,
        DELAYED_KEY, READY_KEY, READY_SET_KEY), reservation.receiptId(), terminal ? "1" : "0");
  }

  @Override
  public void scheduleRetry(ReservedJob reservation, Duration delay) {
    execute(RETRY_SCRIPT, List.of(PROCESSING_KEY, RECEIPTS_KEY, JOB_RECEIPTS_KEY, DELAYED_KEY),
        reservation.receiptId(), String.valueOf(System.currentTimeMillis() + delay.toMillis()));
  }

  @Override
  public void requeueRecovered(String jobId) {
    enqueueExisting(jobId);
  }

  @Override
  public int promoteDue(int limit) {
    Long promoted = execute(PROMOTE_SCRIPT, List.of(DELAYED_KEY, READY_KEY, READY_SET_KEY),
        String.valueOf(System.currentTimeMillis()), String.valueOf(Math.max(1, limit)));
    return promoted == null ? 0 : promoted.intValue();
  }

  @Override
  public Set<String> activeJobIds() {
    try {
      Set<String> values = redis.opsForSet().members(ACTIVE_KEY);
      return values == null ? Set.of() : Set.copyOf(values);
    } catch (DataAccessException ex) {
      throw unavailable(ex);
    }
  }

  @Override
  public void ensureActive(String jobId) {
    try {
      redis.opsForSet().add(ACTIVE_KEY, jobId);
    } catch (DataAccessException ex) {
      throw unavailable(ex);
    }
  }

  @Override
  public void removeActive(String jobId) {
    try {
      redis.opsForSet().remove(ACTIVE_KEY, jobId);
    } catch (DataAccessException ex) {
      throw unavailable(ex);
    }
  }

  private List<String> queueKeys() {
    return List.of(READY_KEY, READY_SET_KEY, PROCESSING_KEY, RECEIPTS_KEY,
        JOB_RECEIPTS_KEY, DELAYED_KEY, ACTIVE_KEY);
  }

  private <T> T execute(DefaultRedisScript<T> script, List<String> keys, String... args) {
    try {
      T result = redis.execute(script, keys, (Object[]) args);
      if (script == OFFER_SCRIPT && (result == null || Long.valueOf(0).equals(result))) {
        throw new SearchQueueFullException("检索队列已满，请稍后重试");
      }
      return result;
    } catch (SearchQueueFullException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      throw unavailable(ex);
    }
  }

  private SearchQueueUnavailableException unavailable(DataAccessException ex) {
    return new SearchQueueUnavailableException("检索队列暂不可用", ex);
  }

  private static <T> DefaultRedisScript<T> script(String text, Class<T> resultType) {
    return new DefaultRedisScript<>(text, resultType);
  }
}
