package com.polecatworks.kotlin.k8smicro.eventSerde

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class Ingredient(
    val name: String,
    val sugar: Double,
    val fat: Double,
)

@Serializable
sealed class Event {
    @Serializable
    @SerialName("com.polecatworks.event.Pizza")
    data class Pizza(
        val name: String,
        val ingredients: List<Ingredient>,
        val vegetarian: Boolean,
        val kcals: Int,
    ) : Event()

    @Serializable
    @SerialName("com.polecatworks.event.Burger")
    data class Burger(
        val name: String,
        val ingredients: List<Ingredient>,
        val kcals: Int,
    ) : Event()

    @Serializable
    @SerialName("com.polecatworks.chaser.Chaser")
    data class Chaser(
        val name: String,
        val id: String,
        val sent: Long,
        val ttl: Long,
        val previous: Long?,
    ) : Event()

    companion object {
        fun subClasses(): List<KClass<out Event>> = Event::class.sealedSubclasses
    }
}
