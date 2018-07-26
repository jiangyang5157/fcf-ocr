package com.fiserv.kit

interface Mapper<in From, out To> {

    fun map(from: From): To
}