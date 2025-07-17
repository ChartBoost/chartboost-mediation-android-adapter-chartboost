/*
 * Copyright 2023-2025 Chartboost, Inc.
 * 
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import com.chartboost.chartboostmediationsdk.ChartboostMediationSdk
import com.chartboost.chartboostmediationsdk.ad.ChartboostMediationBannerAdView.ChartboostMediationBannerSize.Companion.asSize
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationAdException
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationError
import com.chartboost.chartboostmediationsdk.domain.PartnerAd
import com.chartboost.chartboostmediationsdk.domain.PartnerAdFormats
import com.chartboost.chartboostmediationsdk.domain.PartnerAdListener
import com.chartboost.chartboostmediationsdk.domain.PartnerAdLoadRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerAdPreBidRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerAdapter
import com.chartboost.chartboostmediationsdk.domain.PartnerAdapterConfiguration
import com.chartboost.chartboostmediationsdk.domain.PartnerConfiguration
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.CUSTOM
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_CLICK
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_DISMISS
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_EXPIRE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_REWARD
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_TRACK_IMPRESSION
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_DENIED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_GRANTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_UNKNOWN
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_NOT_APPLICABLE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USER_IS_NOT_UNDERAGE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USER_IS_UNDERAGE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USP_CONSENT_DENIED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USP_CONSENT_GRANTED
import com.chartboost.core.consent.ConsentKey
import com.chartboost.core.consent.ConsentKeys
import com.chartboost.core.consent.ConsentValue
import com.chartboost.core.consent.ConsentValues
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.getChartboostMediationError
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.onShowError
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.onShowSuccess
import com.chartboost.sdk.Chartboost
import com.chartboost.sdk.LoggingLevel
import com.chartboost.sdk.Mediation
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.ads.Interstitial
import com.chartboost.sdk.ads.Rewarded
import com.chartboost.sdk.callbacks.BannerCallback
import com.chartboost.sdk.callbacks.InterstitialCallback
import com.chartboost.sdk.callbacks.RewardedCallback
import com.chartboost.sdk.events.CBError
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickError
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.DismissEvent
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ImpressionEvent
import com.chartboost.sdk.events.RewardEvent
import com.chartboost.sdk.events.ShowError
import com.chartboost.sdk.events.ShowEvent
import com.chartboost.sdk.events.StartError
import com.chartboost.sdk.privacy.model.CCPA
import com.chartboost.sdk.privacy.model.COPPA
import com.chartboost.sdk.privacy.model.Custom
import com.chartboost.sdk.privacy.model.GDPR
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * The Chartboost Mediation Chartboost SDK adapter.
 */
class ChartboostAdapter : PartnerAdapter {
    companion object {
        /**
         * Convert a given Chartboost error to a [ChartboostMediationError].
         *
         * @param error The Chartboost error to convert.
         *
         * @return The corresponding [ChartboostMediationError].
         */
        internal fun getChartboostMediationError(error: CBError) =
            when (error) {
                is StartError -> {
                    when (error.code) {
                        StartError.Code.INVALID_CREDENTIALS -> ChartboostMediationError.InitializationError.InvalidCredentials
                        StartError.Code.NETWORK_FAILURE -> ChartboostMediationError.OtherError.AdServerError
                        else -> ChartboostMediationError.InitializationError.Unknown
                    }
                }
                is CacheError -> {
                    when (error.code) {
                        CacheError.Code.INTERNET_UNAVAILABLE -> ChartboostMediationError.OtherError.NoConnectivity
                        CacheError.Code.NO_AD_FOUND -> ChartboostMediationError.LoadError.NoFill
                        CacheError.Code.SESSION_NOT_STARTED -> ChartboostMediationError.InitializationError.Unknown
                        CacheError.Code.NETWORK_FAILURE, CacheError.Code.SERVER_ERROR -> ChartboostMediationError.OtherError.AdServerError
                        else -> ChartboostMediationError.OtherError.PartnerError
                    }
                }
                is ShowError -> {
                    when (error.code) {
                        ShowError.Code.INTERNET_UNAVAILABLE -> ChartboostMediationError.OtherError.NoConnectivity
                        ShowError.Code.NO_CACHED_AD -> ChartboostMediationError.ShowError.AdNotReady
                        ShowError.Code.SESSION_NOT_STARTED -> ChartboostMediationError.ShowError.NotInitialized
                        else -> ChartboostMediationError.OtherError.PartnerError
                    }
                }
                else -> ChartboostMediationError.OtherError.Unknown
            }

        /**
         * A lambda to call for successful Chartboost ad shows.
         */
        internal var onShowSuccess: () -> Unit = {}

        /**
         * A lambda to call for failed Chartboost ad shows.
         */
        internal var onShowError: (event: ShowEvent, error: ShowError) -> Unit =
            { _: ShowEvent, _: ShowError -> }

        /**
         * Key for parsing the Chartboost SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "app_id"

        /**
         * Key for parsing the Chartboost SDK application signature.
         */
        private const val APPLICATION_SIGNATURE_KEY = "app_signature"
    }

