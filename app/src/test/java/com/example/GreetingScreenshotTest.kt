package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val mockCandidates = listOf(
      com.example.network.GeminiClient.CandidateWithScore(
        candidate = com.example.network.OmdbSearchResult(
          Title = "Breaking Bad",
          Year = "2008–2013",
          imdbID = "tt0903747",
          Type = "series",
          Poster = "N/A"
        ),
        score = 100
      ),
      com.example.network.GeminiClient.CandidateWithScore(
        candidate = com.example.network.OmdbSearchResult(
          Title = "No Half Measures: Creating the Final Season of Breaking Bad",
          Year = "2013",
          imdbID = "tt3152504",
          Type = "movie",
          Poster = "N/A"
        ),
        score = 45
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MovieSelectionCard(
          query = "Breaking Bad",
          candidates = mockCandidates,
          onSelect = { _, _, _ -> },
          onCancel = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
