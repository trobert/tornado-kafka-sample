import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import model.KafkaRecord
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import tornadofx.Controller
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class KafkaController : Controller() {
    val topics = FXCollections.observableArrayList<String>()!!
    val records = FXCollections.observableArrayList<KafkaRecord>()!!
    val consuming = SimpleBooleanProperty(false)

    private var consumer: KafkaConsumerRunner? = null  // Option ?

    fun refreshTopics(kafka: String) {
        topics.clear()
        val admin = AdminClient.create(mapOf(BOOTSTRAP_SERVERS_CONFIG to kafka))
        admin.listTopics().names()
            .whenComplete { topics, _ ->
                if (topics != null) {
                    Platform.runLater {
                        this.topics.addAll(topics)
                    }
                }
                admin.close()
            }
    }

    fun startConsumer(kafka: String, topic: String) {
        assert(consumer == null) { "start has been called twice without stop" }
        records.clear()
        with(KafkaConsumerRunner(kafka, topic)) {
            consumer = this
            thread { run() }
        }
        consuming.set(true)
    }

    fun stopConsumer() {
        consuming.set(false)
        consumer?.shutdown()
        consumer = null
    }

    inner class KafkaConsumerRunner(kafka: String, val topic: String) : Runnable {
        private val closed = AtomicBoolean(false)
        private val consumer = KafkaConsumer(
            mapOf(
                BOOTSTRAP_SERVERS_CONFIG to kafka,
                AUTO_OFFSET_RESET_CONFIG to "latest",
                ENABLE_AUTO_COMMIT_CONFIG to false,
                GROUP_ID_CONFIG to "dummy"
            ),
            StringDeserializer(),
            StringDeserializer()
        )

        override fun run() {
            consumer.use {
                try {
                    it.subscribe(listOf(topic))
                    while (!closed.get()) {
                        val newRecords = it.poll(Duration.ofMillis(10000))
                        // Handle new records
                        val uiRecords = newRecords.map {
                            KafkaRecord(
                                Instant.ofEpochMilli(it.timestamp()),
                                it.key(),
                                it.partition(),
                                it.offset(),
                                it.value()
                            )
                        }
                        Platform.runLater { records.addAll(uiRecords) }
                    }
                } catch (e: WakeupException) {
                    // Ignore exception if closing
                    if (!closed.get()) throw e
                }
            }
        }

        // Shutdown hook which can be called from a separate thread
        fun shutdown() {
            closed.set(true)
            consumer.wakeup()
        }
    }
}