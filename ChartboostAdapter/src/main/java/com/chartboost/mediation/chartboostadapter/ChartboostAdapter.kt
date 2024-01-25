/*
 * Copyright 2023-2024 Chartboost, Inc.
 * 
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import com.chartboost.heliumsdk.HeliumSdk
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.PartnerLogController
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.*
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
import com.chartboost.sdk.events.*
import com.chartboost.sdk.privacy.model.CCPA
import com.chartboost.sdk.privacy.model.COPPA
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
                        StartError.Code.INVALID_CREDENTIALS -> ChartboostMediationError.CM_INITIALIZATION_FAILURE_INVALID_CREDENTIALS
                        StartError.Code.NETWORK_FAILURE -> ChartboostMediationError.CM_AD_SERVER_ERROR
                        else -> ChartboostMediationError.CM_INITIALIZATION_FAILURE_UNKNOWN
                    }
                }
                is CacheError -> {
                    when (error.code) {
                        CacheError.Code.INTERNET_UNAVAILABLE -> ChartboostMediationError.CM_NO_CONNECTIVITY
                        CacheError.Code.NO_AD_FOUND -> ChartboostMediationError.CM_LOAD_FAILURE_NO_FILL
                        CacheError.Code.SESSION_NOT_STARTED -> ChartboostMediationError.CM_INITIALIZATION_FAILURE_UNKNOWN
                        CacheError.Code.NETWORK_FAILURE, CacheError.Code.SERVER_ERROR -> ChartboostMediationError.CM_AD_SERVER_ERROR
                        else -> ChartboostMediationError.CM_PARTNER_ERROR
                    }
                }
                is ShowError -> {
                    when (error.code) {
                        ShowError.Code.INTERNET_UNAVAILABLE -> ChartboostMediationError.CM_NO_CONNECTIVITY
                        ShowError.Code.NO_CACHED_AD -> ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_READY
                        ShowError.Code.SESSION_NOT_STARTED -> ChartboostMediationError.CM_SHOW_FAILURE_NOT_INITIALIZED
                        else -> ChartboostMediationError.CM_PARTNER_ERROR
                    }
                }
                else -> ChartboostMediationError.CM_UNKNOWN_ERROR
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
    }

    /**
     * Get the Chartboost SDK version.
     */
    override val partnerSdkVersion: String
        get() = Chartboost.getSDKVersion()

    /**
     * Get the Chartboost adapter version.
     *
     * You may version the adapter using any preferred convention, but it is recommended to apply the
     * following format if the adapter will be published by Chartboost Mediation:
     *
     * Chartboost Mediation.Partner.Adapter
     *
     * "Chartboost Mediation" represents the Chartboost Mediation SDK’s major version that is compatible with this adapter. This must be 1 digit.
     * "Partner" represents the partner SDK’s major.minor.patch.x (where x is optional) version that is compatible with this adapter. This can be 3-4 digits.
     * "Adapter" represents this adapter’s version (starting with 0), which resets to 0 when the partner SDK’s version changes. This must be 1 digit.
     */
    override val adapterVersion: String
        get() = BuildConfig.CHARTBOOST_MEDIATION_CHARTBOOST_ADAPTER_VERSION

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
        partnerConfiguration: PartnerConfiguration,
    ): Result<Unit> {
        PartnerLogController.log(SETUP_STARTED)

        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<Unit>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            Json.decodeFromJsonElement<String>(
                (partnerConfiguration.credentials as JsonObject).getValue(APPLICATION_ID_KEY),
            ).trim()
                .takeIf { it.isNotEmpty() }?.let { appId ->
                    // The server does not provide the app signature. As Chartboost Monetization and
                    // Chartboost Mediation use the same app id and app signature, we can pass the
                    // app signature to Chartboost Monetization SDK from the Chartboost Mediation SDK.
                    HeliumSdk.getAppSignature()?.let { app_signature ->
                        Chartboost.setLoggingLevel(LoggingLevel.ALL)

                        Chartboost.startWithAppId(
                            context.applicationContext,
                            appId,
                            app_signature,
                        ) { startError ->

                            startError?.let {
                                PartnerLogController.log(SETUP_FAILED, "${it.code}")
                                resumeOnce(
                                    Result.failure(
                                        ChartboostMediationAdException(
                                            getChartboostMediationError(it),
                                        ),
                                    ),
                                )
                            } ?: run {
                                PartnerLogController.log(SETUP_SUCCEEDED)
                                resumeOnce(
                                    Result.success(PartnerLogController.log(SETUP_SUCCEEDED)),
                                )
                            }
                        }
                    } ?: run {
                        PartnerLogController.log(SETUP_FAILED, "Missing application signature.")
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.CM_INITIALIZATION_FAILURE_INVALID_CREDENTIALS),
                            ),
                        )
                    }
                } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
                resumeOnce(
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INITIALIZATION_FAILURE_INVALID_CREDENTIALS)),
                )
            }
        }
    }

    /**
     * Notify the Chartboost SDK of the GDPR applicability and consent status.
     *
     * @param context The current [Context].
     * @param applies True if GDPR applies, false otherwise.
     * @param gdprConsentStatus The user's GDPR consent status.
     */
    override fun setGdpr(
        context: Context,
        applies: Boolean?,
        gdprConsentStatus: GdprConsentStatus,
    ) {
        PartnerLogController.log(
            when (applies) {
                true -> GDPR_APPLICABLE
                false -> GDPR_NOT_APPLICABLE
                else -> GDPR_UNKNOWN
            },
        )

        PartnerLogController.log(
            when (gdprConsentStatus) {
                GdprConsentStatus.GDPR_CONSENT_UNKNOWN -> GDPR_CONSENT_UNKNOWN
                GdprConsentStatus.GDPR_CONSENT_GRANTED -> GDPR_CONSENT_GRANTED
                GdprConsentStatus.GDPR_CONSENT_DENIED -> GDPR_CONSENT_DENIED
            },
        )

        // Chartboost does not have a public method as to whether GDPR applies.
        // If anything was set previously for GDPR, it will be reset when GDPR no longer applies.
        if (applies != true) {
            Chartboost.clearDataUseConsent(context, GDPR.GDPR_STANDARD)
        }

        when (gdprConsentStatus) {
            GdprConsentStatus.GDPR_CONSENT_GRANTED -> {
                Chartboost.addDataUseConsent(
                    context,
                    GDPR(GDPR.GDPR_CONSENT.BEHAVIORAL),
                )
            }
            GdprConsentStatus.GDPR_CONSENT_DENIED -> {
                Chartboost.addDataUseConsent(
                    context,
                    GDPR(GDPR.GDPR_CONSENT.NON_BEHAVIORAL),
                )
            }
            else -> {
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
        privacyString: String,
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
    override fun setUserSubjectToCoppa(
        context: Context,
        isSubjectToCoppa: Boolean,
    ) {
        PartnerLogController.log(
            if (isSubjectToCoppa) {
                COPPA_SUBJECT
            } else {
                COPPA_NOT_SUBJECT
            },
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
        request: PreBidRequest,
    ): Map<String, String> {
        PartnerLogController.log(BIDDER_INFO_FETCH_STARTED)

        return withContext(IO) {
            val token = Chartboost.getBidderToken() ?: ""
            PartnerLogController.log(if (token.isEmpty()) BIDDER_INFO_FETCH_FAILED else BIDDER_INFO_FETCH_SUCCEEDED)

            mapOf("buyeruid" to token)
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

        return when (request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> loadBannerAd(context, request, partnerAdListener)
            AdFormat.INTERSTITIAL.key -> loadInterstitialAd(request, partnerAdListener)
            AdFormat.REWARDED.key -> loadRewardedAd(request, partnerAdListener)
            else -> {
                PartnerLogController.log(LOAD_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNSUPPORTED_AD_FORMAT))
            }
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
    override suspend fun show(
        context: Context,
        partnerAd: PartnerAd,
    ): Result<PartnerAd> {
        PartnerLogController.log(SHOW_STARTED)

        return when (partnerAd.request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }
            AdFormat.INTERSTITIAL.key -> showInterstitialAd(partnerAd)
            AdFormat.REWARDED.key -> showRewardedAd(partnerAd)
            else -> {
                PartnerLogController.log(SHOW_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_UNSUPPORTED_AD_FORMAT))
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

        return when (partnerAd.request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL.key, AdFormat.REWARDED.key -> {
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
                    getChartboostAdSize(request.size),
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
                        WeakReference(partnerAdListener),
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
                        WeakReference(partnerAdListener),
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
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_WRONG_RESOURCE_TYPE))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_FOUND))
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
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_WRONG_RESOURCE_TYPE))
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_FOUND))
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
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INVALIDATE_FAILURE_WRONG_RESOURCE_TYPE))
            }
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INVALIDATE_FAILURE_AD_NOT_FOUND))
        }
    }

    /**
     * As Chartboost needs to pass a [Mediation] object to some of the methods.
     * Let's have a method to avoid repetition.
     */
    private fun setMediation(): Mediation {
        return Mediation("Chartboost", HeliumSdk.getVersion(), adapterVersion)
    }
}

