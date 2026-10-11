package org.jeecg.modules.monitor.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: redisInfo
 * @author: jeecg-boot
 */
public class RedisInfo {

	private static Map<String, String> map = new HashMap(5);

	static {
		map.put("redis_version", "Redis server version");
		map.put("redis_git_sha1", "Git SHA1");
		map.put("redis_git_dirty", "Git dirty flag");
		map.put("os", "Host operating system of the Redis server");
		map.put("arch_bits", " Architecture (32 or 64 bit)");
		map.put("multiplexing_api", "Event handling mechanism used by Redis");
		map.put("gcc_version", "GCC version used when compiling Redis");
		map.put("process_id", "Server process PID");
		map.put("run_id", "Random identifier of the Redis server (for Sentinel and cluster)");
		map.put("tcp_port", "TCP/IP listening port");
		map.put("uptime_in_seconds", "Seconds elapsed since Redis server startup");
		map.put("uptime_in_days", "Days elapsed since Redis server startup");
		map.put("lru_clock", " Minute-based incrementing clock for LRU management");
		map.put("connected_clients", "Number of connected clients (excluding clients connected via replica servers)");
		map.put("client_longest_output_list", "Among currently connected clients, longest output list");
		map.put("client_longest_input_buf", "Among currently connected clients, maximum input buffer");
		map.put("blocked_clients", "Number of clients waiting on blocking commands (BLPOP, BRPOP, BRPOPLPUSH)");
		map.put("used_memory", "Total memory allocated by Redis allocator, in bytes");
		map.put("used_memory_human", "Returns total Redis allocated memory in human-readable format");
		map.put("used_memory_rss", "From the OS perspective, returns total memory allocated by Redis (aka resident set size). Consistent with top, ps, etc.");
		map.put("used_memory_peak", " Redis peak memory consumption (in bytes)");
		map.put("used_memory_peak_human", "Returns peak Redis memory usage in human-readable format");
		map.put("used_memory_lua", "Memory size used by the Lua engine (in bytes)");
		map.put("mem_fragmentation_ratio", "Ratio between sed_memory_rss and used_memory");
		map.put("mem_allocator", "Memory allocator used by Redis as specified at compile time. Can be libc, jemalloc, or tcmalloc");

		map.put("redis_build_id", "redis_build_id");
		map.put("redis_mode", "Run mode: standalone or cluster");
		map.put("atomicvar_api", "atomicvar_api");
		map.put("hz", "Redis internal scheduler frequency (closes timed-out clients, deletes expired keys, etc.); serverCron runs 10 times per second by design.");
		map.put("executable", "Server script directory");
		map.put("config_file", "Configuration file directory");
		map.put("client_biggest_input_buf", "Among currently connected clients, maximum input buffer; use client list command to observe max values of qbuf and qbuf-free fields");
		map.put("used_memory_rss_human", "Returns total memory allocated by Redis in human-readable form");
		map.put("used_memory_peak_perc", "Peak memory usage");
		map.put("total_system_memory", "System total memory");
		map.put("total_system_memory_human", "Returns total system memory in human-readable form");
		map.put("used_memory_lua_human", "Returns Lua engine memory usage in human-readable form");
		map.put("maxmemory", "Max memory limit, 0 means unlimited");
		map.put("maxmemory_human", "Returns max memory limit in human-readable form");
		map.put("maxmemory_policy", "Eviction policy after exceeding memory limit");
		map.put("loading", "Whether server is loading persistence file");
		map.put("rdb_changes_since_last_save", "Number of write commands since last successful RDB generation, i.e. how many write commands have not been persisted");
		map.put("rdb_bgsave_in_progress", "Whether server is creating RDB file");
		map.put("rdb_last_save_time", "Timestamp since last successful RDB file creation. Current timestamp - rdb_last_save_time = seconds since last successful RDB generation");
		map.put("rdb_last_bgsave_status", "Whether last RDB persistence succeeded");
		map.put("rdb_last_bgsave_time_sec", "Duration in seconds to generate last successful RDB file");
		map.put("rdb_current_bgsave_time_sec", "If the server is creating an rdb file, this field records elapsed seconds of the current create operation");
		map.put("aof_enabled", "Whether AOF is enabled");
		map.put("aof_rewrite_in_progress", "Indicates whether AOF rewrite operation is in progress");
		map.put("aof_rewrite_scheduled", "rewrite task schedule: when client sends bgrewriteaof and a rewrite child process is running, the request is queued and runs after the AOF child process finishes ");

		map.put("aof_last_rewrite_time_sec", "Duration of last AOF rewrite");
		map.put("aof_current_rewrite_time_sec", "If a rewrite operation is in progress, records elapsed time in seconds");
		map.put("aof_last_bgrewrite_status", "Last bgrewriteaof operation status");
		map.put("aof_last_write_status", "Last AOF write status");

		map.put("total_commands_processed", "Number of commands processed by Redis");
		map.put("total_connections_received", "Number of newly created connections; if too many new connections are created, excessive connection creation and destruction affects performance, indicating severe short-lived connections or connection pool misuse—investigate connection settings in code");
		map.put("instantaneous_ops_per_sec", "Current Redis QPS, Redis internal near-real-time commands per second");
		map.put("total_net_input_bytes", "Redis network ingress traffic bytes");
		map.put("total_net_output_bytes", "Redis network egress traffic bytes");

		map.put("instantaneous_input_kbps", "Redis network ingress KPS");
		map.put("instantaneous_output_kbps", "Redis network egress KPS");
		map.put("rejected_connections", "Number of rejected connections; Redis connections reached maxclients limit, number of new connections rejected");
		map.put("sync_full", "Full master-replica sync success count");

		map.put("sync_partial_ok", "Partial master-replica sync success count");
		map.put("sync_partial_err", "Partial master-replica sync failure count");
		map.put("expired_keys", "Number of keys expired since startup");
		map.put("evicted_keys", "Number of keys evicted since startup (after exceeding maxmemory)");
		map.put("keyspace_hits", "Hit count");
		map.put("keyspace_misses", "Miss count");
		map.put("pubsub_channels", "Number of channels currently in use");
		map.put("pubsub_patterns", "Number of modes currently in use");
		map.put("latest_fork_usec", "Time blocked by last fork operation on Redis process, in microseconds");
		map.put("role", "Instance role, master or slave");
		map.put("connected_slaves", "Number of connected slave instances");
		map.put("master_repl_offset", "Master-replica sync offset; same as offset above means no replication lag");
		map.put("repl_backlog_active", "Whether replication backlog buffer is enabled");
		map.put("repl_backlog_size", "Replication backlog buffer size");
		map.put("repl_backlog_first_byte_offset", "Size of offset in replication buffer");
		map.put("repl_backlog_histlen", "This value equals master_repl_offset - repl_backlog_first_byte_offset; it will not exceed repl_backlog_size");
		map.put("used_cpu_sys", "Sum CPU time spent by all Redis main processes in kernel mode");
		map.put("used_cpu_user", "Sum CPU time spent by all Redis main processes in user mode");
		map.put("used_cpu_sys_children", "Sum CPU time spent by background processes in kernel mode");
		map.put("used_cpu_user_children", "Sum CPU time spent by background processes in user mode");
		map.put("cluster_enabled", "Whether instance has cluster mode enabled");
		map.put("db0", "Number of keys in db0, keys with TTL, and average TTL");

	}

	private String key;
	private String value;
	private String description;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
		this.description = map.get(this.key);
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return "RedisInfo{" + "key='" + key + '\'' + ", value='" + value + '\'' + ", desctiption='" + description + '\'' + '}';
	}
}
