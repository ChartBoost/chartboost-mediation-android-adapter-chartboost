/*
 * Copyright 2023-2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.chartboost.chartboostmediationsdk.ChartboostMediationSdk
import com.chartboost.chartboostmediationsdk.ad.ChartboostMediationBannerAdView.ChartboostMediationBannerSize
import com.chartboost.chartboostmediationsdk.domain.BannerType
import com.chartboost.chartboostmediationsdk.domain.BannerTypes
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationAdException
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationError
import com.chartboost.chartboostmediationsdk.domain.PartnerAd
import com.chartboost.chartboostmediationsdk.domain.PartnerAdFormats
import com.chartboost.chartboostmediationsdk.domain.PartnerAdListener
import com.chartboost.chartboostmediationsdk.domain.PartnerAdLoadRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerAdPreBidRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerAdapter
import com.chartboost.chartboostmediationsdk.domain.PartnerAdapterConfiguration
import com.chartboost.chartboostmediationsdk.domain.PartnerBannerSize
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
import com.chartboost.core.consent.ConsentKey
import com.chartboost.core.consent.ConsentKeys
import com.chartboost.core.consent.ConsentValue
import com.chartboost.core.consent.ConsentValues
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toShowError
import com.chartboost.sdk.Chartboost
import com.chartboost.sdk.Mediation
import com.chartboost.sdk.ads.BannerAdBidTokenRequest
import com.chartboost.sdk.ads.BannerAdLoadRequest
import com.chartboost.sdk.ads.BannerAdView
import com.chartboost.sdk.ads.BannerSize
import com.chartboost.sdk.ads.FullscreenAd
import com.chartboost.sdk.ads.InterstitialAdBidTokenRequest
import com.chartboost.sdk.ads.InterstitialAdLoadRequest
import com.chartboost.sdk.ads.RewardedAdBidTokenRequest
import com.chartboost.sdk.ads.RewardedAdLoadRequest
import com.chartboost.sdk.callbacks.BannerAdViewCallback
import com.chartboost.sdk.callbacks.FullscreenAdCallback
import com.chartboost.sdk.callbacks.StartCallback
import com.chartboost.sdk.events.ChartboostError
import com.chartboost.sdk.events.StartError
import com.chartboost.sdk.privacy.ChartboostSharedPreferencesKeys
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.coroutines.resume

/**
 * The Chartboost Mediation Chartboost SDK adapter.
 */
