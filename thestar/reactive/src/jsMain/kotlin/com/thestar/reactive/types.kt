package com.thestar.reactive

import kotlin.reflect.KClass

/**
 * 未初始化异常。
 *
 * @param message 报错信息
 * @author lignting
 * @since 0.0.1
 */
class UninitializedException(message: String) : RuntimeException(message)

/**
 * 根据当前类返回一个未初始化异常。
 *
 * @receiver 当前类的 KClass 对象
 * @return 异常对象
 * @author lignting
 * @since 0.0.1
 */
fun <T : Any> KClass<T>.uninitializedException() =
    UninitializedException("${this.simpleName} is not initialized")