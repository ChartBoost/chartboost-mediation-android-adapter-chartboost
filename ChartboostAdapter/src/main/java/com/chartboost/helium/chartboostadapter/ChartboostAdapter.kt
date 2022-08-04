package com.chartboost.helium.chartboostadapter

import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import com.chartboost.heliumsdk.HeliumSdk
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.LogController
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
import com.chartboost.sdk.privacy.model.GDPR
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Helium Chartboost SDK adapter.
 */
class ChartboostAdapter : PartnerAdapter {
    companion object {
        /**
         * The tag used for logging messages.
         */
        private val TAG = "[${this::class.java.simpleName}]"

        /**
         * Key for parsing the Chartboost SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "app_id"
    }

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
        return suspendCoroutine { continuation ->
            partnerConfiguration.credentials[APPLICATION_ID_KEY]?.let { app_id ->
                // The server does not provide the app signature. As Chartboost and Helium use
                // the same app id and app signature, we can pass the app signature to Chartboost
                // from the Helium SDK.
                HeliumSdk.getAppSignature()?.let { app_signature ->
                    Chartboost.setLoggingLevel(LoggingLevel.ALL)

                    Chartboost.startWithAppId(
                        context.applicationContext,
                        app_id,
                        app_signature
                    ) { startError ->

                        startError?.let {
                            LogController.e("$TAG Failed to initialize Chartboost SDK: $it")
                            continuation.resume(
                                Result.failure(
                                    HeliumAdException(
                                        HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED
                                    )
                                )
                            )
                        } ?: run {
                            continuation.resume(
                                Result.success(
                                    LogController.i("$TAG Chartboost SDK successfully initialized.")
                                )
                            )
                        }
                    }
                } ?: run {
                    LogController.e("$TAG Failed to initialize Chartboost SDK: Missing application signature.")
                    continuation.resumeWith(Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED)))
                }
            } ?: run {
                LogController.e("$TAG Failed to initialize Chartboost SDK: Missing application ID.")
                continuation.resumeWith(Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED)))
            }
        }
    }

    /**
     * Chartboost does not have a public method as to whether GDPR applies.
     * If anything was set previously for GDPR, it will be reset when gdpr does not apply.
     */
    override fun setGdprApplies(context: Context, gdprApplies: Boolean) {
        if (!gdprApplies) {
            Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
        }
    }

    /**
     * Notify Chartboost of user GDPR consent.
     *
     * @param gdprConsentStatus The user's current GDPR consent status.
     */
    override fun setGdprConsentStatus(context: Context, gdprConsentStatus: GdprConsentStatus) {
        when (gdprConsentStatus) {
            GdprConsentStatus.GDPR_CONSENT_GRANTED -> Chartboost.addDataUseConsent(
                context,
                GDPR(GDPR.GDPR_CONSENT.BEHAVIORAL)
            )
            GdprConsentStatus.GDPR_CONSENT_DENIED -> Chartboost.addDataUseConsent(
                context,
                GDPR(GDPR.GDPR_CONSENT.NON_BEHAVIORAL)
            )
            else -> Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
        }
    }

    /**
     * Notify Chartboost of the CCPA compliance.
     *
     * @param privacyString The CCPA privacy String.
     */
    override fun setCcpaConsent(
        context: Context,
        hasGivenCcpaConsent: Boolean,
        privacyString: String?
    ) {
        when (hasGivenCcpaConsent) {
            true -> Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_IN_SALE))
            false -> Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_OUT_SALE))
        }
    }

    /**
     * Notify Chartboost of the COPPA subjectivity.
     */
    override fun setUserSubjectToCoppa(context: Context, isSubjectToCoppa: Boolean) {
        // Chartboost does not have an API for setting COPPA. This may be different on 9.x.
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
    ): Map<String, String> = emptyMap()

    /**
     * Attempt to load a Chartboost ad.
     *
     * @param context The current [Context].
     * @param request An [AdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    override suspend fun load(
        context: Context,
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {

        return when (request.format) {
            AdFormat.BANNER -> loadBannerAd(request, context, partnerAdListener)
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
        return when (partnerAd.request.format) {
            AdFormat.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
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
        return when (partnerAd.request.format) {
            AdFormat.BANNER -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL, AdFormat.REWARDED -> {
                // Chartboost does not have destroy methods for their fullscreen ads.
                Result.success(partnerAd)
            }
        }
    }

    /**
     * Attempt to load a Chartboost banner ad.
     *
     * @param context The current [Context].
     * @param request An [AdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        request: AdLoadRequest,
        context: Context,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine {
            val chartboostBanner = Banner(
                context,
                request.partnerPlacement,
                getChartboostAdSize(request.size),
                object: BannerCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
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
                            LogController.d("$TAG failed to load Chartboost banner ad. Chartboost Error Code: ${it.code}")
                            Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL))
                        } ?: run {
                            Result.success(
                                PartnerAd(
                                    ad = event.ad,
                                    details = mapOf(),
                                    request = request
                                )
                            )
                        }
                    }

                    override fun onAdRequestedToShow(event: ShowEvent) {}

                    override fun onAdShown(event: ShowEvent, error: ShowError?) {}

                    override fun onImpressionRecorded(event: ImpressionEvent) {
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

            // TODO: New PartnerController needs to pass adm.
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
     * @param request An [AdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {

        return suspendCoroutine { continuation ->
            val chartboostInterstitial = Interstitial(
                request.partnerPlacement,
                object : InterstitialCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onAdDismiss(event: DismissEvent) {
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
                            LogController.d("$TAG failed to load Chartboost interstitial ad. Chartboost Error: $error")
                            continuation.resume(Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL)))
                        } ?: run {
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

                    override fun onAdRequestedToShow(event: ShowEvent) {
                    }

                    override fun onAdShown(event: ShowEvent, error: ShowError?) {
                    }

                    override fun onImpressionRecorded(event: ImpressionEvent) {
                        partnerAdListener.onPartnerAdDismissed(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            ), null
                        )
                    }
                },
                setMediation()
            )

            // TODO: New PartnerController needs to pass adm.
            if (request.adm.isNullOrEmpty()) {
                chartboostInterstitial.cache()
            } else {
                chartboostInterstitial.cache(request.adm)
            }
        }
    }

    /**
     * Attempt to load an Chartboost rewarded ad.
     *
     * @param request The [AdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            val chartboostRewarded = Rewarded(
                request.heliumPlacement,
                object: RewardedCallback {
                    override fun onAdClicked(event: ClickEvent, error: ClickError?) {
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onAdDismiss(event: DismissEvent) {
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
                            LogController.d("$TAG failed to cache Chartboost rewarded ad. Chartboost Error: $error")
                            continuation.resume(Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL)))
                        } ?: run {
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
                        partnerAdListener.onPartnerAdImpression(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }

                    override fun onRewardEarned(event: RewardEvent) {
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

            // TODO: New PartnerController needs to pass adm.
            if (request.adm.isNullOrEmpty()) {
                chartboostRewarded.cache()
            } else {
                chartboostRewarded.cache(request.adm)
            }
        }
    }

    /**
     * Attempt to show an Chartboost interstitial ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private fun showInterstitialAd(
        partnerAd: PartnerAd
    ): Result<PartnerAd> {
        return partnerAd.ad?.let { ad ->
            (ad as Interstitial).show()
            Result.success(partnerAd)
        } ?: run {
            LogController.e("$TAG Failed to show Chartboost interstitial ad. Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * Attempt to show an Chartboost rewarded ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private fun showRewardedAd(
        partnerAd: PartnerAd
    ): Result<PartnerAd> {
        return partnerAd.ad?.let { ad ->
            (ad as Rewarded).show()
            Result.success(partnerAd)
        } ?: run {
            LogController.e("$TAG Failed to show Chartboost rewarded ad. Ad is null.")
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
                Result.success(partnerAd)
            } else {
                LogController.w("$TAG Failed to destroy Chartboost banner ad. Ad is not a ChartboostBanner.")
                Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
            }
        } ?: run {
            LogController.w("$TAG Failed to destroy Chartboost banner ad. Ad is null.")
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
}