class ChartboostAdapter : PartnerAdapter {
    companion object {
        /**
         * Convert a Chartboost SDK start error code to a [ChartboostMediationError]. The fallback
         * also covers codes a newer Chartboost SDK adds, which would otherwise crash the callback.
         *
         * @return The corresponding [ChartboostMediationError].
         */
        internal fun StartError.Code.toInitializationError(): ChartboostMediationError =
            when (this) {
                StartError.Code.INVALID_CREDENTIALS -> ChartboostMediationError.InitializationError.InvalidCredentials
                StartError.Code.INVALID_CONFIGURATION -> ChartboostMediationError.InitializationError.InvalidAppConfig
                StartError.Code.NETWORK_FAILURE -> ChartboostMediationError.InitializationError.NetworkingError
                StartError.Code.SERVER_ERROR -> ChartboostMediationError.InitializationError.ServerError
                StartError.Code.OS_VERSION_NOT_SUPPORTED -> ChartboostMediationError.InitializationError.OSVersionNotSupported
                StartError.Code.INTERNAL, StartError.Code.DISABLED -> ChartboostMediationError.InitializationError.InternalError
                else -> ChartboostMediationError.InitializationError.Unknown
            }

        /**
         * Convert a Chartboost SDK error reported by a load to a [ChartboostMediationError]. A load
         * reports load and connectivity errors only, a banner renders on its own once attached.
         *
         * @return The corresponding [ChartboostMediationError].
         */
        internal fun ChartboostError.toLoadError(): ChartboostMediationError =
            when (this) {
                is ChartboostError.Load.NoAd -> ChartboostMediationError.LoadError.NoFill
                is ChartboostError.Load.NotInitialized -> ChartboostMediationError.LoadError.PartnerNotInitialized
                is ChartboostError.Load.InvalidAdm -> ChartboostMediationError.LoadError.InvalidAdMarkup
                is ChartboostError.Load.InvalidRequest -> ChartboostMediationError.LoadError.InvalidAdRequest
                is ChartboostError.Load.InvalidResponse -> ChartboostMediationError.LoadError.InvalidBidResponse
                is ChartboostError.Load.InvalidPlacement -> ChartboostMediationError.LoadError.InvalidPartnerPlacement
                is ChartboostError.Load.NoContext -> ChartboostMediationError.LoadError.ActivityNotFound
                is ChartboostError.Load.Disabled -> ChartboostMediationError.LoadError.Aborted
                is ChartboostError.Load.LoadInProgress -> ChartboostMediationError.LoadError.LoadInProgress
                is ChartboostError.Load.RateLimited -> ChartboostMediationError.LoadError.RateLimited
                is ChartboostError.Load.TimedOut -> ChartboostMediationError.LoadError.AdRequestTimeout
                is ChartboostError.Load.NoStorage -> ChartboostMediationError.LoadError.OutOfStorage
                is ChartboostError.Load.InvalidBannerSize -> ChartboostMediationError.LoadError.InvalidBannerSize
                is ChartboostError.Connectivity.NoInternet -> ChartboostMediationError.LoadError.NoConnectivity
                is ChartboostError.Connectivity.ServerError -> ChartboostMediationError.LoadError.ServerError
                is ChartboostError.Connectivity -> ChartboostMediationError.LoadError.NetworkingError
                else -> ChartboostMediationError.LoadError.Unknown
            }

        /**
         * Convert a Chartboost SDK error reported by a show to a [ChartboostMediationError].
         *
         * @return The corresponding [ChartboostMediationError].
         */
        internal fun ChartboostError.toShowError(): ChartboostMediationError =
            when (this) {
                is ChartboostError.Show.NoAd -> ChartboostMediationError.ShowError.AdNotReady
                is ChartboostError.Show.AdExpired -> ChartboostMediationError.ShowError.AdExpired
                is ChartboostError.Show.AdInvalidated, is ChartboostError.Show.Disabled -> ChartboostMediationError.ShowError.AdNotFound
                is ChartboostError.Show.NoContext -> ChartboostMediationError.ShowError.ActivityNotFound
                is ChartboostError.Show.NotInitialized -> ChartboostMediationError.ShowError.NotInitialized
                is ChartboostError.Show.FullscreenAlreadyShowing -> ChartboostMediationError.ShowError.ShowInProgress
                is ChartboostError.Show.TimedOut -> ChartboostMediationError.ShowError.Timeout
                is ChartboostError.Show.AssetUnavailable -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Render.VideoPlaybackError -> ChartboostMediationError.ShowError.VideoPlayerError
                is ChartboostError.Render.AssetUnavailable -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Render.Internal -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Render.WebViewMraidUnload -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Render.WebViewTerminated -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Render.Unknown -> ChartboostMediationError.ShowError.MediaBroken
                is ChartboostError.Connectivity.NoInternet -> ChartboostMediationError.ShowError.NoConnectivity
                else -> ChartboostMediationError.ShowError.Unknown
            }

        /**
         * Convert a Chartboost Mediation banner size to the Chartboost SDK banner size to request.
         * The size is passed through: the Chartboost SDK validates it on load and, for an adaptive
         * request, resolves the loaded size from the ad response. Both SDKs read a height of 0 as
         * no height limit.
         *
         * @return The matching fixed size, an adaptive size for adaptive requests, or STANDARD when no fixed size matches.
         */
        internal fun ChartboostMediationBannerSize.toChartboostBannerSize(): BannerSize =
            if (isAdaptive) {
                BannerSize.adaptive(width, height)
            } else {
                BannerSize.Fixed.entries.firstOrNull { it.width == width && it.height == height } ?: BannerSize.Fixed.STANDARD
            }

        /**
         * Convert the fit type of a loaded Chartboost SDK banner to the Chartboost Mediation banner
         * type. FIXED means a fixed request or a creative shown at its own size, so it is a fixed
         * banner. ASPECT and FILL size the creative to the loaded ad size, so they are adaptive banners.
         *
         * @return [BannerTypes.BANNER] or [BannerTypes.ADAPTIVE_BANNER].
         */
        internal fun BannerAdView.FitType.toBannerType(): BannerType =
            when (this) {
                BannerAdView.FitType.FIXED -> BannerTypes.BANNER
                BannerAdView.FitType.ASPECT, BannerAdView.FitType.FILL -> BannerTypes.ADAPTIVE_BANNER
            }

        /**
         * The bid response to load, or null for a waterfall load. Chartboost Mediation reports a
         * waterfall load as an empty ad markup, which the Chartboost SDK would reject as invalid.
         */
        private val PartnerAdLoadRequest.bidResponse: String?
            get() = adm?.takeIf { it.isNotEmpty() }

        /**
         * Key for parsing the Chartboost SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "app_id"

        /**
         * Key under which the bid token is reported to Chartboost Mediation.
         */
        private const val BUYER_UID_KEY = "buyeruid"
    }

