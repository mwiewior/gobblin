package com.linkedin.uif.metrics;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.configuration.State;

/**
 * A class that represents a set of metrics associated with a given name.
 *
 * @author ynli
 */
public class JobMetrics implements MetricSet {

    /**
     * Enumeration of metric types.
     */
    public enum MetricType {
        COUNTER, METER, GAUGE
    }

    /**
     * Enumeration of metric groups.
     */
    public enum MetricGroup {
        JOB, TASK
    }

    // Mapping from job ID to metrics set
    private static final ConcurrentMap<String, JobMetrics> METRICS_MAP =
            Maps.newConcurrentMap();

    private final String jobName;
    private final String jobId;
    private final MetricRegistry metricRegistry = new MetricRegistry();

    public JobMetrics(String jobName, String jobId) {
        this.jobName = jobName;
        this.jobId = jobId;
    }

    /**
     * Get a {@link JobMetrics} instance for the given metrics set name.
     *
     * @param jobName job name of this metrics set
     * @param jobId job ID of this metrics set
     * @return {@link JobMetrics} instance for the given metrics set name
     */
    public static JobMetrics get(String jobName, String jobId) {
        METRICS_MAP.putIfAbsent(jobId, new JobMetrics(jobName, jobId));
        return METRICS_MAP.get(jobId);
    }

    /**
     * Remove the {@link JobMetrics} instance for the given metrics set name
     *
     * @param name metrics set name
     * @return removed {@link JobMetrics} instance or <code>null</code> if {@link JobMetrics}
     *         instance for the given metrics set name is not found
     */
    public static JobMetrics remove(String name) {
        return METRICS_MAP.remove(name);
    }

    /**
     * Create a metric name.
     *
     * @param group metric group
     * @param id metric ID
     * @param name metric name
     * @return the concatenated metric name
     */
    public static String metricName(MetricGroup group, String id, String name) {
        return MetricRegistry.name(group.name(), id, name);
    }

    /**
     * Check whether metrics collection and reporting are enabled or not.
     *
     * @param properties Configuration properties
     * @return whether metrics collection and reporting are enabled
     */
    public static boolean isEnabled(Properties properties) {
        return Boolean.valueOf(properties.getProperty(
                ConfigurationKeys.METRICS_ENABLED_KEY,
                ConfigurationKeys.DEFAULT_METRICS_ENABLED));
    }

    /**
     * Check whether metrics collection and reporting are enabled or not.
     *
     * @param state a {@link State} object containing configuration properties
     * @return whether metrics collection and reporting are enabled
     */
    public static boolean isEnabled(State state) {
        return Boolean.valueOf(state.getProp(
                ConfigurationKeys.METRICS_ENABLED_KEY,
                ConfigurationKeys.DEFAULT_METRICS_ENABLED));
    }

    /**
     * Get a list of metric names from a given {@link com.codahale.metrics.Metric}.
     *
     * <p>
     *     Metric name suffices will be added for {@link com.codahale.metrics.Histogram}s and
     *     {@link com.codahale.metrics.Timer}s to distinguish different dimensions (min, max,
     *     median, mean, etc). No suffix will be added for {@link com.codahale.metrics.Counter}s,
     *     {@link com.codahale.metrics.Meter}s, and {@link com.codahale.metrics.Gauge}s, for
     *     which a single dimension is sufficient. Accordingly,
     *     {@link com.linkedin.uif.metrics.JobMetrics#getMetricValue(com.codahale.metrics.Metric)}
     *     will return values of different dimensions for {@link com.codahale.metrics.Histogram}s
     *     and {@link com.codahale.metrics.Timer}s.
     * </p>
     *
     * @param rootName Root metric name
     * @param metric given {@link com.codahale.metrics.Metric}
     * @return a list of metric names from the given {@link com.codahale.metrics.Metric}
     */
    public static List<String> getMetricNames(String rootName, Metric metric) {
        List<String> names = Lists.newArrayList();

        if (metric instanceof Counter || metric instanceof Meter || metric instanceof Gauge) {
            names.add(rootName + MetricNameSuffix.NONE.getSuffix());
        } else if (metric instanceof Histogram) {
            names.add(rootName + MetricNameSuffix.MIN_VALUE.getSuffix());
            names.add(rootName + MetricNameSuffix.MAX_VALUE.getSuffix());
            names.add(rootName + MetricNameSuffix.MEDIAN_VALUE.getSuffix());
            names.add(rootName + MetricNameSuffix.MEAN_VALUE.getSuffix());
            names.add(rootName + MetricNameSuffix.STDDEV_VALUE.getSuffix());
        } else if (metric instanceof Timer) {
            names.add(rootName + MetricNameSuffix.MEAN_EVENT_RATE.getSuffix());
            names.add(rootName + MetricNameSuffix.MIN_DURATION.getSuffix());
            names.add(rootName + MetricNameSuffix.MAX_DURATION.getSuffix());
            names.add(rootName + MetricNameSuffix.MEDIAN_DURATION.getSuffix());
            names.add(rootName + MetricNameSuffix.MEAN_DURATION.getSuffix());
            names.add(rootName + MetricNameSuffix.STDDEV_DURATION.getSuffix());
        }

        return names;
    }

