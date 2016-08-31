package gobblin.converter;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import gobblin.configuration.WorkUnitState;
import gobblin.metrics.Metric;
import gobblin.metrics.MetricReport;
import gobblin.metrics.reporter.util.AvroBinarySerializer;
import gobblin.metrics.reporter.util.AvroSerializer;
import gobblin.metrics.reporter.util.NoopSchemaVersionWriter;
import gobblin.util.AvroUtils;

public class GobblinMetricsFlattenerConverterTest {

  @Test
  public void test() throws Exception {

    MetricReport metricReport = new MetricReport();
    metricReport.setTags(ImmutableMap.of("tag", "value", "tag2", "value2"));
    metricReport.setTimestamp(10L);
    metricReport.setMetrics(Lists.newArrayList(new Metric("metric", 1.0), new Metric("metric2", 2.0)));

    AvroSerializer<MetricReport> serializer =
        new AvroBinarySerializer<>(MetricReport.SCHEMA$, new NoopSchemaVersionWriter());
    serializer.serializeRecord(metricReport);

    Schema metricReportUtf8 = new Schema.Parser().parse(this.getClass().getClassLoader().getResourceAsStream("MetricReport.avsc"));
    GenericRecord genericRecordMetric = AvroUtils.slowDeserializeGenericRecord(serializer.serializeRecord(metricReport), metricReportUtf8);

    GobblinMetricsFlattenerConverter converter = new GobblinMetricsFlattenerConverter();

    Schema outputSchema = converter.convertSchema(MetricReport.SCHEMA$, new WorkUnitState());
    Iterable<GenericRecord> converted = converter.convertRecord(outputSchema, genericRecordMetric, new WorkUnitState());
    List<GenericRecord> convertedList = Lists.newArrayList(converted);

    Assert.assertEquals(convertedList.size(), 2);

    Assert.assertEquals(Sets.newHashSet((List<Utf8>) convertedList.get(0).get("tags")),
        Sets.newHashSet("tag:value", "tag2:value2"));
    Assert.assertEquals(convertedList.get(0).get("timestamp"), 10L);
    Assert.assertEquals(convertedList.get(0).get("metricName").toString(), "metric");
    Assert.assertEquals(convertedList.get(0).get("metricValue"), 1.0);

    Assert.assertEquals(Sets.newHashSet((List<Utf8>) convertedList.get(1).get("tags")),
        Sets.newHashSet("tag:value", "tag2:value2"));
    Assert.assertEquals(convertedList.get(1).get("timestamp"), 10L);
    Assert.assertEquals(convertedList.get(1).get("metricName").toString(), "metric2");
    Assert.assertEquals(convertedList.get(1).get("metricValue"), 2.0);

  }

}