    /**
     * The Chartboost adapter configuration.
     */
    override var configuration: PartnerAdapterConfiguration = ChartboostAdapterConfiguration

    /**
     * Starts the Chartboost SDK. Tests replace it so they do not start the real SDK.
     */
    internal var startSdk: (Context, String, StartCallback) -> Unit = Chartboost::startWithAppId

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
    ): Result<Map<String, Any>> =
        withContext(IO) {
            PartnerLogController.log(SETUP_STARTED)

            val appId = extractPartnerConfig(APPLICATION_ID_KEY, partnerConfiguration.credentials)

            suspendCancellableCoroutine { continuation ->
                fun resumeOnce(result: Result<Map<String, Any>>) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                if (appId.isNullOrEmpty()) {
                    PartnerLogController.log(SETUP_FAILED, "Missing app ID.")
                    resumeOnce(
                        Result.failure(ChartboostMediationAdException(ChartboostMediationError.InitializationError.InvalidCredentials)),
                    )
                    return@suspendCancellableCoroutine
                }

                // The Chartboost SDK keeps the underage flag in memory only and reads it from start onwards.
                partnerConfiguration.isUserUnderage?.let { setIsUserUnderage(context, it) }

                startSdk(context.applicationContext, appId) { error ->
                    error?.let {
                        PartnerLogController.log(SETUP_FAILED, "${it.exception ?: it.code}")
                        resumeOnce(Result.failure(ChartboostMediationAdException(it.code.toInitializationError())))
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

        Chartboost.setUserUnderage(isUserUnderage)
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

        val bidTokenRequest =
            when (request.format) {
                PartnerAdFormats.BANNER -> {
                    val bannerSize =
                        request.bannerSize ?: run {
                            PartnerLogController.log(BIDDER_INFO_FETCH_FAILED, "Missing banner size.")
                            return Result.failure(ChartboostMediationAdException(ChartboostMediationError.PrebidError.InvalidArgument))
                        }
                    BannerAdBidTokenRequest(bannerSize.toChartboostBannerSize(), request.keywords.ifEmpty { null }, setMediation())
                }

                PartnerAdFormats.INTERSTITIAL -> {
                    InterstitialAdBidTokenRequest(request.keywords.ifEmpty { null }, setMediation())
                }

                PartnerAdFormats.REWARDED -> {
                    RewardedAdBidTokenRequest(request.keywords.ifEmpty { null }, setMediation())
                }

                else -> {
                    PartnerLogController.log(BIDDER_INFO_FETCH_FAILED, "Unsupported ad format: ${request.format}")
                    return Result.failure(ChartboostMediationAdException(ChartboostMediationError.PrebidError.UnsupportedAdFormat))
                }
            }
        val token = Chartboost.fetchBidToken(bidTokenRequest)

        // No token is not a failure to Chartboost Mediation, the partner just sits out the auction.
        return if (token.isNullOrEmpty()) {
            PartnerLogController.log(BIDDER_INFO_FETCH_SUCCEEDED, "No bid token available.")
            Result.success(emptyMap())
        } else {
            PartnerLogController.log(BIDDER_INFO_FETCH_SUCCEEDED)
            Result.success(mapOf(BUYER_UID_KEY to token))
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
            PartnerAdFormats.BANNER -> {
                loadBannerAd(context, request, partnerAdListener)
            }

            PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED -> {
                loadFullscreenAd(request, partnerAdListener)
            }

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
                // A banner renders itself once Chartboost Mediation attaches its view, so there is nothing to show here.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }

            PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED -> {
                showFullscreenAd(partnerAd)
            }

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

        return when (val ad = partnerAd.ad) {
            is BannerAdView -> {
                ad.destroy()
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }

            is FullscreenAd -> {
                ad.destroy()
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }

            null -> {
                PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.InvalidateError.AdNotFound))
            }

            else -> {
                PartnerLogController.log(INVALIDATE_FAILED, "Ad is not a Chartboost ad.")
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.InvalidateError.WrongResourceType))
            }
        }
    }

    /**
     * Forward the GDPR consent to Chartboost through the default SharedPreferences key the
     * Chartboost SDK reads. The IAB TCF, GPP and US Privacy strings need no forwarding, the
     * Chartboost SDK reads them from the same file exactly as the consent management platform writes them.
     */
    override fun setConsents(
        context: Context,
        consents: Map<ConsentKey, ConsentValue>,
        modifiedKeys: Set<ConsentKey>,
    ) {
        // Opening the file can wait on its first load, so only touch it when a forwarded consent changed.
        @Suppress("DEPRECATION")
        val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context.applicationContext) }

        if (configuration.partnerId in modifiedKeys || ConsentKeys.GDPR_CONSENT_GIVEN in modifiedKeys) {
            val consent =
                consents[configuration.partnerId]?.takeIf { it.isNotBlank() }
                    ?: consents[ConsentKeys.GDPR_CONSENT_GIVEN]?.takeIf { it.isNotBlank() }
            PartnerLogController.log(
                when (consent) {
                    ConsentValues.GRANTED -> GDPR_CONSENT_GRANTED
                    ConsentValues.DENIED -> GDPR_CONSENT_DENIED
                    ConsentValues.DOES_NOT_APPLY -> GDPR_NOT_APPLICABLE
                    else -> GDPR_CONSENT_UNKNOWN
                },
            )
            preferences.writeConsent(ChartboostSharedPreferencesKeys.GDPR_CONSENT, consent)
        }
    }

    /**
     * Store a granted or denied consent as the boolean the Chartboost SDK reads, and clear the key
     * for any other value so the SDK treats the consent as unknown.
     *
     * @param key The Chartboost SDK SharedPreferences key to write.
     * @param consent The Chartboost Core consent value, or null when the consent is not reported.
     */
    private fun SharedPreferences.writeConsent(
        key: String,
        consent: ConsentValue?,
    ) {
        val editor = edit()
        when (consent) {
            ConsentValues.GRANTED -> editor.putBoolean(key, true)
            ConsentValues.DENIED -> editor.putBoolean(key, false)
            else -> editor.remove(key)
        }
        editor.apply()
    }

    /**
     * Attempt to load a Chartboost banner ad. The returned view renders itself once Chartboost
     * Mediation attaches it to a window, so the adapter never shows it.
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
        val bannerSize =
            request.bannerSize ?: run {
                PartnerLogController.log(LOAD_FAILED, "Missing banner size.")
                return Result.failure(ChartboostMediationAdException(ChartboostMediationError.LoadError.InvalidBannerSize))
            }

        val result =
            BannerAdView.load(
                context,
                BannerAdLoadRequest(
                    adUnit = request.partnerPlacement,
                    size = bannerSize.toChartboostBannerSize(),
                    adm = request.bidResponse,
                    mediation = setMediation(),
                    keywords = request.keywords.ifEmpty { null },
                ),
            )
        val bannerAdView =
            result.ad ?: run {
                PartnerLogController.log(LOAD_FAILED, "loadId=${result.loadId}, ${result.error}")
                return Result.failure(
                    ChartboostMediationAdException(result.error?.toLoadError() ?: ChartboostMediationError.LoadError.Unknown),
                )
            }

        val partnerAd =
            PartnerAd(
                ad = bannerAdView,
                details = emptyMap(),
                request = request,
                // The Chartboost SDK resolves the loaded size from the requested size and the ad response.
                partnerBannerSize = PartnerBannerSize(bannerAdView.adSize, bannerAdView.fitType.toBannerType()),
            )
        bannerAdView.callback = BannerAdViewCallbackBridge(partnerAd, partnerAdListener)
        // No show call: the view renders on its own once Chartboost Mediation attaches it, visible, to a window.

        PartnerLogController.log(
            LOAD_SUCCEEDED,
            "loadId=${bannerAdView.loadId}, price=${bannerAdView.price}, currency=${bannerAdView.currency}",
        )
        return Result.success(partnerAd)
    }

    /**
     * Attempt to load a Chartboost interstitial or rewarded ad.
     *
     * @param request An [PartnerAdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadFullscreenAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        val adm = request.bidResponse
        val keywords = request.keywords.ifEmpty { null }
        val result =
            if (request.format == PartnerAdFormats.REWARDED) {
                FullscreenAd.load(RewardedAdLoadRequest(request.partnerPlacement, adm, setMediation(), keywords))
            } else {
                FullscreenAd.load(InterstitialAdLoadRequest(request.partnerPlacement, adm, setMediation(), keywords))
            }
        val fullscreenAd =
            result.ad ?: run {
                PartnerLogController.log(LOAD_FAILED, "loadId=${result.loadId}, ${result.error}")
                return Result.failure(
                    ChartboostMediationAdException(result.error?.toLoadError() ?: ChartboostMediationError.LoadError.Unknown),
                )
            }

        val partnerAd =
            PartnerAd(
                ad = fullscreenAd,
                details = emptyMap(),
                request = request,
            )
        fullscreenAd.callback = FullscreenAdCallbackBridge(partnerAd, partnerAdListener)

        PartnerLogController.log(
            LOAD_SUCCEEDED,
            "loadId=${fullscreenAd.loadId}, price=${fullscreenAd.price}, currency=${fullscreenAd.currency}",
        )
        return Result.success(partnerAd)
    }

    /**
     * Attempt to show a Chartboost interstitial or rewarded ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Chartboost ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showFullscreenAd(partnerAd: PartnerAd): Result<PartnerAd> {
        val fullscreenAd =
            when (val ad = partnerAd.ad) {
                is FullscreenAd -> {
                    ad
                }

                null -> {
                    PartnerLogController.log(SHOW_FAILED, "Ad is null.")
                    return Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.AdNotFound))
                }

                else -> {
                    PartnerLogController.log(SHOW_FAILED, "Ad is not a Chartboost fullscreen ad.")
                    return Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.WrongResourceType))
                }
            }

        val showError = fullscreenAd.show().error
        return if (showError == null) {
            PartnerLogController.log(SHOW_SUCCEEDED)
            Result.success(partnerAd)
        } else {
            PartnerLogController.log(SHOW_FAILED, "Location: ${fullscreenAd.request.adUnit}. Error: $showError")
            Result.failure(ChartboostMediationAdException(showError.toShowError()))
        }
    }

    /**
     * As Chartboost needs to pass a [Mediation] object to some of the methods.
     * Let's have a method to avoid repetition.
     */
    private fun setMediation(): Mediation = Mediation("Chartboost", ChartboostMediationSdk.getVersion(), configuration.adapterVersion)

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

        return Json
            .decodeFromJsonElement<String>(element)
            .trim()
            .takeIf { it.isNotEmpty() }
            .also { if (it == null) PartnerLogController.log(SETUP_FAILED, "No value found for $key.") }
    }
}

