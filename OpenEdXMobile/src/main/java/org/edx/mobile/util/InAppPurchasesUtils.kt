package org.edx.mobile.util

import org.edx.mobile.R
import org.edx.mobile.exception.ErrorMessage
import org.edx.mobile.model.api.EnrolledCoursesResponse
import org.edx.mobile.model.course.EnrollmentMode

object InAppPurchasesUtils {

    fun getInCompletePurchases(
        verifiedCoursesSku: List<String>,
        purchases: List<Pair<String, String>>
    ): MutableList<Pair<String, String>> {
        return purchases.filter {
            it.first !in verifiedCoursesSku
        }.toMutableList()
    }

    /**
     * To get verified courses SKUs from enrolled courses response.
     */
    fun getVerifiedCoursesSku(response: List<EnrolledCoursesResponse>): List<String> {
        return response.filter {
            EnrollmentMode.VERIFIED.toString().equals(it.mode, true)
        }.mapNotNull { it.courseSku }.toList()
    }

    fun getErrorMessage(throwable: Throwable) = if (throwable is InAppPurchasesException) {
        when (throwable.httpErrorCode) {
            400 -> when (throwable.errorCode) {
                ErrorMessage.ADD_TO_BASKET_CODE -> R.string.error_course_not_found
                ErrorMessage.CHECKOUT_CODE -> R.string.error_payment_not_processed
                ErrorMessage.EXECUTE_ORDER_CODE -> R.string.error_course_not_fullfilled
                else -> R.string.general_error_message
            }
            403 -> when (throwable.errorCode) {
                ErrorMessage.EXECUTE_ORDER_CODE -> R.string.error_course_not_fullfilled
                else -> R.string.error_user_not_authenticated
            }
            406 -> R.string.error_course_already_paid
            else -> R.string.general_error_message
        }
    } else {
        R.string.general_error_message
    }
}