    /**
     * Get a list of values of a given {@link com.codahale.metrics.Metric}.
     *
     * <p>
     *     For {@link com.codahale.metrics.Counter}s, {@link com.codahale.metrics.Counter#getCount()}
     *     is called to get the counts.
     * </p>
     *
     * <p>
     *     For {@link com.codahale.metrics.Meter}s, {@link com.codahale.metrics.Meter#getMeanRate()}
     *     is called to get the mean rates.
     * </p>
     *
     * <p>
     *     For {@link com.codahale.metrics.Gauge}s, {@link com.codahale.metrics.Gauge#getValue()} is
     *     called to get the values.
     * </p>
     *
     * <p>
     *     For {@link com.codahale.metrics.Histogram}s, {@link com.codahale.metrics.Snapshot#getMin()},
     *     {@link com.codahale.metrics.Snapshot#getMax()}, {@link com.codahale.metrics.Snapshot#getMedian()},
     *     {@link com.codahale.metrics.Snapshot#getMean()}, {@link com.codahale.metrics.Snapshot#getStdDev()}
     *     are called to get the min, max, median, mean, and stand-deviation values.
     * </p>
     *
     * <p>
     *     For {@link com.codahale.metrics.Timer}s, {@link com.codahale.metrics.Meter#getMeanRate()} is called
     *     to get the mean rate of event occurrence. Additionally, {@link com.codahale.metrics.Snapshot#getMin()},
     *     {@link com.codahale.metrics.Snapshot#getMax()}, {@link com.codahale.metrics.Snapshot#getMedian()},
     *     {@link com.codahale.metrics.Snapshot#getMean()}, {@link com.codahale.metrics.Snapshot#getStdDev()}
     *     are called to get the min, max, median, mean, and stand-deviation durations of events.
     * </p>
     *
     * @param metric given {@link com.codahale.metrics.Metric}
     * @return a list of values of the given {@link com.codahale.metrics.Metric}
     */
    public static List<Object> getMetricValue(Metric metric) {
        List<Object> values = Lists.newArrayList();

        if (metric instanceof Counter) {
            values.add(((Counter) metric).getCount());
        } else if (metric instanceof Meter) {
            values.add(((Meter) metric).getMeanRate());
        } else if (metric instanceof Gauge) {
            values.add(((Gauge) metric).getValue());
        } else if (metric instanceof Histogram) {
            Snapshot snapshot = ((Histogram) metric).getSnapshot();
            values.add(snapshot.getMin());
            values.add(snapshot.getMax());
            values.add(snapshot.getMedian());
            values.add(snapshot.getMean());
            values.add(snapshot.getStdDev());
        } else if (metric instanceof Timer) {
            Timer timer = (Timer) metric;
            // Mean rate of event occurrence
            values.add(timer.getMeanRate());
            Snapshot snapshot = ((Timer) metric).getSnapshot();
            // Min, max, median, mean, and stand deviation of even duration
            values.add(snapshot.getMin());
            values.add(snapshot.getMax());
            values.add(timer.getSnapshot().getMedian());
            values.add(timer.getSnapshot().getMean());
            values.add(timer.getSnapshot().getStdDev());
        }

        return values;
    }

    /**
     * Get the job name of this metrics set.
     *
     * @return job name of this metrics set
     */
    public String getJobName() {
        return this.jobName;
    }

    /**
     * Get the job ID of this metrics set.
     *
     * @return job ID of this metrics set
     */
    public String getJobId() {
        return this.jobId;
    }

    /**
     * Create a new {@link com.codahale.metrics.Counter}.
     *
     * @param group metric group
     * @param id metric ID
     * @param name metric name
     * @return newly created {@link com.codahale.metrics.Counter}
     */
    public Counter getCounter(MetricGroup group, String id, String name) {
        return metricRegistry.counter(metricName(group, id, name));
    }

    /**
     * Create a new {@link com.codahale.metrics.Counter} with the given name.
     *
     * @param name concatenated metric name
     * @return newly created {@link com.codahale.metrics.Counter}
     */
    public Counter getCounter(String name) {
        return metricRegistry.counter(name);
    }

    /**
     * Get a {@link com.codahale.metrics.Meter}.
     *
     * @param group metric group
     * @param id metric ID
     * @param name metric name
     * @return newly created {@link com.codahale.metrics.Meter}
     */
    public Meter getMeter(MetricGroup group, String id, String name) {
        return metricRegistry.meter(metricName(group, id, name));
    }

    /**
     * Get a {@link com.codahale.metrics.Meter} with the given name.
     *
     * @param name concatenated metric name
     * @return newly created {@link com.codahale.metrics.Meter}
     */
    public Meter getMeter(String name) {
        return metricRegistry.meter(name);
    }

    /**
     * Register a {@link com.codahale.metrics.Gauge}.
     *
     * @param group metric group
     * @param id metric ID
     * @param name metric name
     * @param gauge the {@link com.codahale.metrics.Gauge} to register
     * @param <T> gauge data type
     */
    public <T> Gauge<T> getGauge(MetricGroup group, String id, String name, Gauge<T> gauge) {
        return metricRegistry.register(metricName(group, id, name), gauge);
    }

    /**
     * Register a {@link com.codahale.metrics.Gauge} with the given name.
     *
     * @param name concatenated metric name
     * @param gauge the {@link com.codahale.metrics.Gauge} to register
     * @param <T> gauge data type
     */
    public <T> Gauge<T> getGauge(String name, Gauge<T> gauge) {
        return metricRegistry.register(name, gauge);
    }

    /**
     * Remove the metric object associated with the given name.
     *
     * @param name metric object name
     */
    public void removeMetric(String name) {
        metricRegistry.remove(name);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metricRegistry.getMetrics();
    }
}