/**
 * Forwards Chartboost fullscreen ad events to Chartboost Mediation. Chartboost Mediation drops a
 * fullscreen ad once it closes or expires without invalidating it, so the ad is destroyed here.
 *
 * @param partnerAd The [PartnerAd] the events belong to.
 * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
 */
private class FullscreenAdCallbackBridge(
    private val partnerAd: PartnerAd,
    private val listener: PartnerAdListener,
) : FullscreenAdCallback {
    override fun onAdClicked(ad: FullscreenAd) {
        PartnerLogController.log(DID_CLICK)
        listener.onPartnerAdClicked(partnerAd)
    }

    override fun onAdClosed(
        ad: FullscreenAd,
        error: ChartboostError?,
    ) {
        PartnerLogController.log(DID_DISMISS, error?.toString() ?: "")
        listener.onPartnerAdDismissed(partnerAd, error?.let { ChartboostMediationAdException(it.toShowError()) })
        ad.destroy()
    }

    override fun onAdExpired(ad: FullscreenAd) {
        PartnerLogController.log(DID_EXPIRE)
        listener.onPartnerAdExpired(partnerAd)
        ad.destroy()
    }

    override fun onImpressionRecorded(ad: FullscreenAd) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)
        listener.onPartnerAdImpression(partnerAd)
    }

    override fun onRewardEarned(ad: FullscreenAd) {
        PartnerLogController.log(DID_REWARD)
        listener.onPartnerAdRewarded(partnerAd)
    }
}