    /**
     * The Chartboost adapter configuration.
     */
    override var configuration: PartnerAdapterConfiguration = ChartboostAdapterConfiguration

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
        partnerConfiguration: PartnerConfiguration,

    ): Result<Map<String, Any>> = withContext(IO) {
        PartnerLogController.log(SETUP_STARTED)

        val credentials = partnerConfiguration.credentials
        val (appId, appSignature) = extractPartnerConfigs(credentials)

        suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<Map<String, Any>>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            if (appId.isNullOrEmpty() || appSignature.isNullOrEmpty()) {
                PartnerLogController.log(SETUP_FAILED, "Missing app ID or app signature.")
                resumeOnce(
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.InitializationError.InvalidCredentials)),
                )
                return@suspendCancellableCoroutine
            }

            Chartboost.setLoggingLevel(LoggingLevel.ALL)
            Chartboost.startWithAppId(context.applicationContext, appId, appSignature) { error ->
                error?.let {
                    PartnerLogController.log(SETUP_FAILED, "${it.code}")
                    resumeOnce(Result.failure(ChartboostMediationAdException(getChartboostMediationError(it))))
                } ?: run {
                    PartnerLogController.log(SETUP_SUCCEEDED)
                    resumeOnce(Result.success(emptyMap()))
                }
            }
        }
    }

    /**
     * Notify Chartboost of the COPPA subjectivity.
     *
     * @param context The current [Context].
     * @param isUserUnderage True if the user is subject to COPPA, false otherwise.
     */
    override fun setIsUserUnderage(
        context: Context,
        isUserUnderage: Boolean,
    ) {
        PartnerLogController.log(
            if (isUserUnderage) {
                USER_IS_UNDERAGE
            } else {
                USER_IS_NOT_UNDERAGE
            },
        )

        Chartboost.addDataUseConsent(context, COPPA(isUserUnderage))
    }

    /**
     * Get a bid token if network bidding is supported.
     *
     * @param context The current [Context].
     * @param request The [PartnerAdPreBidRequest] instance containing relevant data for the current bid request.
     *
     * @return A Map of biddable token Strings.
     */
    override suspend fun fetchBidderInformation(
        context: Context,
        request: PartnerAdPreBidRequest,
    ): Result<Map<String, String>> {
        PartnerLogController.log(BIDDER_INFO_FETCH_STARTED)

        return withContext(IO) {
            val token = Chartboost.getBidderToken() ?: ""
            PartnerLogController.log(if (token.isEmpty()) BIDDER_INFO_FETCH_FAILED else BIDDER_INFO_FETCH_SUCCEEDED)

            Result.success(mapOf("buyeruid" to token))
        }
    }

    /**
     * Attempt to load a Chartboost ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    override suspend fun load(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        PartnerLogController.log(LOAD_STARTED)

        return when (request.format) {
            PartnerAdFormats.BANNER -> loadBannerAd(context, request, partnerAdListener)
            PartnerAdFormats.INTERSTITIAL -> loadInterstitialAd(request, partnerAdListener)
            PartnerAdFormats.REWARDED -> loadRewardedAd(request, partnerAdListener)
            else -> {
                PartnerLogController.log(LOAD_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.LoadError.UnsupportedAdFormat))
            }
        }
    }

    /**
     * Attempt to show the currently loaded Chartboost ad.
     *
     * @param activity The current [Activity]
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    override suspend fun show(
        activity: Activity,
        partnerAd: PartnerAd,
    ): Result<PartnerAd> {
        PartnerLogController.log(SHOW_STARTED)

        return when (partnerAd.request.format) {
            PartnerAdFormats.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }
            PartnerAdFormats.INTERSTITIAL -> showInterstitialAd(partnerAd)
            PartnerAdFormats.REWARDED -> showRewardedAd(partnerAd)
            else -> {
                PartnerLogController.log(SHOW_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.UnsupportedAdFormat))
            }
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
            PartnerAdFormats.BANNER -> destroyBannerAd(partnerAd)
            PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED -> {
                // Chartboost does not have destroy methods for their fullscreen ads.
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
            else -> {
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
        }
    }

    override fun setConsents(
        context: Context,
        consents: Map<ConsentKey, ConsentValue>,
        modifiedKeys: Set<ConsentKey>,
    ) {
        val consent = consents[configuration.partnerId]?.takeIf { it.isNotBlank() }
            ?: consents[ConsentKeys.GDPR_CONSENT_GIVEN]?.takeIf { it.isNotBlank() }
        consent?.let {
            if (it == ConsentValues.DOES_NOT_APPLY) {
                PartnerLogController.log(GDPR_NOT_APPLICABLE)
                // Chartboost does not have a public method as to whether GDPR applies.
                // If anything was set previously for GDPR, it will be reset when GDPR no longer applies.
                Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
                return@let
            }

            PartnerLogController.log(
                when (it) {
                    ConsentValues.GRANTED -> GDPR_CONSENT_GRANTED
                    ConsentValues.DENIED -> GDPR_CONSENT_DENIED
                    else -> GDPR_CONSENT_UNKNOWN
                },
            )

            when (it) {
                ConsentValues.GRANTED -> {
                    Chartboost.addDataUseConsent(
                        context,
                        GDPR(GDPR.GDPR_CONSENT.BEHAVIORAL),
                    )
                }
                ConsentValues.DENIED -> {
                    Chartboost.addDataUseConsent(
                        context,
                        GDPR(GDPR.GDPR_CONSENT.NON_BEHAVIORAL),
                    )
                }
                else -> {
                    Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
                }
            }
        } ?: Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)

        consents[ConsentKeys.USP]?.let {
            Chartboost.addDataUseConsent(context, Custom(CCPA.CCPA_STANDARD, it))
        }

        consents[ConsentKeys.CCPA_OPT_IN]?.let {
            when (it) {
                ConsentValues.GRANTED -> {
                    PartnerLogController.log(USP_CONSENT_GRANTED)
                    Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_IN_SALE))
                }
                ConsentValues.DENIED -> {
                    PartnerLogController.log(USP_CONSENT_DENIED)
                    Chartboost.addDataUseConsent(context, CCPA(CCPA.CCPA_CONSENT.OPT_OUT_SALE))
                }
                else -> PartnerLogController.log(CUSTOM, "Unable to process $it CCPA_OPT_IN")

            }
        }
    }

    /**
     * Attempt to load a Chartboost banner ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            val chartboostBanner =
                Banner(
                    context,
                    request.partnerPlacement,
                    getChartboostAdSize(request.bannerSize?.asSize()),
                    object : BannerCallback {
                        override fun onAdClicked(
                            event: ClickEvent,
                            error: ClickError?,
                        ) {
                            PartnerLogController.log(DID_CLICK)
                            partnerAdListener.onPartnerAdClicked(
                                PartnerAd(
                                    ad = event.ad,
                                    details = emptyMap(),
                                    request = request,
                                ),
                            )
                        }

                        override fun onAdLoaded(
                            event: CacheEvent,
                            error: CacheError?,
                        ) {
                            error?.let {
                                PartnerLogController.log(LOAD_FAILED, "${it.code}")
                                resumeOnce(
                                    Result.failure(
                                        ChartboostMediationAdException(
                                            getChartboostMediationError(
                                                error,
                                            ),
                                        ),
                                    ),
                                )
                            } ?: run {
                                // Render the Chartboost banner on Main thread immediately after ad loaded.
                                CoroutineScope(Main).launch {
                                    event.ad.show()
                                }

                                PartnerLogController.log(LOAD_SUCCEEDED)
                                resumeOnce(
                                    Result.success(
                                        PartnerAd(
                                            ad = event.ad,
                                            details = emptyMap(),
                                            request = request,
                                        ),
                                    ),
                                )
                            }
                        }

                        override fun onAdRequestedToShow(event: ShowEvent) {}

                        override fun onAdShown(
                            event: ShowEvent,
                            error: ShowError?,
                        ) {}

                        override fun onImpressionRecorded(event: ImpressionEvent) {
                            PartnerLogController.log(DID_TRACK_IMPRESSION)
                            partnerAdListener.onPartnerAdImpression(
                                PartnerAd(
                                    ad = event.ad,
                                    details = emptyMap(),
                                    request = request,
                                ),
                            )
                        }

                        override fun onAdExpired(event: ExpirationEvent) {
                            PartnerLogController.log(DID_EXPIRE)

                            partnerAdListener.onPartnerAdExpired(
                                PartnerAd(
                                    ad = event.ad,
                                    details = emptyMap(),
                                    request = request,
                                ),
                            )
                        }
                    },
                    setMediation(),
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
    private fun getChartboostAdSize(size: Size?) =
        when (size?.height) {
            in 50 until 90 -> Banner.BannerSize.STANDARD
            in 90 until 250 -> Banner.BannerSize.LEADERBOARD
            in 250 until DisplayMetrics().heightPixels -> Banner.BannerSize.MEDIUM
            else -> Banner.BannerSize.STANDARD
        }

    /**
     * Attempt to load a Chartboost interstitial ad.
     *
     * @param request An [PartnerAdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            val chartboostInterstitial =
                Interstitial(
                    request.partnerPlacement,
                    InterstitialAdCallback(
                        request,
                        partnerAdListener,
                        WeakReference(continuation),
                    ),
                    setMediation(),
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
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            val chartboostRewarded =
                Rewarded(
                    request.partnerPlacement,
                    RewardedAdCallback(
                        request,
                        partnerAdListener,
                        WeakReference(continuation),
                    ),
                    setMediation(),
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
    private suspend fun showInterstitialAd(partnerAd: PartnerAd): Result<PartnerAd> {
        return (partnerAd.ad)?.let { ad ->
            (ad as? Interstitial)?.let {
                suspendCancellableCoroutine { continuation ->
                    val continuationWeakRef = WeakReference(continuation)

                    fun resumeOnce(result: Result<PartnerAd>) {
                        continuationWeakRef.get()?.let {
                            if (it.isActive) {
                                it.resume(result)
                            }
                        } ?: run {
                            PartnerLogController.log(SHOW_FAILED, "Unable to resume continuation for show. Continuation is null.")
                        }
                    }
                    onShowSuccess = {
                        PartnerLogController.log(SHOW_SUCCEEDED)
                        resumeOnce(Result.success(partnerAd))
                    }

                    onShowError = { event, error ->
                        PartnerLogController.log(
                            SHOW_FAILED,
                            "Location: ${event.ad.location}. Error: ${error.code}",
                        )

                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(getChartboostMediationError(error)),
                            ),
                        )
                    }
                    it.show()
                }
            } ?: run {
                PartnerLogController.log(SHOW_FAILED, "Ad is not Interstitial.")
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.WrongResourceType))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.AdNotFound))
        }
    }

    /**
     * Attempt to show a Chartboost rewarded ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showRewardedAd(partnerAd: PartnerAd): Result<PartnerAd> {
        return (partnerAd.ad)?.let { ad ->
            (ad as? Rewarded)?.let {
                suspendCancellableCoroutine { continuation ->
                    val continuationWeakRef = WeakReference(continuation)

                    fun resumeOnce(result: Result<PartnerAd>) {
                        continuationWeakRef.get()?.let {
                            if (it.isActive) {
                                it.resume(result)
                            }
                        } ?: run {
                            PartnerLogController.log(SHOW_FAILED, "Unable to resume continuation for show. Continuation is null.")
                        }
                    }

                    onShowSuccess = {
                        PartnerLogController.log(SHOW_SUCCEEDED)
                        resumeOnce(Result.success(partnerAd))
                    }

                    onShowError = { event, error ->
                        PartnerLogController.log(
                            SHOW_FAILED,
                            "Location: ${event.ad.location}. Error: ${error.code}",
                        )
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(getChartboostMediationError(error)),
                            ),
                        )
                    }
                    it.show()
                }
            } ?: run {
                PartnerLogController.log(SHOW_FAILED, "Ad is not Rewarded.")
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.WrongResourceType))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.AdNotFound))
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
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.InvalidateError.WrongResourceType))
            }
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.InvalidateError.AdNotFound))
        }
    }

    /**
     * As Chartboost needs to pass a [Mediation] object to some of the methods.
     * Let's have a method to avoid repetition.
     */
    private fun setMediation(): Mediation {
        return Mediation("Chartboost", ChartboostMediationSdk.getVersion(), configuration.adapterVersion)
    }

    /**
     * Extract the Chartboost partner configurations from the given credentials.
     *
     * @param credentials The credentials to extract the Chartboost partner configurations from.
     *
     * @return A Pair containing the extracted Chartboost app ID and app signature.
     */
    private fun extractPartnerConfigs(credentials: JsonObject): Pair<String?, String?> {
        val appId = extractPartnerConfig(APPLICATION_ID_KEY, credentials)
        val appSignature = extractPartnerConfig(APPLICATION_SIGNATURE_KEY, credentials)

        return Pair(appId, appSignature)
    }

    /**
     * Extract a partner configuration value from the given credentials.
     *
     * @param key The key to extract from the credentials.
     * @param credentials The credentials to extract the value from.
     *
     * @return The extracted value, or null if the key or value is not found.
     */
    private fun extractPartnerConfig(
        key: String,
        credentials: JsonObject,
    ): String? {
        val element =
            credentials[key] ?: return null.also {
                PartnerLogController.log(SETUP_FAILED, "No value found for $key.")
            }

        return Json.decodeFromJsonElement<String>(element)
            .trim()
            .takeIf { it.isNotEmpty() }
            .also { if (it == null) PartnerLogController.log(SETUP_FAILED, "No value found for $key.") }
    }
}

