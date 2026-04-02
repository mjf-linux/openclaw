package ai.openclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Required by Health Connect to show a rationale for why the app needs health permissions.
 * This activity is launched when the user taps "Learn more" in Health Connect settings.
 */
class HealthConnectPermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "OpenClaw Health Permissions",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "OpenClaw reads your health data (weight, body composition, nutrition, and steps) " +
                "so your AI agent can help you track fitness goals, provide meal suggestions, " +
                "and monitor your progress over time. Your data stays on your device and is only " +
                "sent to your personal OpenClaw gateway.",
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(top = 16.dp),
            )
          }
        }
      }
    }
  }
}
