package org.edx.mobile.view.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.SkuDetails
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.edx.mobile.R
import org.edx.mobile.core.IEdxEnvironment
import org.edx.mobile.databinding.DialogUpgradeFeaturesBinding
import org.edx.mobile.exception.ErrorMessage
import org.edx.mobile.extenstion.setVisibility
import org.edx.mobile.http.HttpStatus
import org.edx.mobile.module.analytics.Analytics.Events
import org.edx.mobile.module.analytics.InAppPurchasesAnalytics
import org.edx.mobile.util.AppConstants
import org.edx.mobile.util.InAppPurchasesException
import org.edx.mobile.util.NonNullObserver
import org.edx.mobile.util.ResourceUtil
import org.edx.mobile.viewModel.InAppPurchasesViewModel
import org.edx.mobile.wraper.InAppPurchasesDialog
import javax.inject.Inject

@AndroidEntryPoint
class CourseModalDialogFragment : DialogFragment() {

    private lateinit var binding: DialogUpgradeFeaturesBinding
    private var screenName: String = ""
    private var courseId: String = ""
    private var courseSku: String? = null
    private var isSelfPaced: Boolean = false

    private val iapViewModel: InAppPurchasesViewModel
            by viewModels(ownerProducer = { requireActivity() })

    @Inject
    lateinit var environment: IEdxEnvironment

    @Inject
    lateinit var iapAnalytics: InAppPurchasesAnalytics