/**
 * Callback implementation for Chartboost interstitial ad events.
 *
 * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
 * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
 * @param continuationRef A [WeakReference] to the [CancellableContinuation] to resume once the ad has loaded.
 */
private class InterstitialAdCallback(
    private val request: PartnerAdLoadRequest,
    private val listener: PartnerAdListener,
    private val continuationRef: WeakReference<CancellableContinuation<Result<PartnerAd>>>,
) : InterstitialCallback {
    override fun onAdClicked(
        event: ClickEvent,
        error: ClickError?,
    ) {
        PartnerLogController.log(DID_CLICK)

        listener.onPartnerAdClicked(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }

    override fun onAdDismiss(event: DismissEvent) {
        PartnerLogController.log(DID_DISMISS)

        listener.onPartnerAdDismissed(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
            null,
        )
    }

    override fun onAdLoaded(
        event: CacheEvent,
        error: CacheError?,
    ) {
        error?.let {
            PartnerLogController.log(LOAD_FAILED, "$error")

            continuationRef.get()?.let {
                if (it.isActive) {
                    it.resume(
                        Result.failure(
                            ChartboostMediationAdException(
                                getChartboostMediationError(
                                    error,
                                ),
                            ),
                        ),
                    )
                }
            } ?: run {
                PartnerLogController.log(CUSTOM, "Unable to resume continuation for onAdLoaded. Continuation is null.")
            }
        } ?: run {
            PartnerLogController.log(LOAD_SUCCEEDED)

            continuationRef.get()?.let {
                if (it.isActive) {
                    it.resume(
                        Result.success(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request,
                            ),
                        ),
                    )
                }
            } ?: run {
                PartnerLogController.log(CUSTOM, "Unable to resume continuation for onAdLoaded. Continuation is null.")
            }
        }
    }

    override fun onAdRequestedToShow(event: ShowEvent) {}

    override fun onAdShown(
        event: ShowEvent,
        error: ShowError?,
    ) {
        error?.let {
            onShowError(event, it)
            onShowError = { _: ShowEvent, _: ShowError -> }
        } ?: run {
            onShowSuccess()
            onShowSuccess = {}
        }
    }

    override fun onImpressionRecorded(event: ImpressionEvent) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)

        listener.onPartnerAdImpression(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }

    override fun onAdExpired(event: ExpirationEvent) {
        PartnerLogController.log(DID_EXPIRE)

        listener.onPartnerAdExpired(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }
}

