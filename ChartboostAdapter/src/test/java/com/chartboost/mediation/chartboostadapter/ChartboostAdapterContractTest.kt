/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Size
import com.chartboost.chartboostmediationsdk.ChartboostMediationSdk
import com.chartboost.chartboostmediationsdk.ad.ChartboostMediationBannerAdView.ChartboostMediationBannerSize
import com.chartboost.chartboostmediationsdk.domain.AdInteractionListener
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationAdException
import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationError
import com.chartboost.chartboostmediationsdk.domain.PartnerAd
import com.chartboost.chartboostmediationsdk.domain.PartnerAdFormat
import com.chartboost.chartboostmediationsdk.domain.PartnerAdFormats
import com.chartboost.chartboostmediationsdk.domain.PartnerAdListener
import com.chartboost.chartboostmediationsdk.domain.PartnerAdLoadRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerAdPreBidRequest
import com.chartboost.chartboostmediationsdk.domain.PartnerConfiguration
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.oversizeError
import com.chartboost.sdk.ads.BannerAdLoadResult
import com.chartboost.sdk.ads.BannerAdView
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The adapter paths that answer before the Chartboost SDK is started, including the oversized ad
 * decision and the banner load path that acts on it. The SDK is never started here, so its load
 * entry points answer with NotInitialized once they accept the request, except where a test fakes
 * the banner load to hand back a loaded view.
 */