/**
 * Callback implementation for Chartboost interstitial ad events.
 *
 * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
 * @param listenerRef A [WeakReference] to the [PartnerAdListener] to notify Chartboost Mediation of ad events.
 * @param continuationRef A [WeakReference] to the [CancellableContinuation] to resume once the ad has loaded.
 */
private class InterstitialAdCallback(
    private val request: PartnerAdLoadRequest,
    private val listenerRef: WeakReference<PartnerAdListener>,
    private val continuationRef: WeakReference<CancellableContinuation<Result<PartnerAd>>>,
) : InterstitialCallback {
    override fun onAdClicked(
        event: ClickEvent,
        error: ClickError?,
    ) {
        PartnerLogController.log(DID_CLICK)

        listenerRef.get()?.onPartnerAdClicked(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdClicked. Listener is null.")
        }
    }

    override fun onAdDismiss(event: DismissEvent) {
        PartnerLogController.log(DID_DISMISS)

        listenerRef.get()?.onPartnerAdDismissed(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
            null,
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdDismissed. Listener is null.")
        }
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
        } ?: onShowSuccess()
    }

    override fun onImpressionRecorded(event: ImpressionEvent) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)

        listenerRef.get()?.onPartnerAdImpression(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdImpression. Listener is null.")
        }
    }
}

