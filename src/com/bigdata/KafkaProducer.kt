package com.bigdata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun main(args: Array<String>) {
    KafkaProducer("localhost:9092").produce(1)
}

class KafkaProducer(brokers: String) {
    private val producer = createProducer(brokers)

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

    private val jsonMapper = ObjectMapper().apply {
        registerKotlinModule()
    }.registerModule(JavaTimeModule())

    fun produce(ratePerSecond: Int) {
        val waitTimeBetweenIterationsMs = 1000L / ratePerSecond

        val files = File("./src/com/bigdata/resources")

        files.walkTopDown()
            .filter { it.isFile }
            .flatMap { file ->
                sequence {
                    file.useLines { lines ->
                        lines.forEach { yield(it) }
                    }
                }
            }
            .filter { !it.contains("trip_id") }
            .map { line ->
                line.split(',').let {
                    Trip(
                        it[0].toInt(), it[1].toInt(), LocalDateTime.parse(it[2], formatter), it[3].toInt(),
                        it[4].toDouble(), it[5], it[6], it[7].toInt(), it[8].toDouble(), it[9]
                    )
                }
            }
            .filter { it.stationId == 283 }
            .forEach {
                val trip = jsonMapper.writeValueAsString(it)
                val futureResult = producer.send(ProducerRecord("input-topic-2", trip))
                Thread.sleep(waitTimeBetweenIterationsMs)
                futureResult.get()
                println(it)
            }
    }

    private fun createProducer(brokers: String): Producer<String, String> {
        val props = Properties()
        props["bootstrap.servers"] = brokers
        props["key.serializer"] = StringSerializer::class.java.canonicalName
        props["value.serializer"] = StringSerializer::class.java.canonicalName
        return KafkaProducer<String, String>(props)
    }
}


data class Trip(
    val id: Int,
    val eventType: Int,  //start_stop – czy rozpoczęcie (0) czy zakończenie (1) przejazdu
    val eventTime: LocalDateTime,
    val stationId: Int,
    val duration: Double,
    val userType: String,
    val gender: String,
    val week: Int,
    val temperature: Double,
    val events: String
)
