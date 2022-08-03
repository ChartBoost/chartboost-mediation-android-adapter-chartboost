package com.chartboost.helium.chartboostadapter

import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import com.chartboost.heliumsdk.HeliumSdk
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.LogController
import com.chartboost_helium.sdk.Banner.BannerSize
import com.chartboost_helium.sdk.Chartboost
import com.chartboost_helium.sdk.ChartboostBanner
import com.chartboost_helium.sdk.ChartboostBannerListener
import com.chartboost_helium.sdk.ChartboostDelegate
import com.chartboost_helium.sdk.Events.*
import com.chartboost_helium.sdk.Libraries.CBLogging
import com.chartboost_helium.sdk.Model.CBError
import com.chartboost_helium.sdk.Privacy.model.CCPA
import com.chartboost_helium.sdk.Privacy.model.GDPR
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
                    Chartboost.setLoggingLevel(CBLogging.Level.ALL)
                    Chartboost.setMediation(
                        Chartboost.CBMediation.CBMediationHelium,
                        partnerSdkVersion,
                        adapterVersion
                    )

                    Chartboost.setDelegate(object : ChartboostDelegate() {
                        override fun didInitialize() {
                            continuation.resume(
                                Result.success(
                                    LogController.i("$TAG Chartboost SDK successfully initialized.")
                                )
                            )
                        }
                    })

                    Chartboost.startWithAppId(context.applicationContext, app_id, app_signature)

                    // Deprecated, but much needed for Helium. Needs to be set after SDK Start.
                    Chartboost.setAutoCacheAds(false)

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
        return suspendCoroutine { continuation ->
            val chartboostBanner = ChartboostBanner(
                context,
                request.partnerPlacement,
                getChartboostAdSize(request.size),
                null
            )

            chartboostBanner.setListener(object : ChartboostBannerListener {
                override fun onAdCached(
                    cacheEvent: ChartboostCacheEvent?,
                    cacheError: ChartboostCacheError?
                ) {
                    if (cacheError == null) {
                        continuation.resume(
                            Result.success(
                                PartnerAd(
                                    ad = chartboostBanner,
                                    details = mapOf(),
                                    request = request
                                )
                            )
                        )
                    } else {
                        LogController.d("$TAG failed to load Chartboost banner ad. Chartboost Error Code: ${cacheError.code}")
                        continuation.resume(Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL)))
                    }
                }

                override fun onAdShown(
                    showEvent: ChartboostShowEvent?,
                    showError: ChartboostShowError?
                ) {
                    // Helium Banners no longer have a show callback.
                }

                override fun onAdClicked(
                    clickEvent: ChartboostClickEvent?,
                    clickError: ChartboostClickError?
                ) {
                    if (clickError == null) {
                        partnerAdListener.onPartnerAdClicked(
                            PartnerAd(
                                ad = chartboostBanner,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    }
                }

            })

            chartboostBanner.setAutomaticallyRefreshesContent(false)

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
        in 50 until 90 -> BannerSize.STANDARD
        in 90 until 250 -> BannerSize.LEADERBOARD
        in 250 until DisplayMetrics().heightPixels -> BannerSize.MEDIUM
        else -> BannerSize.STANDARD
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
            Chartboost.setDelegate(object : ChartboostDelegate() {
                override fun shouldRequestInterstitial(location: String?): Boolean {
                    return true
                }

                override fun shouldDisplayInterstitial(location: String?): Boolean {
                    return true
                }

                override fun didCacheInterstitial(location: String?) {
                    continuation.resume(
                        Result.success(
                            PartnerAd(
                                ad = null,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    )
                }

                override fun didFailToLoadInterstitial(
                    location: String?,
                    error: CBError.CBImpressionError?
                ) {
                    LogController.d("$TAG failed to load Chartboost interstitial ad. Chartboost Error: $error")
                    continuation.resume(Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL)))
                }

                override fun didDismissInterstitial(location: String?) {
                    partnerAdListener.onPartnerAdDismissed(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        ), null
                    )
                }

                override fun didCloseInterstitial(location: String?) {}

                override fun didClickInterstitial(location: String?) {
                    partnerAdListener.onPartnerAdClicked(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        )
                    )
                }

                override fun didDisplayInterstitial(location: String?) {
                    partnerAdListener.onPartnerAdImpression(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        )
                    )
                }

                override fun didFailToRecordClick(uri: String?, error: CBError.CBClickError?) {}

            })

            // TODO: New PartnerController needs to pass adm.
            if (request.adm.isNullOrEmpty()) {
                Chartboost.cacheInterstitial(request.partnerPlacement)
            } else {
                Chartboost.cacheInterstitial(request.partnerPlacement, request.adm)
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
            Chartboost.setDelegate(object : ChartboostDelegate() {
                override fun shouldDisplayRewardedVideo(location: String?): Boolean {
                    return true
                }

                override fun didCacheRewardedVideo(location: String?) {
                    continuation.resume(
                        Result.success(
                            PartnerAd(
                                ad = null,
                                details = emptyMap(),
                                request = request
                            )
                        )
                    )
                }

                override fun didFailToLoadRewardedVideo(
                    location: String?,
                    error: CBError.CBImpressionError?
                ) {
                    LogController.d("$TAG failed to cache Chartboost rewarded ad. Chartboost Error: $error")
                    continuation.resume(Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL)))
                }

                override fun didDismissRewardedVideo(location: String?) {
                    partnerAdListener.onPartnerAdDismissed(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        ), null
                    )
                }

                override fun didDisplayRewardedVideo(location: String?) {
                    partnerAdListener.onPartnerAdImpression(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        )
                    )
                }

                override fun didClickRewardedVideo(location: String?) {
                    partnerAdListener.onPartnerAdClicked(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        )
                    )
                }

                override fun didFailToRecordClick(uri: String?, error: CBError.CBClickError?) {}

                override fun didCompleteRewardedVideo(location: String?, reward: Int) {
                    partnerAdListener.onPartnerAdRewarded(
                        PartnerAd(
                            ad = null,
                            details = emptyMap(),
                            request = request
                        ),
                        reward = Reward(reward, location ?: "")
                    )
                }
            })

            // TODO: New PartnerController needs to pass adm.
            if (request.adm.isNullOrEmpty()) {
                Chartboost.cacheRewardedVideo(request.partnerPlacement)
            } else {
                Chartboost.cacheRewardedVideo(request.partnerPlacement, request.adm)
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
        return partnerAd.let {
            Chartboost.showInterstitial(it.request.partnerPlacement)
            Result.success(it)
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
        return partnerAd.let {
            Chartboost.showRewardedVideo(it.request.partnerPlacement)
            Result.success(it)
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
            if (it is ChartboostBanner) {
                it.detachBanner()
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
}
