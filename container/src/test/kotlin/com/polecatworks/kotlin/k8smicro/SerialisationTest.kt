package com.polecatworks.kotlin.k8smicro

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test

class SerialisationTest {
    // https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#custom-serializers

    @Test
    fun testHello() {
        println("I am another useless test")
    }

    @Serializable
    @SerialName("Color")
    class Color(
        val rgb: Int,
    )

    object ColorAsStringSerializer : KSerializer<Color2> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color2", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: Color2,
        ) {
            val string = value.rgb.toString(16).padStart(6, '0')
            encoder.encodeString(string)
        }

        override fun deserialize(decoder: Decoder): Color2 {
            val string = decoder.decodeString()
            return Color2(string.toInt(16))
        }
    }

    @Serializable(with = ColorAsStringSerializer::class)
    class Color2(
        val rgb: Int,
    )

    @Test
    fun testSimpleSerialisation() {
        println("Start serialisation objects")

        val green = Color(0x00ff00)

        println(Json.encodeToString(green))
        val colorSerializer: KSerializer<Color> = Color.serializer()
        println(colorSerializer.descriptor)

        val green2 = Color2(0x00ff00)

        println("Color2 descriptor >${Color2.serializer().descriptor}<")
        println(Json.encodeToString(green2))

        println()

        val stringToColorMapSerializer: KSerializer<Map<String, Color>> = serializer()
        println("Simple serialisation descriptor>${stringToColorMapSerializer.descriptor}<")

        println("Simple serialisation >$stringToColorMapSerializer<")
    }
}