@RunWith(RobolectricTestRunner::class)
class ChartboostAdapterContractTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val adapter = ChartboostAdapter()

    // Never touched by the paths under test, the fullscreen show needs a real ad first.
    private val activity by lazy { Activity() }

    private val listener =
        object : PartnerAdListener {
            override fun onPartnerAdImpression(partnerAd: PartnerAd) = Unit

            override fun onPartnerAdClicked(partnerAd: PartnerAd) = Unit

            override fun onPartnerAdRewarded(partnerAd: PartnerAd) = Unit

            override fun onPartnerAdDismissed(
                partnerAd: PartnerAd,
                error: ChartboostMediationAdException?,
            ) = Unit

            override fun onPartnerAdExpired(partnerAd: PartnerAd) = Unit
        }

    @After
    fun tearDown() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(false)
        unmockkAll()
    }

    @Test
    fun `setting up without an app id fails with invalid credentials`() =
        runBlocking {
            for (credentials in listOf(
                emptyMap(),
                mapOf("app_signature" to "sig"),
                mapOf("app_id" to " "),
            )) {
                val configuration =
                    PartnerConfiguration(
                        credentials = buildJsonObject { credentials.forEach { (key, value) -> put(key, value) } },
                        consents = emptyMap(),
                        isUserUnderage = null,
                    )

                val result = adapter.setUp(context, configuration)

                assertEquals("$credentials", ChartboostMediationError.InitializationError.InvalidCredentials, result.mediationError())
            }
        }

    @Test
    fun `setting up with an app id starts the SDK with that app id alone`() =
        runBlocking {
            for (credentials in listOf(
                mapOf("app_id" to "an-app-id"),
                mapOf("app_id" to "an-app-id", "app_signature" to "sig"),
            )) {
                var startedWith: String? = null
                adapter.startSdk = { _, appId, callback ->
                    startedWith = appId
                    callback.onStartCompleted(null)
                }
                val configuration =
                    PartnerConfiguration(
                        credentials = buildJsonObject { credentials.forEach { (key, value) -> put(key, value) } },
                        consents = emptyMap(),
                        isUserUnderage = null,
                    )

                val result = adapter.setUp(context, configuration)

                assertTrue("$credentials", result.isSuccess)
                assertEquals("$credentials", "an-app-id", startedWith)
            }
        }

    @Test
    fun `a bid token request before the SDK starts reports no token`() =
        runBlocking {
            // The SDK answers on the calling thread, so no main looper drain is needed.
            for (format in listOf(PartnerAdFormats.BANNER, PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED)) {
                val result = adapter.fetchBidderInformation(context, preBidRequest(format))

                assertEquals("format $format", emptyMap<String, String>(), result.getOrThrow())
            }
        }

    @Test
    fun `a banner bid token request without a size fails with an invalid argument`() =
        runBlocking {
            val result = adapter.fetchBidderInformation(context, preBidRequest(PartnerAdFormats.BANNER, bannerSize = null))

            assertEquals(ChartboostMediationError.PrebidError.InvalidArgument, result.mediationError())
        }

    @Test
    fun `a bid token request for an unsupported format fails`() =
        runBlocking {
            val result = adapter.fetchBidderInformation(context, preBidRequest(PartnerAdFormats.REWARDED_INTERSTITIAL))

            assertEquals(ChartboostMediationError.PrebidError.UnsupportedAdFormat, result.mediationError())
        }

    @Test
    fun `loading an unsupported format fails`() =
        runBlocking {
            val result = adapter.load(context, loadRequest(PartnerAdFormats.REWARDED_INTERSTITIAL), listener)

            assertEquals(ChartboostMediationError.LoadError.UnsupportedAdFormat, result.mediationError())
        }

    @Test
    fun `loading a banner without a size fails with an invalid banner size`() =
        runBlocking {
            val result = adapter.load(context, loadRequest(PartnerAdFormats.BANNER, bannerSize = null), listener)

            assertEquals(ChartboostMediationError.LoadError.InvalidBannerSize, result.mediationError())
        }

    @Test
    fun `loading a banner with an out of range size fails with an invalid banner size`() {
        // The SDK checks the size before anything else, so the failure is reachable before it starts.
        val request = loadRequest(PartnerAdFormats.BANNER, bannerSize = ChartboostMediationBannerSize.bannerSize(49))

        val result = awaitWithMainLooper { adapter.load(context, request, listener) }

        assertEquals(ChartboostMediationError.LoadError.InvalidBannerSize, result.mediationError())
    }

    @Test
    fun `a waterfall load reaches the SDK without an ad markup`() {
        // Chartboost Mediation reports waterfall loads as an empty markup. Passed on as is, the SDK would reject it as InvalidAdm.
        for (format in listOf(PartnerAdFormats.BANNER, PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED)) {
            val result = awaitWithMainLooper { adapter.load(context, loadRequest(format, adm = ""), listener) }

            assertEquals("format $format", ChartboostMediationError.LoadError.PartnerNotInitialized, result.mediationError())
        }
    }

    @Test
    fun `a blank bid response is rejected as an invalid ad markup`() {
        val result = awaitWithMainLooper { adapter.load(context, loadRequest(PartnerAdFormats.INTERSTITIAL, adm = " "), listener) }

        assertEquals(ChartboostMediationError.LoadError.InvalidAdMarkup, result.mediationError())
    }

    @Test
    fun `showing a partner ad without an ad fails as not found`() =
        runBlocking {
            val partnerAd = PartnerAd(ad = null, details = emptyMap(), request = loadRequest(PartnerAdFormats.INTERSTITIAL))

            val result = adapter.show(activity, partnerAd)

            assertEquals(ChartboostMediationError.ShowError.AdNotFound, result.mediationError())
        }

    @Test
    fun `showing a foreign ad object fails as the wrong resource type`() =
        runBlocking {
            val partnerAd = PartnerAd(ad = Any(), details = emptyMap(), request = loadRequest(PartnerAdFormats.REWARDED))

            val result = adapter.show(activity, partnerAd)

            assertEquals(ChartboostMediationError.ShowError.WrongResourceType, result.mediationError())
        }

    @Test
    fun `showing a banner succeeds without touching the ad`() =
        runBlocking {
            val partnerAd = PartnerAd(ad = Any(), details = emptyMap(), request = loadRequest(PartnerAdFormats.BANNER))

            val result = adapter.show(activity, partnerAd)

            assertEquals(partnerAd, result.getOrNull())
        }

    @Test
    fun `invalidating a partner ad without an ad fails as not found`() =
        runBlocking {
            val partnerAd = PartnerAd(ad = null, details = emptyMap(), request = loadRequest(PartnerAdFormats.BANNER))

            val result = adapter.invalidate(partnerAd)

            assertEquals(ChartboostMediationError.InvalidateError.AdNotFound, result.mediationError())
        }

    @Test
    fun `invalidating a foreign ad object fails as the wrong resource type`() =
        runBlocking {
            val partnerAd = PartnerAd(ad = Any(), details = emptyMap(), request = loadRequest(PartnerAdFormats.INTERSTITIAL))

            val result = adapter.invalidate(partnerAd)

            assertEquals(ChartboostMediationError.InvalidateError.WrongResourceType, result.mediationError())
        }

    @Test
    fun `an ad larger than the requested adaptive size is too large`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)
        val request = loadRequest(PartnerAdFormats.BANNER, requestedBannerSize = ChartboostMediationBannerSize.bannerSize(320, 50))

        for (adSize in listOf(Size(321, 50), Size(320, 51), Size(728, 90))) {
            assertEquals("$adSize", ChartboostMediationError.LoadError.AdTooLarge, request.oversizeError(adSize))
        }
    }

    @Test
    fun `an ad within the requested adaptive size is not too large`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)
        val request = loadRequest(PartnerAdFormats.BANNER, requestedBannerSize = ChartboostMediationBannerSize.bannerSize(320, 50))

        for (adSize in listOf(Size(320, 50), Size(300, 50), Size(320, 25))) {
            assertNull("$adSize", request.oversizeError(adSize))
        }
    }

    @Test
    fun `a requested height of 0 accepts any ad height`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)
        val request = loadRequest(PartnerAdFormats.BANNER, requestedBannerSize = ChartboostMediationBannerSize.bannerSize(320))

        assertNull(request.oversizeError(Size(320, 600)))
        assertEquals(ChartboostMediationError.LoadError.AdTooLarge, request.oversizeError(Size(321, 600)))
    }

    @Test
    fun `an ad is never too large while discarding is off`() {
        val request = loadRequest(PartnerAdFormats.BANNER, requestedBannerSize = ChartboostMediationBannerSize.bannerSize(320, 50))

        assertNull(request.oversizeError(Size(728, 90)))
    }

    @Test
    fun `an ad is never too large for a fixed or missing request`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)

        assertNull(loadRequest(PartnerAdFormats.BANNER, requestedBannerSize = null).oversizeError(Size(728, 90)))
        assertNull(
            loadRequest(
                PartnerAdFormats.BANNER,
                requestedBannerSize = ChartboostMediationBannerSize.STANDARD,
            ).oversizeError(Size(728, 90)),
        )
    }

    @Test
    fun `an oversized loaded banner is destroyed and fails the load before the callback bridge`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)
        val bannerAdView = fakeBannerLoad(Size(728, 90))

        val result = runBlocking { adapter.load(context, adaptiveBannerRequest(), listener) }

        assertEquals(ChartboostMediationError.LoadError.AdTooLarge, result.mediationError())
        verify(exactly = 1) { bannerAdView.destroy() }
        verify(exactly = 0) { bannerAdView.callback = any() }
    }

    @Test
    fun `a loaded banner within the requested size is kept and takes the callback bridge`() {
        ChartboostMediationSdk.setShouldDiscardOversizedAds(true)
        // Smaller than the requested 320x50, so the reported size cannot be the requested one.
        val bannerAdView = fakeBannerLoad(Size(300, 40))

        val result = runBlocking { adapter.load(context, adaptiveBannerRequest(), listener) }

        val partnerAd = result.getOrThrow()
        assertSame(bannerAdView, partnerAd.ad)
        // Chartboost Mediation reads the loaded size from here to discard an oversized ad itself.
        assertEquals(mapOf("banner_width_dips" to "300", "banner_height_dips" to "40"), partnerAd.details)
        verify(exactly = 0) { bannerAdView.destroy() }
        verify(exactly = 1) { bannerAdView.callback = any() }
    }

    /**
     * Fakes the Chartboost SDK banner load so it answers with a view of [loadedSize]. Both the view
     * and the load result have internal constructors, so neither can be built here.
     *
     * @return The view the faked load hands back.
     */
    private fun fakeBannerLoad(loadedSize: Size): BannerAdView {
        val bannerAdView =
            mockk<BannerAdView>(relaxed = true) {
                every { adSize } returns loadedSize
                every { fitType } returns BannerAdView.FitType.FILL
            }
        mockkObject(BannerAdView.Companion)
        coEvery { BannerAdView.load(any(), any()) } returns
            mockk<BannerAdLoadResult>(relaxed = true) { every { ad } returns bannerAdView }
        return bannerAdView
    }

    private fun adaptiveBannerRequest(): PartnerAdLoadRequest {
        val requested = ChartboostMediationBannerSize.bannerSize(320, 50)
        return loadRequest(PartnerAdFormats.BANNER, bannerSize = requested, requestedBannerSize = requested)
    }

    /**
     * Runs [block] off the test thread while this thread drains the main looper, since the SDK
     * posts its results to the main thread and Robolectric only runs those when idled.
     */
    private fun <T> awaitWithMainLooper(block: suspend () -> T): T {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val done = CountDownLatch(1)
            val deferred = scope.async { block() }.also { it.invokeOnCompletion { done.countDown() } }
            val deadline = System.currentTimeMillis() + 10_000
            while (!done.await(10, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() > deadline) fail("The SDK never answered.")
                shadowOf(Looper.getMainLooper()).idle()
            }
            return runBlocking { deferred.await() }
        } finally {
            scope.cancel()
        }
    }

    private fun <T> Result<T>.mediationError(): ChartboostMediationError {
        assertTrue(isFailure)
        return (exceptionOrNull() as ChartboostMediationAdException).chartboostMediationError
    }

    private fun preBidRequest(
        format: PartnerAdFormat,
        bannerSize: ChartboostMediationBannerSize? = ChartboostMediationBannerSize.STANDARD,
    ) = PartnerAdPreBidRequest(
        mediationPlacement = "placement",
        format = format,
        loadId = "load",
        bannerSize = bannerSize,
        keywords = emptyMap(),
        partnerSettings = emptyMap(),
    )

    private fun loadRequest(
        format: PartnerAdFormat,
        bannerSize: ChartboostMediationBannerSize? = ChartboostMediationBannerSize.STANDARD,
        adm: String? = null,
        requestedBannerSize: ChartboostMediationBannerSize? = null,
    ) = PartnerAdLoadRequest(
        partnerId = ChartboostAdapterConfiguration.partnerId,
        mediationPlacement = "placement",
        partnerPlacement = "partner_placement",
        bannerSize = bannerSize,
        format = format,
        adm = adm,
        identifier = "identifier",
        partnerSettings = emptyMap(),
        adInteractionListener =
            object : AdInteractionListener {
                override fun onImpressionTracked(partnerAd: PartnerAd) = Unit

                override fun onClicked(partnerAd: PartnerAd) = Unit

                override fun onRewarded(partnerAd: PartnerAd) = Unit

                override fun onDismissed(
                    partnerAd: PartnerAd,
                    error: ChartboostMediationAdException?,
                ) = Unit

                override fun onExpired(partnerAd: PartnerAd) = Unit
            },
        keywords = emptyMap(),
        requestedBannerSize = requestedBannerSize,
    )
}
