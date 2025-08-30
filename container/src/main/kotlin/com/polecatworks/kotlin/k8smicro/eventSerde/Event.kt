package com.polecatworks.kotlin.k8smicro.eventSerde

import kotlinx.serialization.Serializable


@Serializable
data class Ingredient(val name: String, val sugar: Double, val fat: Double)

@Serializable
sealed class Event {
    @Serializable
    data class Pizza(val name: String, val ingredients: List<Ingredient>, val vegetarian: Boolean, val kcals: Int) :
        Event()

    @Serializable
    data class Burger(val name: String, val ingredients: List<Ingredient>, val kcals: Int) : Event()
}
