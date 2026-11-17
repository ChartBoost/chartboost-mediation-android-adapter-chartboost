/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.chartboostadapter

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.chartboost.core.consent.ConsentKeys
import com.chartboost.core.consent.ConsentValues
import com.chartboost.sdk.privacy.ChartboostSharedPreferencesKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChartboostAdapterConsentTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val adapter = ChartboostAdapter()

    @Suppress("DEPRECATION")
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `granted GDPR consent is stored as true`() {
        adapter.setConsents(
            context,
            mapOf(ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.GRANTED),
            setOf(ConsentKeys.GDPR_CONSENT_GIVEN),
        )

        assertTrue(preferences.getBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, false))
    }

    @Test
    fun `denied GDPR consent is stored as false`() {
        adapter.setConsents(
            context,
            mapOf(ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.DENIED),
            setOf(ConsentKeys.GDPR_CONSENT_GIVEN),
        )

        assertTrue(preferences.contains(ChartboostSharedPreferencesKeys.GDPR_CONSENT))
        assertFalse(preferences.getBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, true))
    }

    @Test
    fun `GDPR consent that does not apply clears the stored value`() {
        preferences.edit().putBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, true).commit()

        adapter.setConsents(
            context,
            mapOf(ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.DOES_NOT_APPLY),
            setOf(ConsentKeys.GDPR_CONSENT_GIVEN),
        )

        assertFalse(preferences.contains(ChartboostSharedPreferencesKeys.GDPR_CONSENT))
    }

    @Test
    fun `a removed GDPR consent clears the stored value`() {
        preferences.edit().putBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, false).commit()

        adapter.setConsents(context, emptyMap(), setOf(ConsentKeys.GDPR_CONSENT_GIVEN))

        assertFalse(preferences.contains(ChartboostSharedPreferencesKeys.GDPR_CONSENT))
    }

    @Test
    fun `a partner specific consent that does not apply clears the stored value`() {
        preferences.edit().putBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, true).commit()

        adapter.setConsents(
            context,
            mapOf(
                ChartboostAdapterConfiguration.partnerId to ConsentValues.DOES_NOT_APPLY,
                ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.GRANTED,
            ),
            setOf(ChartboostAdapterConfiguration.partnerId),
        )

        assertFalse(preferences.contains(ChartboostSharedPreferencesKeys.GDPR_CONSENT))
    }

    @Test
    fun `the partner specific consent wins over the general GDPR consent`() {
        adapter.setConsents(
            context,
            mapOf(
                ChartboostAdapterConfiguration.partnerId to ConsentValues.DENIED,
                ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.GRANTED,
            ),
            setOf(ChartboostAdapterConfiguration.partnerId),
        )

        assertFalse(preferences.getBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, true))
    }

    @Test
    fun `a blank partner specific consent falls back to the general GDPR consent`() {
        adapter.setConsents(
            context,
            mapOf(
                ChartboostAdapterConfiguration.partnerId to " ",
                ConsentKeys.GDPR_CONSENT_GIVEN to ConsentValues.GRANTED,
            ),
            setOf(ChartboostAdapterConfiguration.partnerId, ConsentKeys.GDPR_CONSENT_GIVEN),
        )

        assertTrue(preferences.getBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, false))
    }

    @Test
    fun `an unmodified GDPR consent leaves the stored value alone`() {
        preferences.edit().putBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, true).commit()

        adapter.setConsents(
            context,
            mapOf(ConsentKeys.USP to "1YNN"),
            setOf(ConsentKeys.USP),
        )

        assertTrue(preferences.getBoolean(ChartboostSharedPreferencesKeys.GDPR_CONSENT, false))
    }

    @Test
    fun `IAB strings are not copied since the SDK reads them from the CMP`() {
        adapter.setConsents(
            context,
            mapOf(
                ConsentKeys.USP to "1YNN",
                ConsentKeys.TCF to "CP_tcf",
                ConsentKeys.GPP to "DBABMA~gpp",
            ),
            setOf(ConsentKeys.USP, ConsentKeys.TCF, ConsentKeys.GPP),
        )

        assertEquals(emptyMap<String, Any?>(), preferences.all)
    }
}
