package com.chartboost.helium.chartboostadapter

import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import com.chartboost.heliumsdk.HeliumSdk
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.PartnerLogController
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.*
import com.chartboost.sdk.Chartboost
import com.chartboost.sdk.LoggingLevel
import com.chartboost.sdk.Mediation
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.ads.Interstitial
import com.chartboost.sdk.ads.Rewarded
import com.chartboost.sdk.callbacks.BannerCallback
import com.chartboost.sdk.callbacks.InterstitialCallback
import com.chartboost.sdk.callbacks.RewardedCallback
import com.chartboost.sdk.events.*
import com.chartboost.sdk.privacy.model.CCPA
import com.chartboost.sdk.privacy.model.COPPA
import com.chartboost.sdk.privacy.model.GDPR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Helium Chartboost SDK adapter.
 */
class ChartboostAdapter : PartnerAdapter {
    companion object {
        /**
         * Key for parsing the Chartboost SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "app_id"
    }

    /**
     * A lambda to call for successful Chartboost ad shows.
     */
    private var onShowSuccess: () -> Unit = {}

    /**
     * A lambda to call for failed Chartboost ad shows.
     */
    private var onShowError: (event: ShowEvent, error: ShowError) -> Unit =
        { _: ShowEvent, _: ShowError -> }

    /**
     * Get the Chartboost SDK version.
     */
    override val partnerSdkVersion: String
        get() = Chartboost.getSDKVersion()

    /**
     * Get the Chartboost adapter version.
     *
     * Note that the version string will be in the format of `Helium.Partner.Partner.Partner.Adapter`,
     * in which `Helium` is the version of the Helium SDK, `Partner` is the major.minor.patch version
     * of the partner SDK, and `Adapter` is the version of the adapter.
     */
    override val adapterVersion: String
        get() = BuildConfig.HELIUM_CHARTBOOST_ADAPTER_VERSION

    /**
     * Get the partner name for internal uses.
     */
    override val partnerId: String
        get() = "chartboost"

    /**
     * Get the partner name for external uses.
     */
    override val partnerDisplayName: String
        get() = "Chartboost"

    /**
     * Initialize the Chartboost SDK so that it is ready to request ads.
     *
     * @param context The current [Context].
     * @param partnerConfiguration Configuration object containing relevant data to initialize Chartboost.
     *
     * @return Result.success(Unit) if Chartboost successfully initialized, Result.failure(Exception) otherwise.
     */
    override suspend fun setUp(
        context: Context,
        partnerConfiguration: PartnerConfiguration
    ): Result<Unit> {
        PartnerLogController.log(SETUP_STARTED)

        return suspendCoroutine { continuation ->
            partnerConfiguration.credentials.optString(APPLICATION_ID_KEY).trim()
                .takeIf { it.isNotEmpty() }?.let { appId ->
                    // The server does not provide the app signature. As Chartboost and Helium use
                    // the same app id and app signature, we can pass the app signature to Chartboost
                    // from the Helium SDK.
                    HeliumSdk.getAppSignature()?.let { app_signature ->
                        Chartboost.setLoggingLevel(LoggingLevel.ALL)

                        Chartboost.startWithAppId(
                            context.applicationContext,
                            appId,
                            app_signature
                        ) { startError ->

                            startError?.let {
                                PartnerLogController.log(SETUP_FAILED, "${it.code}")
                                continuation.resume(
                                    Result.failure(
                                        HeliumAdException(
                                            HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED
                                        )
                                    )
                                )
                            } ?: run {
                                PartnerLogController.log(SETUP_SUCCEEDED)
                                continuation.resume(
                                    Result.success(PartnerLogController.log(SETUP_SUCCEEDED))
                                )
                            }
                        }
                    } ?: run {
                        PartnerLogController.log(SETUP_FAILED, "Missing application signature.")
                        continuation.resumeWith(Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED)))
                    }
                } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
                continuation.resumeWith(Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED)))
            }
        }
    }

    /**
     * Chartboost does not have a public method as to whether GDPR applies.
     * If anything was set previously for GDPR, it will be reset when gdpr does not apply.
     *
     * @param context The current [Context].
     * @param gdprApplies True if GDPR applies, false otherwise.
     */
    override fun setGdprApplies(context: Context, gdprApplies: Boolean) {
        PartnerLogController.log(if (gdprApplies) GDPR_APPLICABLE else GDPR_NOT_APPLICABLE)

        if (!gdprApplies) {
            Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
        }
    }

    /**
     * Notify Chartboost of user GDPR consent.
     * @param context The current [Context].
     * @param gdprConsentStatus The user's current GDPR consent status.
     */
    override fun setGdprConsentStatus(context: Context, gdprConsentStatus: GdprConsentStatus) {
        when (gdprConsentStatus) {
            GdprConsentStatus.GDPR_CONSENT_GRANTED -> {
                PartnerLogController.log(GDPR_CONSENT_GRANTED)
                Chartboost.addDataUseConsent(
                    context,
                    GDPR(GDPR.GDPR_CONSENT.BEHAVIORAL)
                )
            }
            GdprConsentStatus.GDPR_CONSENT_DENIED -> {
                PartnerLogController.log(GDPR_CONSENT_DENIED)
                Chartboost.addDataUseConsent(
                    context,
                    GDPR(GDPR.GDPR_CONSENT.NON_BEHAVIORAL)
                )
            }
            else -> {
                PartnerLogController.log(GDPR_CONSENT_UNKNOWN)
                Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
            }
        }
    }

    /**
     * Notify Chartboost of the CCPA compliance.
     * @param context The current [Context].
     * @param hasGrantedCcpaConsent True if the user has granted CCPA consent, false otherwise.
     * @param privacyString The CCPA privacy String.
     */
    override fun setCcpaConsent(
        context: Context,
        hasGrantedCcpaConsent: Boolean,
        privacyString: String?
    ) {
        when (hasGrantedCcpaConsent) {
            true -> {
                PartnerLogController.log(CCPA_CONSENT_GRANTED)
                Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_IN_SALE))
            }
            false -> {
                PartnerLogController.log(CCPA_CONSENT_DENIED)
                Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_OUT_SALE))
            }
        }
    }

    /**
     * Notify Chartboost of the COPPA subjectivity.
     *
     * @param context The current [Context].
     * @param isSubjectToCoppa True if the user is subject to COPPA, false otherwise.
     */
    override fun setUserSubjectToCoppa(context: Context, isSubjectToCoppa: Boolean) {
        PartnerLogController.log(
            if (isSubjectToCoppa) COPPA_SUBJECT
            else COPPA_NOT_SUBJECT
        )

        Chartboost.addDataUseConsent(context, COPPA(isSubjectToCoppa))
    }

    /**
     * Get a bid token if network bidding is supported.
     *
     * @param context The current [Context].
     * @param request The [PreBidRequest] instance containing relevant data for the current bid request.
     *
     * @return A Map of biddable token Strings.
     */
    override suspend fun fetchBidderInformation(
        context: Context,
        request: PreBidRequest
    ): Map<String, String> {
        PartnerLogController.log(BIDDER_INFO_FETCH_STARTED)
        PartnerLogController.log(BIDDER_INFO_FETCH_SUCCEEDED)
        return emptyMap()
    }

    /**
     * Attempt to load a Chartboost ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    override suspend fun load(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        PartnerLogController.log(LOAD_STARTED)

        return when (request.format) {
            AdFormat.BANNER -> loadBannerAd(context, request, partnerAdListener)
            AdFormat.INTERSTITIAL -> loadInterstitialAd(request, partnerAdListener)
            AdFormat.REWARDED -> loadRewardedAd(request, partnerAdListener)
        }
    }

    /**
     * Attempt to show the currently loaded Chartboost ad.
     *
     * @param context The current [Context]
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    override suspend fun show(context: Context, partnerAd: PartnerAd): Result<PartnerAd> {
        PartnerLogController.log(SHOW_STARTED)

        return when (partnerAd.request.format) {
            AdFormat.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }
            AdFormat.INTERSTITIAL -> showInterstitialAd(partnerAd)
            AdFormat.REWARDED -> showRewardedAd(partnerAd)
        }
    }

    /**
     * Discard unnecessary Chartboost ad objects and release resources.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be discarded.
     *
     * @return Result.success(PartnerAd) if the ad was successfully discarded, Result.failure(Exception) otherwise.
     */
    override suspend fun invalidate(partnerAd: PartnerAd): Result<PartnerAd> {
        PartnerLogController.log(INVALIDATE_STARTED)

        return when (partnerAd.request.format) {
            AdFormat.BANNER -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL, AdFormat.REWARDED -> {
                // Chartboost does not have destroy methods for their fullscreen ads.
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
        }
    }

    /**
     * Attempt to load a Chartboost banner ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            val chartboostBanner = Banner(
                context,
                request.partnerPlacement,
                getChartboostAdSize(request.size),
                object : BannerCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
                        PartnerLogController.log(DID_CLICK)
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onAdLoaded(event: CacheEvent, error: CacheError?) {
                        error?.let {
                            PartnerLogController.log(LOAD_FAILED, "${it.code}")
                            continuation.resume(Result.failure(HeliumAdException(getHeliumErrorCode(error))))
                        } ?: run {
                            // Render the Chartboost banner on Main thread immediately after ad loaded.
                            CoroutineScope(Main).launch {
                                event.ad.show()
                            }

                            PartnerLogController.log(LOAD_SUCCEEDED)
                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = event.ad,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
                        }
                    }

                    override fun onAdRequestedToShow(event: ShowEvent) {}

                    override fun onAdShown(event: ShowEvent, error: ShowError?) {}

                    override fun onImpressionRecorded(event: ImpressionEvent) {
                        PartnerLogController.log(DID_TRACK_IMPRESSION)
                        partnerAdListener.onPartnerAdImpression(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }
                },
                setMediation()
            )

            if (request.adm.isNullOrEmpty()) {
                chartboostBanner.cache()
            } else {
                chartboostBanner.cache(request.adm)
            }
        }
    }

    /**
     * Find the most appropriate Chartboost ad size for the given screen area based on height.
     *
     * @param size The [Size] to parse for conversion.
     *
     * @return The Chartboost ad size that best matches the given [Size].
     */
    private fun getChartboostAdSize(size: Size?) = when (size?.height) {
        in 50 until 90 -> Banner.BannerSize.STANDARD
        in 90 until 250 -> Banner.BannerSize.LEADERBOARD
        in 250 until DisplayMetrics().heightPixels -> Banner.BannerSize.MEDIUM
        else -> Banner.BannerSize.STANDARD
    }

    /**
     * Attempt to load a Chartboost interstitial ad.
     *
     * @param request An [PartnerAdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {

        return suspendCoroutine { continuation ->
            val chartboostInterstitial = Interstitial(
                request.partnerPlacement,
                object : InterstitialCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
                        PartnerLogController.log(DID_CLICK)
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onAdDismiss(event: DismissEvent) {
                        PartnerLogController.log(DID_DISMISS)
                        partnerAdListener.onPartnerAdDismissed(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            ), null
                        )
                    }

                    override fun onAdLoaded(event: CacheEvent, error: CacheError?) {
                        error?.let {
                            PartnerLogController.log(LOAD_FAILED, "$error")
                            continuation.resume(Result.failure(HeliumAdException(getHeliumErrorCode(error))))
                        } ?: run {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = event.ad,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
                        }
                    }

                    override fun onAdRequestedToShow(event: ShowEvent) {}

                    override fun onAdShown(event: ShowEvent, error: ShowError?) {
                        error?.let {
                            onShowError(event, it)
                        } ?: onShowSuccess()
                    }

                    override fun onImpressionRecorded(event: ImpressionEvent) {
                        PartnerLogController.log(DID_TRACK_IMPRESSION)
                        partnerAdListener.onPartnerAdImpression(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }
                },
                setMediation()
            )

            if (request.adm.isNullOrEmpty()) {
                chartboostInterstitial.cache()
            } else {
                chartboostInterstitial.cache(request.adm)
            }
        }
    }

    /**
     * Attempt to load a Chartboost rewarded ad.
     *
     * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            val chartboostRewarded = Rewarded(
                request.partnerPlacement,
                object : RewardedCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
                        PartnerLogController.log(DID_CLICK)
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onAdDismiss(event: DismissEvent) {
                        PartnerLogController.log(DID_DISMISS)
                        partnerAdListener.onPartnerAdDismissed(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            ), null
                        )
                    }

                    override fun onAdLoaded(event: CacheEvent, error: CacheError?) {
                        error?.let {
                            PartnerLogController.log(LOAD_FAILED, "$error")
                            continuation.resume(Result.failure(HeliumAdException(getHeliumErrorCode(error))))
                        } ?: run {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = event.ad,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
                        }
                    }

                    override fun onAdRequestedToShow(event: ShowEvent) {}

                    override fun onAdShown(event: ShowEvent, error: ShowError?) {
                        error?.let {
                            onShowError(event, it)
                        } ?: onShowSuccess()
                    }

                    override fun onImpressionRecorded(event: ImpressionEvent) {
                        PartnerLogController.log(DID_TRACK_IMPRESSION)
                        partnerAdListener.onPartnerAdImpression(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onRewardEarned(event: RewardEvent) {
                        PartnerLogController.log(DID_REWARD)
                        partnerAdListener.onPartnerAdRewarded(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            ),
                            reward = Reward(event.reward, event.adID ?: "")
                        )
                    }
                },
                setMediation()
            )

            if (request.adm.isNullOrEmpty()) {
                chartboostRewarded.cache()
            } else {
                chartboostRewarded.cache(request.adm)
            }
        }
    }

    /**
     * Attempt to show a Chartboost interstitial ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showInterstitialAd(
        partnerAd: PartnerAd
    ): Result<PartnerAd> {
        return (partnerAd.ad)?.let { ad ->
            (ad as? Interstitial)?.let {
                suspendCancellableCoroutine { continuation ->
                    onShowSuccess = {
                        PartnerLogController.log(SHOW_SUCCEEDED)
                        continuation.resume(Result.success(partnerAd))
                    }

                    onShowError = { event, error ->
                        PartnerLogController.log(
                            SHOW_FAILED, "Location: ${event.ad.location}. Error: ${error.code}"
                        )

                        continuation.resume(
                            Result.failure(
                                HeliumAdException(getHeliumErrorCode(error))
                            )
                        )
                    }
                    it.show()
                }
            } ?: run {
                PartnerLogController.log(SHOW_FAILED, "Ad is not Interstitial.")
                Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * Attempt to show a Chartboost rewarded ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showRewardedAd(
        partnerAd: PartnerAd
    ): Result<PartnerAd> {
        return (partnerAd.ad)?.let { ad ->
            (ad as? Rewarded)?.let {
                suspendCancellableCoroutine { continuation ->
                    onShowSuccess = {
                        PartnerLogController.log(SHOW_SUCCEEDED)
                        continuation.resume(Result.success(partnerAd))
                    }

                    onShowError = { event, error ->
                        PartnerLogController.log(
                            SHOW_FAILED, "Location: ${event.ad.location}. Error: ${error.code}"
                        )
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(getHeliumErrorCode(error))
                            )
                        )
                    }
                    it.show()
                }
            } ?: run {
                PartnerLogController.log(SHOW_FAILED, "Ad is not Rewarded.")
                Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * Destroy the current Chartboost banner ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be destroyed.
     *
     * @return Result.success(PartnerAd) if the ad was successfully destroyed, Result.failure(Exception) otherwise.
     */
    private fun destroyBannerAd(partnerAd: PartnerAd): Result<PartnerAd> {
        return partnerAd.ad?.let {
            if (it is Banner) {
                it.detach()

                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            } else {
                PartnerLogController.log(INVALIDATE_FAILED, "Ad is not a Chartboost Banner.")
                Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
            }
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * As Chartboost needs to pass a [Mediation] object to some of the methods.
     * Let's have a method to avoid repetition.
     */
    private fun setMediation(): Mediation {
        return Mediation("Helium", HeliumSdk.getVersion(), adapterVersion)
    }

    /**
     * Convert a given Chartboost error to a [HeliumErrorCode].
     *
     * @param error The Chartboost error to convert.
     *
     * @return The corresponding [HeliumErrorCode].
     */
    private fun getHeliumErrorCode(error: CBError) = when (error) {
        is CacheError -> {
            when (error.code) {
                CacheError.Code.INTERNET_UNAVAILABLE, CacheError.Code.NETWORK_FAILURE -> HeliumErrorCode.NO_CONNECTIVITY
                CacheError.Code.NO_AD_FOUND -> HeliumErrorCode.NO_FILL
                CacheError.Code.SESSION_NOT_STARTED -> HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED
                CacheError.Code.SERVER_ERROR -> HeliumErrorCode.SERVER_ERROR
                else -> HeliumErrorCode.INTERNAL
            }
        }
        is ShowError -> {
            when (error.code) {
                ShowError.Code.INTERNET_UNAVAILABLE -> HeliumErrorCode.NO_CONNECTIVITY
                ShowError.Code.NO_CACHED_AD -> HeliumErrorCode.NO_FILL
                ShowError.Code.SESSION_NOT_STARTED -> HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED
                else -> HeliumErrorCode.PARTNER_ERROR
            }
        }
        else -> HeliumErrorCode.PARTNER_ERROR
    }
}
