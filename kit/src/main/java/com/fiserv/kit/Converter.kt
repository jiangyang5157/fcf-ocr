package com.fiserv.kit

interface Converter<in From, in To> {

    fun convert(src: From, dst: To)
}