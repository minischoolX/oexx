package org.edx.mobile.viewModel

import android.app.Activity
import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.edx.mobile.R
import org.edx.mobile.exception.ErrorMessage
import org.edx.mobile.extenstion.decodeToLong
import org.edx.mobile.http.model.NetworkResponseCallback
import org.edx.mobile.http.model.Result
import org.edx.mobile.inapppurchases.BillingProcessor
import org.edx.mobile.model.iap.AddToBasketResponse
import org.edx.mobile.model.iap.CheckoutResponse
import org.edx.mobile.model.iap.ExecuteOrderResponse
import org.edx.mobile.module.analytics.Analytics
import org.edx.mobile.module.analytics.InAppPurchasesAnalytics
import org.edx.mobile.repository.InAppPurchasesRepository
import org.edx.mobile.util.InAppPurchasesException
import org.edx.mobile.util.InAppPurchasesUtils
import javax.inject.Inject

@HiltViewModel
class InAppPurchasesViewModel @Inject constructor(
    private val billingProcessor: BillingProcessor,
    private val repository: InAppPurchasesRepository,
    private val iapAnalytics: InAppPurchasesAnalytics
) : ViewModel() {

    private val _showLoader = MutableLiveData(false)
    val showLoader: LiveData<Boolean> = _showLoader

    private val _errorMessage = MutableLiveData<ErrorMessage?>()
    val errorMessage: LiveData<ErrorMessage?> = _errorMessage

    private val _executeResponse = MutableLiveData<ExecuteOrderResponse>()
    val executeResponse: LiveData<ExecuteOrderResponse> = _executeResponse

    private val _showFullscreenLoaderDialog = MutableLiveData(false)
    val showFullscreenLoaderDialog: LiveData<Boolean> = _showFullscreenLoaderDialog

    private val _refreshCourseData = MutableLiveData(false)
    val refreshCourseData: LiveData<Boolean> = _refreshCourseData

    private val _productPurchased = MutableLiveData<Purchase>()
    val productPurchased: LiveData<Purchase> = _productPurchased

    private val _purchaseFlowComplete = MutableLiveData(false)
    val purchaseFlowComplete: LiveData<Boolean> = _purchaseFlowComplete

    private val _productPrice = MutableLiveData<SkuDetails>()
    val productPrice: LiveData<SkuDetails> = _productPrice

    private val _purchasesList = MutableLiveData<List<Pair<String, String>>>()
    val purchasesList: LiveData<List<Pair<String, String>>> = _purchasesList

    var upgradeMode = UpgradeMode.NORMAL

    private var _productId: String = ""
    val productId: String
        get() = _productId

    private var _isVerificationPending = true
    val isVerificationPending: Boolean
        get() = _isVerificationPending

    private val listener = object : BillingProcessor.BillingFlowListeners {
        override fun onPurchaseCancel(responseCode: Int, message: String) {
            super.onPurchaseCancel(responseCode, message)
            dispatchError(
                errorCode = responseCode,
                errorType = ErrorMessage.PAYMENT_SDK_CODE,
                errorMessage = message,
                errorResId = R.string.error_payment_not_processed
            )
            endLoading()
        }

        override fun onPurchaseComplete(purchase: Purchase) {
            super.onPurchaseComplete(purchase)
            purchaseToken = purchase.purchaseToken
            _productPurchased.postValue(purchase)
            iapAnalytics.trackIAPEvent(eventName = Analytics.Events.IAP_PAYMENT_TIME)
            iapAnalytics.initUnlockContentTime()
        }
    }

    private var basketId: Long = 0
    private var purchaseToken: String = ""

    init {
        billingProcessor.setUpBillingFlowListeners(listener)
        billingProcessor.startConnection()
    }

    fun initializeProductPrice(courseSku: String?) {
        iapAnalytics.initPriceTime()
        courseSku?.let {
            billingProcessor.querySyncDetails(
                productId = courseSku
            ) { billingResult, skuDetails ->
                val skuDetail = skuDetails?.get(0)
                skuDetail?.let {
                    if (it.sku == courseSku) {
                        _productPrice.postValue(it)
                        iapAnalytics.setPrice(skuDetail.price)
                        iapAnalytics.trackIAPEvent(Analytics.Events.IAP_LOAD_PRICE_TIME)
                    }
                } ?: dispatchError(
                    errorCode = billingResult.responseCode,
                    errorType = ErrorMessage.PRICE_CODE,
                    errorMessage = billingResult.debugMessage,
                    errorResId = R.string.error_price_not_fetched,
                )
            }
        } ?: dispatchError(
            errorType = ErrorMessage.PRICE_CODE,
            errorResId = R.string.error_price_not_fetched,
        )
    }

    fun queryPurchases(userId: Long) {
        billingProcessor.queryPurchase { _, purchases ->
            if (purchases.isEmpty()) {
                return@queryPurchase
            }
            val purchasesList = purchases.filter {
                it.accountIdentifiers?.obfuscatedAccountId?.decodeToLong() == userId
            }.associate { it.skus[0] to it.purchaseToken }.toList()
            _purchasesList.postValue(purchasesList)
        }
    }

    fun addProductToBasket(activity: Activity, userId: Long, productId: String) {
        this._productId = productId
        startLoading()
        repository.addToBasket(
            productId = productId,
            callback = object : NetworkResponseCallback<AddToBasketResponse> {
                override fun onSuccess(result: Result.Success<AddToBasketResponse>) {
                    result.data?.let {
                        proceedCheckout(activity, userId, it.basketId)
                    } ?: endLoading()
                }

                override fun onError(error: Result.Error) {
                    endLoading()
                    dispatchError(
                        errorResId = InAppPurchasesUtils.getErrorMessage(error.throwable),
                        errorType = ErrorMessage.ADD_TO_BASKET_CODE,
                        throwable = error.throwable
                    )
                }
            })
    }

    fun proceedCheckout(activity: Activity, userId: Long, basketId: Long) {
        this.basketId = basketId
        repository.proceedCheckout(
            basketId = basketId,
            callback = object : NetworkResponseCallback<CheckoutResponse> {
                override fun onSuccess(result: Result.Success<CheckoutResponse>) {
                    result.data?.let {
                        if (upgradeMode.isSilentMode()) {
                            executeOrder()
                        } else {
                            if (it.paymentPageUrl.isNotEmpty()) {
                                iapAnalytics.initPaymentTime()
                                billingProcessor.purchaseItem(activity, productId, userId)
                            }
                        }
                    } ?: endLoading()
                }

                override fun onError(error: Result.Error) {
                    endLoading()
                    dispatchError(
                        errorType = ErrorMessage.CHECKOUT_CODE,
                        errorResId = InAppPurchasesUtils.getErrorMessage(error.throwable),
                        throwable = error.throwable
                    )
                }
            })
    }

    fun executeOrder() {
        _isVerificationPending = false
        repository.executeOrder(
            basketId = basketId,
            productId = productId,
            purchaseToken = purchaseToken,
            callback = object : NetworkResponseCallback<ExecuteOrderResponse> {
                override fun onSuccess(result: Result.Success<ExecuteOrderResponse>) {
                    result.data?.let {
                        orderExecuted()
                        if (upgradeMode.isSilentMode()) _executeResponse.postValue(it)
                        else refreshCourseData(true)
                    }
                    endLoading()
                }

                override fun onError(error: Result.Error) {
                    endLoading()
                    dispatchError(
                        errorType = ErrorMessage.EXECUTE_ORDER_CODE,
                        errorResId = InAppPurchasesUtils.getErrorMessage(error.throwable),
                        throwable = error.throwable
                    )
                }
            })
    }

    fun dispatchError(
        errorCode: Int = 0,
        errorType: Int = 0,
        httpErrorCode: Int = 0,
        errorMessage: String? = null,
        errorResId: Int = 0,
        throwable: Throwable? = null
    ) {
        var actualErrorMessage = errorMessage
        var actualErrorResId = errorResId
        throwable?.let {
            if (TextUtils.isEmpty(errorMessage)) {
                actualErrorMessage = it.message
            }
            if (errorResId == 0) {
                actualErrorResId = InAppPurchasesUtils.getErrorMessage(it)
            }
        }
        _errorMessage.postValue(
            ErrorMessage(
                errorCode = errorType,
                errorResId = actualErrorResId,
                throwable = InAppPurchasesException(
                    errorCode = errorCode,
                    httpErrorCode = httpErrorCode,
                    errorMessage = actualErrorMessage
                )
            )
        )
    }

    /**
     * To detect and handle courses which are purchased but still not Verified
     *
     * @param activity: context of the activity trigger to handle the incomplete purchases flow.
     * @param userId: handle the incomplete purchases flow for the user.
     * @param incompletePurchases: A list of pairs of SKU and PurchaseToken of purchased courses
     * from Payment SDK.
     *
     * @return incompletePurchases after dropout the verified one.
     */
    fun handleIncompletePurchasesFlow(
        activity: Activity,
        userId: Long,
        incompletePurchases: MutableList<Pair<String, String>>
    ): MutableList<Pair<String, String>> {
        viewModelScope.launch {
            completeCoursePurchaseFlow(activity, userId, incompletePurchases)
        }
        return incompletePurchases.drop(1).toMutableList()
    }

    private fun completeCoursePurchaseFlow(
        activity: Activity,
        userId: Long,
        incompletePurchases: MutableList<Pair<String, String>>
    ) {
        upgradeMode = UpgradeMode.SILENT
        purchaseToken = incompletePurchases[0].second
        addProductToBasket(activity, userId, incompletePurchases[0].first)
    }

    private fun orderExecuted() {
        _productId = ""
        basketId = 0
    }

    fun errorMessageShown() {
        _errorMessage.value = null
    }

    private fun startLoading() {
        _showLoader.postValue(true)
    }

    fun endLoading() {
        _showLoader.postValue(false)
    }

    fun showFullScreenLoader(show: Boolean) {
        _showFullscreenLoaderDialog.value = show
    }

    fun refreshCourseData(refresh: Boolean) {
        _refreshCourseData.postValue(refresh)
    }

    // To refrain the View Model from emitting further observable calls
    fun resetPurchase(complete: Boolean) {
        upgradeMode = UpgradeMode.NORMAL
        _isVerificationPending = true
        _purchaseFlowComplete.postValue(complete)
    }

    override fun onCleared() {
        billingProcessor.disconnect()
        super.onCleared()
    }

    enum class UpgradeMode {
        NORMAL,
        SILENT;

        fun isSilentMode() = this == SILENT

        fun isNormalMode() = this == NORMAL
    }
}
