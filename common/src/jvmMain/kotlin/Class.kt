package dev.kord.common

import dev.kord.common.annotation.KordInternal
import kotlin.reflect.KClass
import kotlin.jvm.java as getJavaClass

/** @suppress */
public actual typealias Class<T> = java.lang.Class<T>

/** @suppress */
@KordInternal
public actual inline val <T : Any> KClass<T>.java: Class<T> inline get() = getJavaClass
