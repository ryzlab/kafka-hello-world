package se.ryz.kafka.demo;

import com.github.javafaker.Faker;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.Test;
import se.ryz.kafka.demo.util.Common;

import java.util.Properties;

/*

Look in doc/exercises/ChuckNorrisFact.adoc for instructions
 */
public class ChuckNorrisFact {

    /**
     * Connects to the Kafka Cluster, creates a Producer and
     * produces facts about Chuck Norris at a timely interval
     * @throws InterruptedException Don't really think so...
     */
    @Test
    public void chuckNorrisFactProducer() throws InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Common.KAFKA_BROKERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "chuck-norris-fact-producer-v1.0");
        KafkaProducer<String, String> quoteProducer = new KafkaProducer<>(props);
        Faker faker = new Faker();
        while (true) {
            String key = faker.superhero().name();
            String fact = faker.chuckNorris().fact();
            ProducerRecord<String, String> chuckNorrisFactRecord = new ProducerRecord<>("chuck-norris-fact", key, fact);
            quoteProducer.send(chuckNorrisFactRecord);
            quoteProducer.flush();
            System.out.println("Sent record");
            Thread.sleep(4000);
        }
    }

    @Test
    public void chuckNorrisFactShouter() throws InterruptedException {
        final Properties streamsConfiguration = new Properties();
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, Common.KAFKA_BROKERS);
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run. A new Application ID -> consume from beginning
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "chuck-norris-shouter-v1.0");

        // Specify (de)serializers for record keys values.
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        //props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class);
        // If we do not commit and restart, we will get messages since last commit again when we restart
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "5000");

        final StreamsBuilder builder = new StreamsBuilder();

        final KStream<String, String> messageStream = builder.stream("chuck-norris-fact");
        messageStream
                .map((whoSaidId, quote) -> new KeyValue(whoSaidId, quote.toUpperCase()))
                .to("chuck-norris-shout-fact");

        // Create and start the stream
        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        System.out.println("Check KSQL for output ");
        for (; ; ) {
            Thread.sleep(1000);
        }

    }

}
