package org.edx.mobile.util

class InAppPurchasesException(
    errorCode: Int = 0,
    val httpErrorCode: Int = 0,
    val errorMessage: String? = null
) : Exception(errorMessage) {
    val errorCode: Int = errorCode
        get() = if (field == 0) {
            field
        } else {
            httpErrorCode
        }
}
