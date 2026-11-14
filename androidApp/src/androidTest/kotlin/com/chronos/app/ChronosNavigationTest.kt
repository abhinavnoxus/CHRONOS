package com.chronos.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an emulator/device; never substitutes for physical alarm audio/reboot verification. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChronosNavigationTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()
    @Before fun setup() { hiltRule.inject() }

    @Test fun primaryDestinationsAndPlanTabsNavigate() {
        composeRule.onNodeWithText("Plan").performClick()
        composeRule.onNodeWithText("Reminders").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Routines").performClick()
        composeRule.onNodeWithText("Alarms").performClick()
        composeRule.onNodeWithContentDescription("Create alarm").assertIsDisplayed()
        composeRule.onNodeWithText("Focus").performClick()
        composeRule.onNodeWithText("Focus studio").assertIsDisplayed()
        composeRule.onNodeWithText("Insights").performClick()
        composeRule.onNodeWithTag("insights-list").performScrollToNode(hasText("Your time pattern"))
        composeRule.onNodeWithText("Your time pattern").assertIsDisplayed()
    }

    @Test fun settingsBackRestoresHome() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Personalize").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("CHRONOS").assertIsDisplayed()
    }
}