    @Inject
    lateinit var iapDialog: InAppPurchasesDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(
            STYLE_NORMAL,
            R.style.AppTheme_NoActionBar
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogUpgradeFeaturesBinding.inflate(inflater)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.attributes?.windowAnimations = R.style.DialogSlideUpAndDownAnimation
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {
        arguments?.let { bundle ->
            screenName = bundle.getString(KEY_SCREEN_NAME, "")
            courseId = bundle.getString(KEY_COURSE_ID, "")
            courseSku = bundle.getString(KEY_COURSE_SKU)
            isSelfPaced = bundle.getBoolean(KEY_IS_SELF_PACED)
            iapAnalytics.initCourseValues(
                courseId = courseId,
                isSelfPaced = isSelfPaced,
                screenName = screenName
            )
            environment.analyticsRegistry.trackValuePropLearnMoreTapped(courseId, screenName)
            environment.analyticsRegistry.trackValuePropModalView(courseId, screenName)
        }

        binding.dialogTitle.text = ResourceUtil.getFormattedString(
            resources,
            R.string.course_modal_heading,
            KEY_COURSE_NAME,
            arguments?.getString(KEY_COURSE_NAME)
        )
        binding.layoutUpgradeBtn.root.setVisibility(environment.config.isIAPEnabled)
        binding.dialogDismiss.setOnClickListener {
            dialog?.dismiss()
        }

        if (environment.config.isIAPEnabled) {
            initIAPObservers()
            // Shimmer container taking sometime to get ready and perform the animation, so
            // by adding the some delay fixed that issue for lower-end devices, and for the
            // proper animation.
            binding.layoutUpgradeBtn.shimmerViewContainer.postDelayed({
                iapViewModel.initializeProductPrice(courseSku)
            }, 1500)
            binding.layoutUpgradeBtn.btnUpgrade.isEnabled = false
        }
    }

    private fun initIAPObservers() {
        iapViewModel.productPrice.observe(viewLifecycleOwner, NonNullObserver { skuDetails ->
            setUpUpgradeButton(skuDetails)
        })

        iapViewModel.showLoader.observe(viewLifecycleOwner, NonNullObserver {
            enableUpgradeButton(!it)
        })

        iapViewModel.errorMessage.observe(viewLifecycleOwner, NonNullObserver { errorMsg ->
            if (errorMsg.throwable is InAppPurchasesException) {
                handleIAPException(errorMsg)
            } else {
                iapDialog.showUpgradeErrorDialog(
                    context = this@CourseModalDialogFragment,
                    errorResId = errorMsg.errorResId,
                    errorType = errorMsg.errorCode
                )
            }
            iapViewModel.errorMessageShown()
        })

        iapViewModel.productPurchased.observe(viewLifecycleOwner, NonNullObserver {
            lifecycleScope.launch {
                iapViewModel.showFullScreenLoader(true)
                dismiss()
            }
        })
    }

    private fun setUpUpgradeButton(skuDetails: SkuDetails) {
        binding.layoutUpgradeBtn.btnUpgrade.text =
            ResourceUtil.getFormattedString(
                resources,
                R.string.label_upgrade_course_button,
                AppConstants.PRICE,
                skuDetails.price
            ).toString()
        // The app get the sku details instantly, so add some wait to perform
        // animation at least one cycle.
        binding.layoutUpgradeBtn.shimmerViewContainer.postDelayed({
            binding.layoutUpgradeBtn.shimmerViewContainer.hideShimmer()
            binding.layoutUpgradeBtn.btnUpgrade.isEnabled = true
        }, 500)

        binding.layoutUpgradeBtn.btnUpgrade.setOnClickListener {
            iapAnalytics.trackIAPEvent(eventName = Events.IAP_UPGRADE_NOW_CLICKED)
            environment.loginPrefs.userId?.let { userId ->
                iapDialog.showSDNDialog(this) { _, _ ->
                    courseSku?.let {
                        iapViewModel.addProductToBasket(
                            requireActivity(),
                            userId,
                            it
                        )
                    } ?: iapDialog.showUpgradeErrorDialog(this)
                }
            }
        }
    }

    private fun handleIAPException(errorMsg: ErrorMessage) {
        errorMsg.throwable as InAppPurchasesException
        when (errorMsg.throwable.httpErrorCode) {
            HttpStatus.UNAUTHORIZED -> {
                environment.router?.forceLogout(
                    requireContext(),
                    environment.analyticsRegistry,
                    environment.notificationDelegate
                )
                return
            }
            HttpStatus.NOT_ACCEPTABLE -> {
                iapDialog.showPostUpgradeErrorDialog(
                    context = this@CourseModalDialogFragment,
                    errorResId = errorMsg.errorResId,
                    errorCode = errorMsg.throwable.httpErrorCode,
                    errorMessage = errorMsg.throwable.errorMessage,
                    errorType = errorMsg.errorCode,
                    retryListener = { _, _ ->
                        iapViewModel.upgradeMode =
                            InAppPurchasesViewModel.UpgradeMode.SILENT
                        iapViewModel.showFullScreenLoader(true)
                        dismiss()
                    },
                    cancelListener = null
                )
            }
            else -> {
                var retryListener: DialogInterface.OnClickListener? = null
                if (errorMsg.canRetry()) {
                    retryListener = DialogInterface.OnClickListener { dialog, which ->
                        when (errorMsg.errorCode) {
                            ErrorMessage.PRICE_CODE -> {
                                iapViewModel.initializeProductPrice(courseSku)
                            }
                        }
                    }
                }
                iapDialog.showUpgradeErrorDialog(
                    context = this@CourseModalDialogFragment,
                    errorResId = errorMsg.errorResId,
                    errorCode = errorMsg.throwable.errorCode,
                    errorMessage = errorMsg.throwable.errorMessage,
                    errorType = errorMsg.errorCode,
                    listener = retryListener
                )
            }
        }
    }

    private fun enableUpgradeButton(enable: Boolean) {
        binding.layoutUpgradeBtn.btnUpgrade.setVisibility(enable)
        binding.layoutUpgradeBtn.loadingIndicator.setVisibility(!enable)
    }

    companion object {
        const val TAG: String = "CourseModalDialogFragment"
        const val KEY_SCREEN_NAME = "screen_name"
        const val KEY_COURSE_ID = "course_id"
        const val KEY_COURSE_SKU = "course_sku"
        const val KEY_COURSE_NAME = "course_name"
        const val KEY_IS_SELF_PACED = "is_Self_Paced"

        @JvmStatic
        fun newInstance(
            screenName: String,
            courseId: String,
            courseSku: String?,
            courseName: String,
            isSelfPaced: Boolean
        ): CourseModalDialogFragment {
            val frag = CourseModalDialogFragment()
            val args = Bundle().apply {
                putString(KEY_SCREEN_NAME, screenName)
                putString(KEY_COURSE_ID, courseId)
                putString(KEY_COURSE_SKU, courseSku)
                putString(KEY_COURSE_NAME, courseName)
                putBoolean(KEY_IS_SELF_PACED, isSelfPaced)
            }
            frag.arguments = args
            return frag
        }
    }
}
