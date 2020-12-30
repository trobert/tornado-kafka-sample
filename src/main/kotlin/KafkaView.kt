import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.scene.control.ComboBox
import model.KafkaRecord
import tornadofx.*

class KafkaView : View() {
    val controller: KafkaController by inject()
    val kafka = SimpleStringProperty("localhost:9092")
    var topicCombo: ComboBox<String> by singleAssign()

    override val root =
        vbox {
            title = "Kafka Consumer"
            prefWidth = 800.0

            form {
                fieldset(labelPosition = Orientation.VERTICAL) {
                    field("kafka server:") {
                        textfield(kafka)
                        tooltip("host:port")
                    }
                    field("topic:") {
                        topicCombo = combobox(values = controller.topics) { isEditable = true }
                        button("refresh").action { KafkaController::refreshTopics.runAsync(kafka.value) }
                    }
                }
                buttonbar {
                    button("start consumer...") {
                        action { controller.startConsumer(kafka.value, topicCombo.value) }
                        disableWhen(controller.consuming)
                    }
                    button("stop") {
                        action { controller.stopConsumer() }
                        enableWhen(controller.consuming)
                    }
                }
            }
            tableview(controller.records) {
                columnResizePolicy = SmartResize.POLICY
                readonlyColumn("timestamp", KafkaRecord::date).prefWidth(200)
                readonlyColumn("partition", KafkaRecord::partition)
                readonlyColumn("offset", KafkaRecord::offset)
                readonlyColumn("key", KafkaRecord::key)
                readonlyColumn("value", KafkaRecord::value).remainingWidth()
            }
        }

    override fun onUndock() {
        controller.stopConsumer()
    }
}