/**
 * Callback implementation for Chartboost rewarded ad events.
 *
 * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
 * @param listenerRef A [WeakReference] to the [PartnerAdListener] to notify Chartboost Mediation of ad events.
 * @param continuationRef A [WeakReference] to the [CancellableContinuation] to resume once the ad has loaded.
 */
private class RewardedAdCallback(
    private val request: PartnerAdLoadRequest,
    private val listenerRef: WeakReference<PartnerAdListener>,
    private val continuationRef: WeakReference<CancellableContinuation<Result<PartnerAd>>>,
) : RewardedCallback {
    override fun onAdClicked(
        event: ClickEvent,
        error: ClickError?,
    ) {
        PartnerLogController.log(DID_CLICK)

        listenerRef.get()?.onPartnerAdClicked(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdClicked. Listener is null.")
        }
    }

    override fun onAdDismiss(event: DismissEvent) {
        PartnerLogController.log(DID_DISMISS)

        listenerRef.get()?.onPartnerAdDismissed(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
            null,
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdDismissed. Listener is null.")
        }
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
        } ?: onShowSuccess()
    }

    override fun onImpressionRecorded(event: ImpressionEvent) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)

        listenerRef.get()?.onPartnerAdImpression(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdImpression. Listener is null.")
        }
    }

    override fun onRewardEarned(event: RewardEvent) {
        PartnerLogController.log(DID_REWARD)

        listenerRef.get()?.onPartnerAdRewarded(
            PartnerAd(
                ad = event.ad,
                details = emptyMap(),
                request = request,
            ),
        ) ?: run {
            PartnerLogController.log(CUSTOM, "Unable to fire onPartnerAdRewarded. Listener is null.")
        }
    }
}