/**
 * Callback implementation for Chartboost rewarded ad events.
 *
 * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
 * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
 * @param continuationRef A [WeakReference] to the [CancellableContinuation] to resume once the ad has loaded.
 */
private class RewardedAdCallback(
    private val request: PartnerAdLoadRequest,
    private val listener: PartnerAdListener,
    private val continuationRef: WeakReference<CancellableContinuation<Result<PartnerAd>>>,
) : RewardedCallback {
    override fun onAdClicked(
        event: ClickEvent,
        error: ClickError?,
    ) {
        PartnerLogController.log(DID_CLICK)

        listener.onPartnerAdClicked(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }

    override fun onAdDismiss(event: DismissEvent) {
        PartnerLogController.log(DID_DISMISS)

        listener.onPartnerAdDismissed(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
            null,
        )
    }

    override fun onAdLoaded(
        event: CacheEvent,
        error: CacheError?,
    ) {
        error?.let {
            PartnerLogController.log(LOAD_FAILED, "$error")

            continuationRef.get()?.let {
                if (it.isActive) {
                    it.resume(
                        Result.failure(
                            ChartboostMediationAdException(
                                getChartboostMediationError(
                                    error,
                                ),
                            ),
                        ),
                    )
                }
            } ?: run {
                PartnerLogController.log(CUSTOM, "Unable to resume continuation for onAdLoaded. Continuation is null.")
            }
        } ?: run {
            PartnerLogController.log(LOAD_SUCCEEDED)

            continuationRef.get()?.let {
                if (it.isActive) {
                    it.resume(
                        Result.success(
                            PartnerAd(
                                ad = event.ad,
                                details = emptyMap(),
                                request = request,
                            ),
                        ),
                    )
                }
            } ?: run {
                PartnerLogController.log(CUSTOM, "Unable to resume continuation for onAdLoaded. Continuation is null.")
            }
        }
    }

    override fun onAdRequestedToShow(event: ShowEvent) {}

    override fun onAdShown(
        event: ShowEvent,
        error: ShowError?,
    ) {
        error?.let {
            onShowError(event, it)
            onShowError = { _: ShowEvent, _: ShowError -> }
        } ?: run {
            onShowSuccess()
            onShowSuccess = {}
        }
    }

    override fun onImpressionRecorded(event: ImpressionEvent) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)

        listener.onPartnerAdImpression(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }

    override fun onRewardEarned(event: RewardEvent) {
        PartnerLogController.log(DID_REWARD)

        listener.onPartnerAdRewarded(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }

    override fun onAdExpired(event: ExpirationEvent) {
        PartnerLogController.log(DID_EXPIRE)

        listener.onPartnerAdExpired(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        )
    }
}
