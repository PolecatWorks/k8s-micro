package com.polecatworks.kotlin.k8smicro.models

import kotlinx.serialization.Serializable

@Serializable
data class Alive(val name: String, val good: Boolean)
