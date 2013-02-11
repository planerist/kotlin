// Auto-generated by org.jetbrains.jet.generators.tests.GenerateRangesCodegenTestData. DO NOT EDIT!
import java.util.ArrayList

fun box(): String {
    val list1 = ArrayList<Int>()
    for (i in 3..8 step 2) {
        list1.add(i)
    }
    if (list1 != listOf<Int>(3, 5, 7)) {
        return "Wrong elements for 3..8 step 2: $list1"
    }

    val list2 = ArrayList<Byte>()
    for (i in 3.toByte()..8.toByte() step 2) {
        list2.add(i)
    }
    if (list2 != listOf<Byte>(3, 5, 7)) {
        return "Wrong elements for 3.toByte()..8.toByte() step 2: $list2"
    }

    val list3 = ArrayList<Short>()
    for (i in 3.toShort()..8.toShort() step 2) {
        list3.add(i)
    }
    if (list3 != listOf<Short>(3, 5, 7)) {
        return "Wrong elements for 3.toShort()..8.toShort() step 2: $list3"
    }

    val list4 = ArrayList<Long>()
    for (i in 3.toLong()..8.toLong() step 2.toLong()) {
        list4.add(i)
    }
    if (list4 != listOf<Long>(3, 5, 7)) {
        return "Wrong elements for 3.toLong()..8.toLong() step 2.toLong(): $list4"
    }

    val list5 = ArrayList<Char>()
    for (i in 'a'..'d' step 2) {
        list5.add(i)
    }
    if (list5 != listOf<Char>('a', 'c')) {
        return "Wrong elements for 'a'..'d' step 2: $list5"
    }

    val list6 = ArrayList<Double>()
    for (i in 4.0..5.8 step 0.5) {
        list6.add(i)
    }
    if (list6 != listOf<Double>(4.0, 4.5, 5.0, 5.5)) {
        return "Wrong elements for 4.0..5.8 step 0.5: $list6"
    }

    val list7 = ArrayList<Float>()
    for (i in 4.0.toFloat()..5.8.toFloat() step 0.5.toFloat()) {
        list7.add(i)
    }
    if (list7 != listOf<Float>(4.0, 4.5, 5.0, 5.5)) {
        return "Wrong elements for 4.0.toFloat()..5.8.toFloat() step 0.5.toFloat(): $list7"
    }

    return "OK"
}