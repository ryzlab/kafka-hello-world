package se.ryz.kafka.demo;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import se.ryz.kafka.demo.util.Common;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/*
Two methods to reprocess, start consumer with new ID or reset partition offsets

Create a Topic with two partitions

 # Set necessary variable(s)

 TOPIC_NAME=reprocess-messages

  # Delete Topic if it exists
 kafka-topics --delete \
 --if-exists \
 --topic $TOPIC_NAME \
 --zookeeper localhost:2181,localhost:2182,localhost:2183

 # Create Topic
 PARTITION_COUNT=3
 REPLICATION_FACTOR=2

 kafka-topics --create \
 --topic $TOPIC_NAME \
 --partitions $PARTITION_COUNT \
 --replication-factor $REPLICATION_FACTOR \
 --if-not-exists \
 --config min.insync.replicas=2 \
 --zookeeper localhost:2181,localhost:2182,localhost:2183

 # Describe the Topic
 kafka-topics --describe --topic $TOPIC_NAME --zookeeper localhost:2181,localhost:2182,localhost:2183

 # Look at consumer offset
 kafka-consumer-groups --bootstrap-server localhost:9092  --group sameGroupIdBetweenRuns --describe

 */
public class ReprocessMessages {

    private static final String TOPIC_NAME = "reprocess-messages";

    /**
     * Run the producer once to produce records with null key.
     * This will use round-robin scheme when producing messages to partitions. Each partition
     * will get every other message
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void runProducerWithNullKey() throws InterruptedException, ExecutionException {
        Common common = new Common();
        common.runProducerWithNullKey(TOPIC_NAME, "reprocessMessagesProducer");
    }


    /**
     * Run the consumer once and see that it consumes messages. Restart and see that
     * messages are consumed from where the previous run stopped processing.
     * We will not reprocess anything.
     * Stop the consumer
     * @throws InterruptedException
     */
    @Test
    public void runConsumerWithSameGroupId() throws InterruptedException {
        Common common = new Common();
        common.runSubscriptionConsumer(TOPIC_NAME, "sameGroupIdBetweenRuns", "rebalancingConsumerId");
    }
    /**
     * Run the consumer and see that it consumes messages from the beginning every time since client id always changes.
     * @throws InterruptedException
     */
    @Test
    public void runConsumerWithUUIDGroupId() throws InterruptedException {
        Common common = new Common();
        common.runSubscriptionConsumer(TOPIC_NAME, UUID.randomUUID().toString(), "client1");
    }

    /**
     * Run multiple times and see that we successfully seek to the beginning event if we have the
     * same group ID between runs.
     */
    @Test
    public void resetOffsetsAndRunConsumer() {
        Common common = new Common();
        String groupId = "sameGroupidBetweenRunsButResetOffsets";
        String clientId = null ;
        Properties consumerConfig = common.createConsumerConfig(groupId, clientId);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig);

        // Retrieve information about the topic partitions
        List<PartitionInfo> partitionInfoList = consumer.partitionsFor(TOPIC_NAME);
        // Create a list of TopicPartition objects so that we can assign them to the consumer
        List<TopicPartition> partitions = partitionInfoList.stream()
                .map(partitionInfo -> new TopicPartition(TOPIC_NAME, partitionInfo.partition()))
                .collect(Collectors.toList());

        // Print offsets
        partitions.forEach(partition -> System.out.println (TOPIC_NAME + ", partition: " + partition.partition() + ", committed: " + consumer.committed(partition) /*+ ", position: " + consumer..position(partition)*/));

        // Assign consumer the list of created TopicPartitions
        consumer.assign(partitions);
        // Tell the consumer to seek to beginning for all its partitions
        consumer.seekToBeginning(partitions);

        for (;;) {
            ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofSeconds(5));
            if (consumerRecords.isEmpty()) {
                System.out.println("No records received") ;
            } else {
                for (Iterator<ConsumerRecord<String, String>> it = consumerRecords.iterator(); it.hasNext();) {
                    ConsumerRecord<String, String> record = it.next();
                    System.out.println ("   " + record.topic() + ", partition: " + record.partition() + ", key: " + record.key() + ", value: " + record.value());
                }
            }

        }
    }


}
