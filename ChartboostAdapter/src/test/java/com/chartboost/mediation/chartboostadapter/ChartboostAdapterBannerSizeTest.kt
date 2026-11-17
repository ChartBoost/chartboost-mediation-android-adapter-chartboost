/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import com.chartboost.chartboostmediationsdk.ad.ChartboostMediationBannerAdView.ChartboostMediationBannerSize
import com.chartboost.chartboostmediationsdk.domain.BannerTypes
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toBannerType
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toChartboostBannerSize
import com.chartboost.sdk.ads.BannerAdView
import com.chartboost.sdk.ads.BannerSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartboostAdapterBannerSizeTest {
    @Test
    fun `fixed mediation sizes map to the fixed Chartboost sizes`() {
        assertEquals(BannerSize.Fixed.STANDARD, ChartboostMediationBannerSize.STANDARD.toChartboostBannerSize())
        assertEquals(BannerSize.Fixed.MEDIUM, ChartboostMediationBannerSize.MEDIUM.toChartboostBannerSize())
        assertEquals(BannerSize.Fixed.LEADERBOARD, ChartboostMediationBannerSize.LEADERBOARD.toChartboostBannerSize())
    }

    @Test
    fun `adaptive mediation sizes keep their requested dimensions`() {
        val size = ChartboostMediationBannerSize.bannerSize(360, 100).toChartboostBannerSize()

        assertEquals(BannerSize.adaptive(360, 100), size)
        assertEquals(360, size.width)
        assertEquals(100, size.height)
    }

    @Test
    fun `an adaptive request matching a fixed size stays adaptive`() {
        assertEquals(BannerSize.adaptive(320, 50), ChartboostMediationBannerSize.bannerSize(320, 50).toChartboostBannerSize())
    }

    @Test
    fun `an adaptive request without a height limit keeps the flexible height`() {
        val size = ChartboostMediationBannerSize.bannerSize(360).toChartboostBannerSize()

        assertEquals(BannerSize.adaptive(360), size)
        assertEquals(0, size.height)
        assertEquals(BannerSize.adaptive(728), ChartboostMediationBannerSize.bannerSize(728).toChartboostBannerSize())
    }

    @Test
    fun `an out of range adaptive request is passed through for the Chartboost SDK to reject`() {
        assertEquals(BannerSize.adaptive(49, 1801), ChartboostMediationBannerSize.bannerSize(49, 1801).toChartboostBannerSize())
    }

    @Test
    fun `the fit type of the loaded ad sets the partner banner type`() {
        assertEquals(BannerTypes.BANNER, BannerAdView.FitType.FIXED.toBannerType())
        assertEquals(BannerTypes.ADAPTIVE_BANNER, BannerAdView.FitType.ASPECT.toBannerType())
        assertEquals(BannerTypes.ADAPTIVE_BANNER, BannerAdView.FitType.FILL.toBannerType())
    }
}