/**
 * Forwards Chartboost banner ad events to Chartboost Mediation. The will appear, modal and app
 * left events are only logged, since [PartnerAdListener] has no matching events.
 *
 * @param partnerAd The [PartnerAd] the events belong to.
 * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
 */
private class BannerAdViewCallbackBridge(
    private val partnerAd: PartnerAd,
    private val listener: PartnerAdListener,
) : BannerAdViewCallback {
    override fun onAdWillAppear(ad: BannerAdView) {
        PartnerLogController.log(CUSTOM, "onAdWillAppear ignored, Chartboost Mediation has no matching event")
    }

    override fun onAdClicked(ad: BannerAdView) {
        PartnerLogController.log(DID_CLICK)
        listener.onPartnerAdClicked(partnerAd)
    }

    override fun onAdExpired(ad: BannerAdView) {
        PartnerLogController.log(DID_EXPIRE)
        listener.onPartnerAdExpired(partnerAd)
    }

    override fun onImpressionRecorded(ad: BannerAdView) {
        PartnerLogController.log(DID_TRACK_IMPRESSION)
        listener.onPartnerAdImpression(partnerAd)
    }

    override fun onModalPresented(ad: BannerAdView) {
        PartnerLogController.log(CUSTOM, "onModalPresented ignored, Chartboost Mediation has no matching event")
    }

    override fun onModalDismissed(ad: BannerAdView) {
        PartnerLogController.log(CUSTOM, "onModalDismissed ignored, Chartboost Mediation has no matching event")
    }

    override fun onAppLeft(ad: BannerAdView) {
        PartnerLogController.log(CUSTOM, "onAppLeft ignored, Chartboost Mediation has no matching event")
    }
}
