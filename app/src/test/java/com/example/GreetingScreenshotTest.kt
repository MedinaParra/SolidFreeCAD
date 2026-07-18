package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WelcomeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun welcomeScreenShowsApplicationName() {
    composeTestRule.setContent {
      MyApplicationTheme {
        WelcomeScreen(onNewClick = {})
      }
    }

    composeTestRule.onNodeWithText("SolidFreeCAD").assertExists()
  }
}
