/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import com.chartboost.chartboostmediationsdk.ad.ChartboostMediationBannerAdView.ChartboostMediationBannerSize
import com.chartboost.mediation.chartboostadapter.ChartboostAdapter.Companion.toChartboostBannerSize
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
    fun `an adaptive request without a height uses the anchored banner height for its width`() {
        assertEquals(BannerSize.adaptive(360, 50), ChartboostMediationBannerSize.bannerSize(360).toChartboostBannerSize())
        assertEquals(BannerSize.adaptive(727, 50), ChartboostMediationBannerSize.bannerSize(727).toChartboostBannerSize())
        assertEquals(BannerSize.adaptive(728, 90), ChartboostMediationBannerSize.bannerSize(728).toChartboostBannerSize())
        assertEquals(BannerSize.adaptive(1024, 90), ChartboostMediationBannerSize.bannerSize(1024).toChartboostBannerSize())
    }
}
