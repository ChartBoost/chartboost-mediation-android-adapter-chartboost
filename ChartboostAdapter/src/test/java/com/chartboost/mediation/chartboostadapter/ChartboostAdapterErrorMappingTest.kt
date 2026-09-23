/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import com.chartboost.chartboostmediationsdk.domain.ChartboostMediationError
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toInitializationError
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toLoadError
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toShowError
import com.chartboost.sdk.events.ChartboostError
import com.chartboost.sdk.events.StartError
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartboostAdapterErrorMappingTest {
    @Test
    fun `every start error code maps to an initialization error`() {
        val expected =
            mapOf(
                StartError.Code.INVALID_CREDENTIALS to ChartboostMediationError.InitializationError.InvalidCredentials,
                StartError.Code.INVALID_CONFIGURATION to ChartboostMediationError.InitializationError.InvalidAppConfig,
                StartError.Code.NETWORK_FAILURE to ChartboostMediationError.InitializationError.NetworkingError,
                StartError.Code.SERVER_ERROR to ChartboostMediationError.InitializationError.ServerError,
                StartError.Code.OS_VERSION_NOT_SUPPORTED to ChartboostMediationError.InitializationError.OSVersionNotSupported,
                StartError.Code.INTERNAL to ChartboostMediationError.InitializationError.InternalError,
                StartError.Code.DISABLED to ChartboostMediationError.InitializationError.InternalError,
                StartError.Code.NO_CONTEXT to ChartboostMediationError.InitializationError.Unknown,
                StartError.Code.PERMISSIONS_NOT_SET to ChartboostMediationError.InitializationError.Unknown,
            )

        assertEquals(StartError.Code.entries.toSet(), expected.keys)
        expected.forEach { (code, error) -> assertEquals(code.name, error, code.toInitializationError()) }
    }

    @Test
    fun `load errors map to the matching load error`() {
        val expected =
            mapOf(
                ChartboostError.Load.NoAd to ChartboostMediationError.LoadError.NoFill,
                ChartboostError.Load.NotInitialized to ChartboostMediationError.LoadError.PartnerNotInitialized,
                ChartboostError.Load.InvalidAdm("blank", null) to ChartboostMediationError.LoadError.InvalidAdMarkup,
                ChartboostError.Load.InvalidRequest("bad", null) to ChartboostMediationError.LoadError.InvalidAdRequest,
                ChartboostError.Load.InvalidResponse("bad", null) to ChartboostMediationError.LoadError.InvalidBidResponse,
                ChartboostError.Load.InvalidPlacement to ChartboostMediationError.LoadError.InvalidPartnerPlacement,
                ChartboostError.Load.NoContext to ChartboostMediationError.LoadError.ActivityNotFound,
                ChartboostError.Load.Disabled to ChartboostMediationError.LoadError.Aborted,
                ChartboostError.Load.LoadInProgress to ChartboostMediationError.LoadError.LoadInProgress,
                ChartboostError.Load.RateLimited to ChartboostMediationError.LoadError.RateLimited,
                ChartboostError.Load.TimedOut("slow", null) to ChartboostMediationError.LoadError.AdRequestTimeout,
                ChartboostError.Load.NoStorage to ChartboostMediationError.LoadError.OutOfStorage,
                ChartboostError.Load.InvalidBannerSize to ChartboostMediationError.LoadError.InvalidBannerSize,
                ChartboostError.Load.Unknown("boom", null) to ChartboostMediationError.LoadError.Unknown,
                ChartboostError.Load.VastError("vast", null) to ChartboostMediationError.LoadError.Unknown,
                ChartboostError.Show.NoAd to ChartboostMediationError.LoadError.NoBannerView,
                ChartboostError.Show.AdExpired to ChartboostMediationError.LoadError.NoFill,
                ChartboostError.Show.NotInitialized to ChartboostMediationError.LoadError.PartnerNotInitialized,
                ChartboostError.Show.Disabled to ChartboostMediationError.LoadError.Aborted,
                ChartboostError.Show.AdInvalidated to ChartboostMediationError.LoadError.Aborted,
                ChartboostError.Show.NoContext to ChartboostMediationError.LoadError.ActivityNotFound,
                ChartboostError.Show.TimedOut("slow", null) to ChartboostMediationError.LoadError.AdRequestTimeout,
                ChartboostError.Show.FullscreenAlreadyShowing to ChartboostMediationError.LoadError.Unknown,
                ChartboostError.Render.Unknown("boom", null) to ChartboostMediationError.LoadError.Unknown,
                ChartboostError.Connectivity.NoInternet to ChartboostMediationError.LoadError.NoConnectivity,
                ChartboostError.Connectivity.ServerError("500", null) to ChartboostMediationError.LoadError.ServerError,
                ChartboostError.Connectivity.NetworkError("dns", null) to ChartboostMediationError.LoadError.NetworkingError,
                ChartboostError.Connectivity.TimedOut to ChartboostMediationError.LoadError.NetworkingError,
            )

        expected.forEach { (error, mediationError) -> assertEquals(error.code, mediationError, error.toLoadError()) }
    }

    @Test
    fun `show and render errors map to the matching show error`() {
        val expected =
            mapOf(
                ChartboostError.Show.NoAd to ChartboostMediationError.ShowError.AdNotReady,
                ChartboostError.Show.AdExpired to ChartboostMediationError.ShowError.AdExpired,
                ChartboostError.Show.AdInvalidated to ChartboostMediationError.ShowError.AdNotFound,
                ChartboostError.Show.Disabled to ChartboostMediationError.ShowError.AdNotFound,
                ChartboostError.Show.NoContext to ChartboostMediationError.ShowError.ActivityNotFound,
                ChartboostError.Show.NotInitialized to ChartboostMediationError.ShowError.NotInitialized,
                ChartboostError.Show.FullscreenAlreadyShowing to ChartboostMediationError.ShowError.ShowInProgress,
                ChartboostError.Show.TimedOut("slow", null) to ChartboostMediationError.ShowError.Timeout,
                ChartboostError.Show.AssetUnavailable("url", "gone", null) to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Show.Unknown("boom", null) to ChartboostMediationError.ShowError.Unknown,
                ChartboostError.Render.VideoPlaybackError("codec", null) to ChartboostMediationError.ShowError.VideoPlayerError,
                ChartboostError.Render.AssetUnavailable("url", "gone", null) to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Render.Internal("boom", null) to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Render.WebViewMraidUnload to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Render.WebViewTerminated to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Render.Unknown("boom", null) to ChartboostMediationError.ShowError.MediaBroken,
                ChartboostError.Render.UnexpectedDismiss to ChartboostMediationError.ShowError.Unknown,
                ChartboostError.Connectivity.NoInternet to ChartboostMediationError.ShowError.NoConnectivity,
                ChartboostError.Load.NoAd to ChartboostMediationError.ShowError.Unknown,
            )

        expected.forEach { (error, mediationError) -> assertEquals(error.code, mediationError, error.toShowError()) }
    }
}
